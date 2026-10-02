/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.lut.ModalityLutModule;
import org.dcm4che3.img.lut.ModalityPresetProvider;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.LayeredEntries;
import org.weasis.core.api.util.LayeredEntries.Merged;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.ref.AnatomicItem;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.opencv.op.lut.WlPresentation;

/**
 * The window/level presets offered after the presets of the image, merged by id from the bundled
 * document, the site document {@value #SITE_FILE} of the resources package (see {@code
 * SiteDocuments}) and the user document {@value #USER_FILE}, by the rules of {@link
 * LayeredEntries}: a later document overrides an earlier one, unless the site locked the id.
 *
 * <p>Each image gets the presets whose modality and conditions it meets, those preferred for its
 * anatomy (resolved region: code and region groups) first, each group in document order. Percent
 * presets are resolved on a range of modality values: the {@code image} reference uses the range of
 * the image (for an MPR slice, the one of the volume); the {@code series} reference uses Smallest
 * and Largest Pixel Value in Series (0028,0108 and 0028,0109) through the Modality LUT of the
 * image, and the range of the image when the series does not carry them (both attributes are Type
 * 3).
 */
public final class WindowPresetRegistry implements ModalityPresetProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(WindowPresetRegistry.class);

  public static final String BUILTIN_RESOURCE = "/windowPresets.json"; // NON-NLS
  public static final String USER_FILE = "customWindowPresets.json"; // NON-NLS

  /** File name of the site document in the {@code config} folder of the resources package. */
  public static final String SITE_FILE = "windowPresets.json"; // NON-NLS

  /** Where a merged preset comes from; a later layer overrides an earlier one. */
  public enum Origin {
    BUILT_IN,
    SITE,
    USER
  }

  private static final int LAYER_BUILT_IN = Origin.BUILT_IN.ordinal();

  private static volatile WindowPresetRegistry instance; // NOSONAR double-checked locking

  /** What the conditions of a preset look at. */
  /**
   * @param anatomy the notation of the image region ({@code SCHEME:code}, or its Body Part Examined
   *     term when it has no code), null when unknown; regions with one code share their presets
   */
  record ImageKey(String modality, int bitsStored, boolean hasRescale, String anatomy) {}

  /**
   * A preset offered for an image key: the shared instance for an absolute preset, resolved on each
   * image for a percent one.
   */
  private record Entry(
      WindowPreset preset, PresetWindowLevel shared, int keyCode, boolean isDefault) {}

  private final List<WindowPreset> builtIn;
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  private final Map<ImageKey, List<Entry>> byImage = new ConcurrentHashMap<>();
  private final Map<String, WindowPreset> user = new LinkedHashMap<>();
  private List<WindowPreset> site = List.of();
  // Set when the user file exists but cannot be read: it is kept aside before being rewritten
  private boolean userUnreadable;
  private Path siteFile;
  private Path userFile;
  private Consumer<Path> remoteStore;
  private volatile Merged<WindowPreset> merged; // NOSONAR immutable snapshot

  public WindowPresetRegistry(List<WindowPreset> builtIn) {
    this.builtIn = builtIn == null ? List.of() : List.copyOf(builtIn);
    this.merged = merge();
  }

  /** The shared registry, created from the bundled document on first use. */
  public static WindowPresetRegistry getInstance() {
    WindowPresetRegistry result = instance;
    if (result == null) {
      synchronized (WindowPresetRegistry.class) {
        result = instance;
        if (result == null) {
          result = new WindowPresetRegistry(loadBuiltIn());
          instance = result;
        }
      }
    }
    return result;
  }

  /** Installs the shared instance, for hosts that build their own such as tests. */
  public static void useInstance(WindowPresetRegistry registry) {
    synchronized (WindowPresetRegistry.class) {
      instance = registry;
    }
  }

  public static List<WindowPreset> loadBuiltIn() {
    try (InputStream in = WindowPresetRegistry.class.getResourceAsStream(BUILTIN_RESOURCE)) {
      return in == null ? List.of() : WindowPresetJson.read(in);
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read the built-in window presets", e);
      return List.of();
    }
  }

  /**
   * Sets the documents and reloads.
   *
   * @param siteFile the site document, or null
   * @param userFile the user document, or null
   */
  public void configure(Path siteFile, Path userFile) {
    configure(siteFile, userFile, null);
  }

  /**
   * @param remoteStore called with the user file after it is written, to mirror it remotely
   */
  public void configure(Path siteFile, Path userFile, Consumer<Path> remoteStore) {
    synchronized (this) {
      this.siteFile = siteFile;
      this.userFile = userFile;
      this.remoteStore = remoteStore;
      this.site = List.of();
      user.clear();
    }
    reload();
  }

  /**
   * Re-reads every document, merges and notifies the listeners. When the site document cannot be
   * read, the previous site presets are kept.
   */
  public void reload() {
    synchronized (this) {
      if (siteFile != null) {
        loadSite(siteFile).ifPresent(p -> site = p);
      }
      readUser();
    }
    apply(merge());
  }

  public void addListener(Runnable listener) {
    listeners.add(listener);
  }

  public void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  /** Every merged preset, in menu order, hidden ones included. */
  public List<WindowPreset> presets() {
    return merged.entries();
  }

  public Optional<WindowPreset> find(String id) {
    return merged.entries().stream().filter(p -> p.id().equals(id)).findFirst();
  }

  /** Where the merged preset of that id comes from. */
  public Origin origin(String id) {
    return Origin.values()[merged.layerOf(id)];
  }

  /** Whether the site locked that id: the user document may not replace or hide it. */
  public boolean isLocked(String id) {
    return merged.isLocked(id);
  }

  /** The user presets, in document order. */
  public synchronized List<WindowPreset> userPresets() {
    return List.copyOf(user.values());
  }

  /** Adds or replaces the user preset of that id, writes the user file and merges. */
  public void saveUser(WindowPreset preset) throws IOException {
    saveUser(List.of(Objects.requireNonNull(preset)));
  }

  /** Adds or replaces the user presets of those ids, with one write of the user file. */
  public void saveUser(Collection<WindowPreset> presets) throws IOException {
    synchronized (this) {
      presets.forEach(p -> user.put(p.id(), p));
      persistUser();
    }
    apply(merge());
  }

  /**
   * Removes the user preset of that id.
   *
   * @return whether one existed
   */
  public boolean deleteUser(String id) throws IOException {
    synchronized (this) {
      if (user.remove(id) == null) {
        return false;
      }
      persistUser();
    }
    apply(merge());
    return true;
  }

  /** Writes presets to a JSON document, for sharing or backup. */
  public static void export(Path file, Collection<WindowPreset> presets) throws IOException {
    WindowPresetJson.write(file, presets);
  }

  /**
   * Reads presets from a JSON document, with their ids moved to the user prefix so they can be
   * saved as user presets.
   *
   * @throws IOException when the file cannot be read or holds no preset
   */
  public static List<WindowPreset> readImport(Path file) throws IOException {
    List<WindowPreset> presets = WindowPresetJson.read(file);
    if (presets.isEmpty()) {
      throw new IOException("No window preset in " + file);
    }
    return presets.stream().map(WindowPreset::asUserPreset).toList();
  }

  private void persistUser() throws IOException {
    if (userFile == null) {
      return;
    }
    Path parent = userFile.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    if (userUnreadable && Files.isRegularFile(userFile)) {
      Path backup = userFile.resolveSibling(userFile.getFileName() + ".bak"); // NON-NLS
      Files.move(userFile, backup, StandardCopyOption.REPLACE_EXISTING);
      LOGGER.warn("The unreadable user window presets are kept in {}", backup);
    }
    userUnreadable = false;
    WindowPresetJson.write(userFile, user.values());
    if (remoteStore != null) {
      remoteStore.accept(userFile);
    }
  }

  @Override
  public List<PresetWindowLevel> getPresets(DicomImageAdapter adapter, WlPresentation wl) {
    AnatomicRegion anatomy = anatomy(adapter);
    ImageKey key = imageKey(adapter, anatomy);
    if (key == null) {
      return List.of();
    }
    List<Entry> entries = byImage.computeIfAbsent(key, k -> build(k, anatomy));
    if (entries.isEmpty()) {
      return List.of();
    }
    double width = adapter.getFullDynamicWidth(wl);
    double center = adapter.getFullDynamicCenter(wl);
    double[] imageRange = {center - width / 2.0, center + width / 2.0};
    double[] seriesRange = null;
    List<PresetWindowLevel> presets = new ArrayList<>(entries.size());
    for (Entry entry : entries) {
      if (entry.shared() != null) {
        presets.add(entry.shared());
      } else {
        double[] range = imageRange;
        if (WindowPreset.REFERENCE_SERIES.equals(entry.preset().domain().reference())) {
          if (seriesRange == null) {
            seriesRange = seriesRange(adapter, wl).orElse(imageRange);
          }
          range = seriesRange;
        }
        PresetWindowLevel p = entry.preset().resolve(range[0], range[1]);
        if (p != null) {
          p.setKeyCode(entry.keyCode());
          p.setFallbackDefault(entry.isDefault());
          presets.add(p);
        }
      }
    }
    return presets;
  }

  /** The series pixel range in modality values, ordered, when the image carries it. */
  static Optional<double[]> seriesRange(DicomImageAdapter adapter, WlPresentation wl) {
    ImageDescriptor desc = adapter.getImageDescriptor();
    if (desc == null) {
      return Optional.empty();
    }
    return desc.getSeriesPixelRange()
        .map(
            r -> {
              double a = adapter.pixelToRealValue(r.minVal, wl).doubleValue();
              double b = adapter.pixelToRealValue(r.maxVal, wl).doubleValue();
              return new double[] {Math.min(a, b), Math.max(a, b)};
            });
  }

  static AnatomicRegion anatomy(DicomImageAdapter adapter) {
    ImageDescriptor desc = adapter.getImageDescriptor();
    return desc == null ? null : desc.getAnatomicRegion();
  }

  static ImageKey imageKey(DicomImageAdapter adapter, AnatomicRegion anatomy) {
    ImageDescriptor desc = adapter.getImageDescriptor();
    if (desc == null) {
      return null;
    }
    String modality = desc.getModality();
    if (!StringUtil.hasText(modality)) {
      return null;
    }
    ModalityLutModule mLut = desc.getModalityLutForFrame(adapter.getFrameIndex());
    boolean hasRescale =
        mLut != null && (mLut.getRescaleSlope().isPresent() || mLut.getLut().isPresent());
    return new ImageKey(modality, adapter.getBitsStored(), hasRescale, anatomyKey(anatomy));
  }

  private static String anatomyKey(AnatomicRegion anatomy) {
    if (anatomy == null) {
      return null;
    }
    AnatomicItem item = anatomy.getRegion();
    return item.getCodingScheme() == null
        ? item.getLegacyCode()
        : item.getCodingScheme().getDesignator() + ":" + item.getCodeValue();
  }

  private List<Entry> build(ImageKey key, AnatomicRegion anatomy) {
    Merged<WindowPreset> current = merged;
    List<WindowPreset> offered = new ArrayList<>();
    for (WindowPreset preset : current.entries()) {
      if (!preset.hidden()
          && preset.appliesTo(key.modality())
          && preset.when().matches(key.bitsStored(), key.hasRescale(), anatomy)) {
        offered.add(preset);
      }
    }
    // Stable: the presets of the body part move up, the others keep their order
    offered.sort(Comparator.comparing(p -> !p.when().prefers(anatomy)));
    WindowPreset defaultPreset = selectDefault(offered, current.layers(), key);

    Map<Integer, WindowPreset> keyOwners = keyOwners(offered, current.layers(), key);
    List<Entry> entries = new ArrayList<>(offered.size());
    for (WindowPreset preset : offered) {
      int keyCode = keyOwners.get(preset.keyCode()) == preset ? preset.keyCode() : 0;
      boolean isDefault = preset == defaultPreset;
      PresetWindowLevel shared = null;
      if (!preset.isPercent()) {
        shared = preset.toPresetWindowLevel();
        shared.setKeyCode(keyCode);
        shared.setFallbackDefault(isDefault);
      }
      entries.add(new Entry(preset, shared, keyCode, isDefault));
    }
    return List.copyOf(entries);
  }

  /** The preset that gets each key, by {@link LayeredEntries#pick}. */
  private static Map<Integer, WindowPreset> keyOwners(
      List<WindowPreset> offered, Map<String, Integer> layers, ImageKey key) {
    Map<Integer, List<WindowPreset>> byKey = new LinkedHashMap<>();
    for (WindowPreset preset : offered) {
      if (preset.keyCode() != 0) {
        byKey.computeIfAbsent(preset.keyCode(), k -> new ArrayList<>()).add(preset);
      }
    }
    Map<Integer, WindowPreset> owners = new HashMap<>();
    byKey.forEach(
        (keyCode, candidates) ->
            LayeredEntries.pick(
                    candidates,
                    p -> layer(layers, p),
                    WindowPreset::id,
                    "Window preset key " + candidates.getFirst().key(),
                    key.modality())
                .ifPresent(owner -> owners.put(keyCode, owner)));
    return owners;
  }

  private static int layer(Map<String, Integer> layers, WindowPreset preset) {
    return layers.getOrDefault(preset.id(), LAYER_BUILT_IN);
  }

  /** The flagged preset of the latest layer; the first one when a layer flags several. */
  private static WindowPreset selectDefault(
      List<WindowPreset> offered, Map<String, Integer> layers, ImageKey key) {
    List<WindowPreset> flagged = offered.stream().filter(WindowPreset::defaultPreset).toList();
    return LayeredEntries.pick(
            flagged,
            p -> layer(layers, p),
            WindowPreset::id,
            "Default window preset",
            key.modality())
        .orElse(null);
  }

  private void apply(Merged<WindowPreset> presets) {
    merged = presets;
    byImage.clear();
    listeners.forEach(Runnable::run);
  }

  // In memory only: the documents are read by reload()
  private synchronized Merged<WindowPreset> merge() {
    return LayeredEntries.merge(
        List.of(builtIn, site, List.copyOf(user.values())), "Window preset"); // NON-NLS
  }

  private static Optional<List<WindowPreset>> loadSite(Path file) {
    try {
      return Optional.of(WindowPresetJson.read(file));
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read the site window presets: {}", file, e);
      return Optional.empty();
    }
  }

  // An unreadable file leaves the current presets untouched and is kept aside on the next write
  private void readUser() {
    userUnreadable = false;
    if (userFile == null || !Files.isRegularFile(userFile)) {
      return;
    }
    try {
      List<WindowPreset> presets = WindowPresetJson.read(userFile);
      user.clear();
      presets.forEach(p -> user.put(p.id(), p));
    } catch (IOException | RuntimeException e) {
      userUnreadable = true;
      LOGGER.error("Cannot read the user window presets: {}", userFile, e);
    }
  }
}
