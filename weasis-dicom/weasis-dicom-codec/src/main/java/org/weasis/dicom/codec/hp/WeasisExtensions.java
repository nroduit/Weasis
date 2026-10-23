/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.weasis.core.api.util.JsonUtil;

/**
 * What a Weasis protocol says beyond the DICOM Hanging Protocol IOD: display state by reference to
 * the registries (preset, color map, measurement profile ids, never copied values), the viewer type
 * and the non-image cells of a display set, the workspace placement, the masking profile, the
 * loading strategy and the UI profile. Members this version does not know are kept in {@link
 * #extra} and written back unchanged, so an older Weasis never loses what a newer one wrote.
 *
 * @param imageSets the extension of each image set, by Image Set Number
 * @param displaySets the extension of each display set, by Display Set Number
 * @param steps the UI profile of each presentation group (a reading step), by group number
 * @param placement the workspace node tree the display sets live in, in the form of the workspace
 *     snapshot; null when the protocol lets the engine place the viewers
 * @param maskingProfile the identity masking profile applied when the protocol hangs
 * @param loading the loading strategy of the protocol, overridden per image set
 * @param uiProfile the UI profile of the protocol
 * @param extra members of the {@code weasis} block this version does not read
 */
public record WeasisExtensions(
    Map<Integer, ImageSet> imageSets,
    Map<Integer, DisplaySet> displaySets,
    Map<Integer, String> steps,
    JsonObject placement,
    String maskingProfile,
    Loading loading,
    String uiProfile,
    JsonObject extra) {

  public static final WeasisExtensions NONE =
      new WeasisExtensions(
          Map.of(), Map.of(), Map.of(), null, null, null, null, JsonValue.EMPTY_JSON_OBJECT);

  private static final Set<String> KNOWN =
      Set.of(
          "imageSets", // NON-NLS
          "displaySets", // NON-NLS
          "steps", // NON-NLS
          "placement", // NON-NLS
          "maskingProfile", // NON-NLS
          "loading", // NON-NLS
          "uiProfile"); // NON-NLS

  public WeasisExtensions {
    imageSets = imageSets == null ? Map.of() : Map.copyOf(imageSets);
    displaySets = displaySets == null ? Map.of() : Map.copyOf(displaySets);
    steps = steps == null ? Map.of() : Map.copyOf(steps);
  }

  /**
   * The extension of one image set: selectors on the normalized series features next to the DICOM
   * selectors on raw attributes, filters and weighted preferences alike.
   *
   * @param label a label for the why panel, null for the DICOM label
   * @param selectors the feature selectors, in order
   */
  public record ImageSet(String label, List<FeatureSelector> selectors) {
    public ImageSet {
      selectors = selectors == null ? List.of() : List.copyOf(selectors);
    }
  }

  /** How a series is viewed: 2D, MPR, 3D, curved MPR, waveform, SR, audio, video, document. */
  public enum ViewerType {
    VIEWER_2D,
    MPR,
    VOLUME_3D,
    CURVED_MPR,
    WAVEFORM,
    SR,
    AUDIO,
    VIDEO,
    DOCUMENT
  }

  /**
   * The extension of one display set.
   *
   * @param viewerType the viewer, null for the default of the series
   * @param display the display state by reference
   * @param cells the non-image cells that follow the image cell
   * @param loading the loading strategy of the image set, overriding the protocol's
   * @param repeat when set, the display set is repeated for every series of its image set that no
   *     cell of the step shows yet, up to that many cells ({@code 0}: no limit); the grid grows
   *     with the cells. One cell per series, the Weasis way of hanging a study whose series are all
   *     to be seen (radiographs, cine runs, ultrasound clips)
   */
  public record DisplaySet(
      ViewerType viewerType, Display display, List<Cell> cells, Loading loading, Integer repeat) {

    public DisplaySet(ViewerType viewerType, Display display, List<Cell> cells, Loading loading) {
      this(viewerType, display, cells, loading, null);
    }
  }

  /**
   * The display state by reference.
   *
   * @param preset W/L preset id
   * @param colorMap color map id
   * @param lutShape LUT shape name
   * @param preset3d 3D preset id
   * @param fusion fusion configuration
   * @param mpr MPR configuration
   * @param measurementProfile measurement profile id
   * @param zoomMode zoom mode name, as a {@code ZoomType}
   * @param synch synchronization rule, as a {@code SynchView} command
   * @param scaleGroup the cells of a step sharing it show their images at the same physical scale
   *     (e.g. current and prior mammograms), the largest that fits all of them
   */
  public record Display(
      String preset,
      String colorMap,
      String lutShape,
      String preset3d,
      Fusion fusion,
      Mpr mpr,
      String measurementProfile,
      String zoomMode,
      String synch,
      String scaleGroup) {

    public Display(
        String preset,
        String colorMap,
        String lutShape,
        String preset3d,
        Fusion fusion,
        Mpr mpr,
        String measurementProfile,
        String zoomMode,
        String synch) {
      this(
          preset,
          colorMap,
          lutShape,
          preset3d,
          fusion,
          mpr,
          measurementProfile,
          zoomMode,
          synch,
          null);
    }
  }

  /**
   * @param base image set number of the base series
   * @param overlay image set number of the overlay series
   * @param colorMap color map id of the overlay
   * @param opacity opacity of the overlay, 0 to 1
   */
  public record Fusion(Integer base, Integer overlay, String colorMap, Double opacity) {}

  /**
   * @param planes the planes shown, {@code AXIAL}, {@code CORONAL}, {@code SAGITTAL}
   * @param thickness slab thickness in mm
   * @param mode {@code MEAN}, {@code MIP} or {@code MINIP}
   */
  public record Mpr(List<String> planes, Double thickness, String mode) {}

  /**
   * A non-image cell of a layout.
   *
   * @param type {@code INFO}, {@code HISTOGRAM}, {@code PROFILE}, {@code WAVEFORM}, {@code SR_TEXT}
   * @param follows the Image Box Number of the image cell it follows
   */
  public record Cell(String type, Integer follows) {}

  /**
   * @param strategy {@code ALL}, {@code PROTOCOL_THEN_REST}, {@code PROTOCOL} or {@code NONE}
   * @param order {@code DISPLAY}, {@code STEPS} or {@code SERIES}
   * @param sampling 1/n sampling of the images, 1 for all
   * @param skip true when the image set is not downloaded
   */
  public record Loading(String strategy, String order, Integer sampling, Boolean skip) {}

  public JsonObject toJson() {
    JsonObjectBuilder json = Json.createObjectBuilder();
    if (!imageSets.isEmpty()) {
      JsonObjectBuilder sets = Json.createObjectBuilder();
      imageSets.forEach((n, is) -> sets.add(String.valueOf(n), imageSet(is)));
      json.add("imageSets", sets); // NON-NLS
    }
    if (!displaySets.isEmpty()) {
      JsonObjectBuilder sets = Json.createObjectBuilder();
      displaySets.forEach((n, ds) -> sets.add(String.valueOf(n), displaySet(ds)));
      json.add("displaySets", sets); // NON-NLS
    }
    if (!steps.isEmpty()) {
      JsonObjectBuilder s = Json.createObjectBuilder();
      steps.forEach((n, profile) -> s.add(String.valueOf(n), profile));
      json.add("steps", s); // NON-NLS
    }
    if (placement != null) {
      json.add("placement", placement); // NON-NLS
    }
    JsonUtil.addIfPresent(json, "maskingProfile", maskingProfile); // NON-NLS
    if (loading != null) {
      json.add("loading", loading(loading)); // NON-NLS
    }
    JsonUtil.addIfPresent(json, "uiProfile", uiProfile); // NON-NLS
    if (extra != null) {
      extra.forEach(
          (k, v) -> {
            if (!KNOWN.contains(k)) {
              json.add(k, v);
            }
          });
    }
    return json.build();
  }

  public static WeasisExtensions fromJson(JsonObject json) {
    if (json == null) {
      return NONE;
    }
    Map<Integer, ImageSet> imageSets = new LinkedHashMap<>();
    if (json.get("imageSets") instanceof JsonObject o) { // NON-NLS
      o.forEach(
          (k, v) -> {
            if (v instanceof JsonObject is) {
              imageSets.put(Integer.valueOf(k), imageSet(is));
            }
          });
    }
    Map<Integer, DisplaySet> sets = new LinkedHashMap<>();
    if (json.get("displaySets") instanceof JsonObject o) { // NON-NLS
      o.forEach(
          (k, v) -> {
            if (v instanceof JsonObject ds) {
              sets.put(Integer.valueOf(k), displaySet(ds));
            }
          });
    }
    Map<Integer, String> steps = new LinkedHashMap<>();
    if (json.get("steps") instanceof JsonObject o) { // NON-NLS
      o.forEach(
          (k, v) -> {
            if (v instanceof JsonString s) {
              steps.put(Integer.valueOf(k), s.getString());
            }
          });
    }
    JsonObjectBuilder extra = Json.createObjectBuilder();
    json.forEach(
        (k, v) -> {
          if (!KNOWN.contains(k)) {
            extra.add(k, v);
          }
        });
    return new WeasisExtensions(
        Map.copyOf(imageSets),
        Map.copyOf(sets),
        Map.copyOf(steps),
        json.get("placement") instanceof JsonObject p ? p : null, // NON-NLS
        json.getString("maskingProfile", null), // NON-NLS
        json.get("loading") instanceof JsonObject l ? loading(l) : null, // NON-NLS
        json.getString("uiProfile", null), // NON-NLS
        extra.build());
  }

  private static JsonObjectBuilder imageSet(ImageSet is) {
    JsonObjectBuilder json = Json.createObjectBuilder();
    JsonUtil.addIfPresent(json, "label", is.label()); // NON-NLS
    JsonArrayBuilder selectors = Json.createArrayBuilder();
    is.selectors().forEach(sel -> selectors.add(sel.toJson()));
    return json.add("selectors", selectors); // NON-NLS
  }

  private static ImageSet imageSet(JsonObject json) {
    List<FeatureSelector> selectors =
        json.get("selectors") instanceof JsonArray a // NON-NLS
            ? JsonUtil.objects(a).stream()
                .map(FeatureSelector::fromJson)
                .flatMap(Optional::stream)
                .toList()
            : List.of();
    return new ImageSet(json.getString("label", null), selectors); // NON-NLS
  }

  private static JsonObjectBuilder displaySet(DisplaySet ds) {
    JsonObjectBuilder json = Json.createObjectBuilder();
    if (ds.viewerType() != null) {
      json.add("viewer", ds.viewerType().name()); // NON-NLS
    }
    if (ds.display() != null) {
      json.add("display", display(ds.display())); // NON-NLS
    }
    if (ds.cells() != null && !ds.cells().isEmpty()) {
      JsonArrayBuilder cells = Json.createArrayBuilder();
      for (Cell cell : ds.cells()) {
        JsonObjectBuilder c = Json.createObjectBuilder().add("type", cell.type()); // NON-NLS
        if (cell.follows() != null) {
          c.add("follows", cell.follows()); // NON-NLS
        }
        cells.add(c);
      }
      json.add("cells", cells); // NON-NLS
    }
    if (ds.loading() != null) {
      json.add("loading", loading(ds.loading())); // NON-NLS
    }
    if (ds.repeat() != null) {
      json.add("repeat", ds.repeat()); // NON-NLS
    }
    return json;
  }

  private static DisplaySet displaySet(JsonObject json) {
    List<Cell> cells = List.of();
    if (json.get("cells") instanceof JsonArray a) { // NON-NLS
      cells =
          JsonUtil.objects(a).stream()
              .map(c -> new Cell(c.getString("type", null), integer(c, "follows"))) // NON-NLS
              .toList();
    }
    String viewer = json.getString("viewer", null); // NON-NLS
    return new DisplaySet(
        viewer == null ? null : ViewerType.valueOf(viewer),
        json.get("display") instanceof JsonObject d ? display(d) : null, // NON-NLS
        cells,
        json.get("loading") instanceof JsonObject l ? loading(l) : null, // NON-NLS
        integer(json, "repeat")); // NON-NLS
  }

  private static JsonObjectBuilder display(Display d) {
    JsonObjectBuilder json = Json.createObjectBuilder();
    JsonUtil.addIfPresent(json, "preset", d.preset()); // NON-NLS
    JsonUtil.addIfPresent(json, "colorMap", d.colorMap()); // NON-NLS
    JsonUtil.addIfPresent(json, "lutShape", d.lutShape()); // NON-NLS
    JsonUtil.addIfPresent(json, "preset3d", d.preset3d()); // NON-NLS
    if (d.fusion() != null) {
      JsonObjectBuilder f = Json.createObjectBuilder();
      if (d.fusion().base() != null) {
        f.add("base", d.fusion().base()); // NON-NLS
      }
      if (d.fusion().overlay() != null) {
        f.add("overlay", d.fusion().overlay()); // NON-NLS
      }
      JsonUtil.addIfPresent(f, "colorMap", d.fusion().colorMap()); // NON-NLS
      if (d.fusion().opacity() != null) {
        f.add("opacity", d.fusion().opacity()); // NON-NLS
      }
      json.add("fusion", f); // NON-NLS
    }
    if (d.mpr() != null) {
      JsonObjectBuilder m = Json.createObjectBuilder();
      if (d.mpr().planes() != null) {
        JsonArrayBuilder planes = Json.createArrayBuilder();
        d.mpr().planes().forEach(planes::add);
        m.add("planes", planes); // NON-NLS
      }
      if (d.mpr().thickness() != null) {
        m.add("thickness", d.mpr().thickness()); // NON-NLS
      }
      JsonUtil.addIfPresent(m, "mode", d.mpr().mode()); // NON-NLS
      json.add("mpr", m); // NON-NLS
    }
    JsonUtil.addIfPresent(json, "measurementProfile", d.measurementProfile()); // NON-NLS
    JsonUtil.addIfPresent(json, "zoomMode", d.zoomMode()); // NON-NLS
    JsonUtil.addIfPresent(json, "synch", d.synch()); // NON-NLS
    JsonUtil.addIfPresent(json, "scaleGroup", d.scaleGroup()); // NON-NLS
    return json;
  }

  private static Display display(JsonObject json) {
    Fusion fusion = null;
    if (json.get("fusion") instanceof JsonObject f) { // NON-NLS
      fusion =
          new Fusion(
              integer(f, "base"), // NON-NLS
              integer(f, "overlay"), // NON-NLS
              f.getString("colorMap", null), // NON-NLS
              f.containsKey("opacity")
                  ? f.getJsonNumber("opacity").doubleValue()
                  : null); // NON-NLS
    }
    Mpr mpr = null;
    if (json.get("mpr") instanceof JsonObject m) { // NON-NLS
      mpr =
          new Mpr(
              m.containsKey("planes") ? JsonUtil.getStringList(m, "planes") : null, // NON-NLS
              m.containsKey("thickness")
                  ? m.getJsonNumber("thickness").doubleValue()
                  : null, // NON-NLS
              m.getString("mode", null)); // NON-NLS
    }
    return new Display(
        json.getString("preset", null), // NON-NLS
        json.getString("colorMap", null), // NON-NLS
        json.getString("lutShape", null), // NON-NLS
        json.getString("preset3d", null), // NON-NLS
        fusion,
        mpr,
        json.getString("measurementProfile", null), // NON-NLS
        json.getString("zoomMode", null), // NON-NLS
        json.getString("synch", null), // NON-NLS
        json.getString("scaleGroup", null)); // NON-NLS
  }

  private static JsonObjectBuilder loading(Loading l) {
    JsonObjectBuilder json = Json.createObjectBuilder();
    JsonUtil.addIfPresent(json, "strategy", l.strategy()); // NON-NLS
    JsonUtil.addIfPresent(json, "order", l.order()); // NON-NLS
    if (l.sampling() != null) {
      json.add("sampling", l.sampling()); // NON-NLS
    }
    if (l.skip() != null) {
      json.add("skip", l.skip()); // NON-NLS
    }
    return json;
  }

  private static Loading loading(JsonObject json) {
    return new Loading(
        json.getString("strategy", null), // NON-NLS
        json.getString("order", null), // NON-NLS
        integer(json, "sampling"), // NON-NLS
        json.containsKey("skip") ? json.getBoolean("skip") : null); // NON-NLS
  }

  private static Integer integer(JsonObject json, String key) {
    return json.containsKey(key) && !json.isNull(key) ? json.getInt(key) : null;
  }
}
