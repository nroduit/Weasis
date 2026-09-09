/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import com.formdev.flatlaf.util.SystemInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;
import oshi.hardware.CentralProcessor;
import oshi.hardware.CentralProcessor.ProcessorIdentifier;
import oshi.hardware.ComputerSystem;
import oshi.hardware.GraphicsCard;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.GlobalConfig;

/**
 * Describes the machine Weasis runs on: processor, graphics adapters, memory and mounted drives.
 *
 * <p>Everything is read through <a href="https://github.com/oshi/oshi">OSHI</a>, which queries the
 * native operating-system interfaces (WMI, sysctl, {@code /proc} and {@code /sys}) and therefore
 * knows far more than the JVM does — the processor model, the physical core count behind the
 * hyper-threaded logical ones, the graphics adapters even when no 3D view was ever opened, and
 * which of the mounted volumes are removable.
 *
 * <p>The {@code oshi-core-ffm} variant is used: it calls the operating system through {@link
 * java.lang.foreign}, so it needs no JNA and no bundled native dispatch library. Calling a
 * restricted method requires {@code --enable-native-access=ALL-UNNAMED}, which the packaged
 * launchers pass; without it the JVM only warns today, but a future release will block the call.
 *
 * <p>That backend may be unavailable or refuse to load on an exotic platform, so every probe is
 * fail-safe: it degrades to an empty or zero value and never propagates an error to the caller.
 * Values that cannot change while the application runs are read once and cached; memory and drives
 * are read on each call.
 *
 * <p>This class is deliberately <em>not</em> the source of the memory budget: {@link SystemMemory}
 * keeps using the JVM bean for that, because it is cgroup-aware and OSHI reports the host's memory
 * even inside a container.
 */
public final class HardwareInfo {
  private static final Logger LOGGER = LoggerFactory.getLogger(HardwareInfo.class);

  /** Directories under which the desktop environments mount removable media. */
  private static final List<String> REMOVABLE_MOUNT_ROOTS =
      SystemInfo.isMacOS
          ? List.of("/Volumes/")
          : List.of("/media/", "/run/media/", "/mnt/"); // NON-NLS

  /** OSHI's Linux mount-scan exclusion list, whose default hides {@code /run}. */
  private static final String LINUX_PATH_EXCLUDES = "oshi.os.linux.filesystem.path.excludes";

  /** File systems of optical media. */
  private static final Set<String> OPTICAL_FILE_SYSTEMS =
      Set.of("iso9660", "udf", "cd9660", "cdfs"); // NON-NLS

  /** Virtual file systems that are never user media, whatever they are mounted on. */
  private static final Set<String> PSEUDO_FILE_SYSTEMS =
      Set.of(
          "autofs",
          "bpf",
          "cgroup",
          "cgroup2",
          "configfs",
          "debugfs",
          "devpts",
          "devtmpfs",
          "efivarfs",
          "hugetlbfs",
          "mqueue",
          "none",
          "overlay",
          "proc",
          "pstore",
          "ramfs",
          "securityfs",
          "squashfs",
          "sysfs",
          "tmpfs",
          "tracefs"); // NON-NLS

  /** A processor: the marketing name, its microarchitecture, core counts and top frequency. */
  public record Cpu(
      String name,
      String microarchitecture,
      int physicalCores,
      int logicalCores,
      long maxFrequency) {}

  /** A graphics adapter as reported by the operating system, whether or not it drives OpenGL. */
  public record GraphicsAdapter(String name, String vendor, long videoMemory) {}

  /** A mounted volume: {@code name} is its label when it has one, otherwise its mount point. */
  public record Drive(String name, String mount, String type, long totalBytes, long freeBytes) {}

  /**
   * A mount point as the operating system reports it. {@code description} is the drive type on
   * Windows ({@code Network drive}, {@code CD-ROM}, ...) and {@code local} is false for network
   * file systems (the {@code MNT_LOCAL} flag on macOS, the file system type elsewhere).
   */
  public record Volume(String mount, String type, String description, boolean local) {}

  private HardwareInfo() {}

  /** Holds the OSHI entry point, or {@code null} when the native backend is unusable. */
  private static final class Backend {
    static final oshi.ffm.SystemInfo SYSTEM = create();

    private Backend() {}

    private static oshi.ffm.SystemInfo create() {
      try {
        widenLinuxMountScan();
        oshi.ffm.SystemInfo system = new oshi.ffm.SystemInfo();
        if (!system.isAvailable()) {
          LOGGER.warn("The hardware probe does not support this platform");
          return null;
        }
        system.getHardware().getMemory().getTotal(); // fails fast if the backend cannot load
        return system;
      } catch (Throwable e) { // NOSONAR an unusable native backend must not prevent startup
        LOGGER.warn("No hardware probe available on this platform", e);
        return null;
      }
    }
  }

  /**
   * OSHI hides every mount under {@code /run} on Linux, which is exactly where udisks2 mounts
   * removable media on a systemd desktop ({@code /run/media/<user>/<label>}); without this the
   * drives would never be seen there. Dropping that one exclusion also lets in {@code /run/user},
   * {@code /run/snapd} and similar, but none of them sits under a {@link #REMOVABLE_MOUNT_ROOTS}
   * directory, so {@link #isRemovable} rejects them anyway. Must run before anything reads the
   * property, hence before the first probe.
   */
  private static void widenLinuxMountScan() {
    if (SystemInfo.isLinux) {
      GlobalConfig.set(LINUX_PATH_EXCLUDES, "/sys**,/proc**,/dev,**/shm"); // NON-NLS
    }
  }

  /** Holds the descriptors that cannot change while the application runs. */
  private static final class Descriptors {
    static final Cpu CPU = readCpu();
    static final List<GraphicsAdapter> GRAPHICS = readGraphicsAdapters();
    static final String COMPUTER = readComputerModel();
    static final String OS = readOperatingSystem();

    private Descriptors() {}
  }

  /**
   * @return true when the native probe is available; every accessor is safe to call regardless.
   */
  public static boolean isAvailable() {
    return Backend.SYSTEM != null;
  }

  /**
   * @return the processor description, or empty when the probe is unavailable.
   */
  public static Optional<Cpu> cpu() {
    return Optional.ofNullable(Descriptors.CPU);
  }

  /**
   * @return the graphics adapters known to the operating system, in the order it lists them.
   */
  public static List<GraphicsAdapter> graphicsAdapters() {
    return Descriptors.GRAPHICS;
  }

  /**
   * @return the machine manufacturer and model (e.g. {@code LENOVO ThinkStation P620}), or an empty
   *     string when unknown.
   */
  public static String computerModel() {
    return Descriptors.COMPUTER;
  }

  /**
   * @return the operating system with its version and build, or an empty string when unknown.
   */
  public static String operatingSystem() {
    return Descriptors.OS;
  }

  /**
   * @return the installed physical memory in bytes, or 0 when unknown.
   */
  public static long totalMemory() {
    return probe(system -> system.getHardware().getMemory().getTotal(), 0L);
  }

  /**
   * Memory the operating system can hand out right away. Unlike the free memory reported by the JVM
   * bean, it counts the reclaimable page cache, which on Linux is most of what looks used.
   *
   * @return the available physical memory in bytes, or 0 when unknown.
   */
  public static long availableMemory() {
    return probe(system -> system.getHardware().getMemory().getAvailable(), 0L);
  }

  /**
   * Lists the mounted volumes that hold removable media — USB sticks, memory cards and external
   * disks — so they can be offered as media sources.
   *
   * <p>Windows reports removability directly, as a {@code Removable drive} description. Linux and
   * macOS do not, so a volume mounted under the directory their desktop environment reserves for
   * removable media is taken as removable: {@code /media}, {@code /run/media} or {@code /mnt} on
   * Linux, {@code /Volumes} on macOS. Network shares and virtual file systems are excluded
   * everywhere.
   *
   * <p>Optical discs are included on Linux and macOS, where they mount under those directories like
   * any other medium, but not on Windows: OSHI does not consider a CD-ROM drive local, so it never
   * reaches the local-only scan used here.
   *
   * @return the removable drives, empty when there is none or when the probe is unavailable.
   */
  public static List<Drive> removableDrives() {
    List<OSFileStore> stores =
        probe(system -> system.getOperatingSystem().getFileSystem().getFileStores(true), List.of());
    List<Drive> drives = new ArrayList<>();
    for (OSFileStore store : stores) {
      if (isRemovable(store.getMount(), store.getType(), store.getDescription())) {
        drives.add(
            new Drive(
                driveName(store),
                store.getMount(),
                store.getType(),
                store.getTotalSpace(),
                store.getUsableSpace()));
      }
    }
    return drives;
  }

  /**
   * Lists the mounted optical discs (CD, DVD, Blu-ray), the media DICOM file-sets are usually
   * shipped on. Windows reports the drive type, Linux and macOS the file system type.
   *
   * @return the optical drives, empty when there is none or when the probe is unavailable.
   */
  public static List<Drive> opticalDrives() {
    List<Drive> drives = new ArrayList<>();
    for (Volume volume : mountedVolumes()) {
      if (isOptical(volume.type(), volume.description())) {
        drives.add(
            new Drive(volume.mount(), volume.mount(), volume.type(), 0, 0)); // sizes not needed
      }
    }
    return drives;
  }

  /** Applies the optical-media rule of {@link #opticalDrives()} to one mounted volume. */
  static boolean isOptical(String type, String description) {
    return lower(description).contains("cd-rom") // NON-NLS
        || OPTICAL_FILE_SYSTEMS.contains(lower(type));
  }

  /**
   * Lists every mount point, network shares and optical media included, so callers can tell how a
   * path is reached. Enumerating costs a WMI query on Windows, so cache the answer.
   *
   * @return the mounted volumes, empty when the probe is unavailable.
   */
  public static List<Volume> mountedVolumes() {
    List<OSFileStore> stores =
        probe(
            system -> system.getOperatingSystem().getFileSystem().getFileStores(false), List.of());
    List<Volume> volumes = new ArrayList<>(stores.size());
    for (OSFileStore store : stores) {
      if (StringUtil.hasText(store.getMount())) {
        volumes.add(
            new Volume(store.getMount(), store.getType(), store.getDescription(), store.isLocal()));
      }
    }
    return volumes;
  }

  /** Applies the removability rule of {@link #removableDrives()} to one mounted volume. */
  static boolean isRemovable(String mount, String type, String description) {
    if (!StringUtil.hasText(mount) || PSEUDO_FILE_SYSTEMS.contains(lower(type))) {
      return false;
    }
    String kind = lower(description);
    if (kind.contains("network") || kind.contains("ram disk")) { // NON-NLS
      return false;
    }
    if (kind.contains("removable")) { // NON-NLS
      return true;
    }
    return REMOVABLE_MOUNT_ROOTS.stream().anyMatch(mount::startsWith);
  }

  private static String driveName(OSFileStore store) {
    String label = store.getLabel();
    return StringUtil.hasText(label) ? label : store.getMount();
  }

  private static Cpu readCpu() {
    return probe(
        system -> {
          CentralProcessor processor = system.getHardware().getProcessor();
          ProcessorIdentifier id = processor.getProcessorIdentifier();
          return new Cpu(
              id.getName().trim(),
              id.getMicroarchitecture(),
              processor.getPhysicalProcessorCount(),
              processor.getLogicalProcessorCount(),
              processor.getMaxFreq());
        },
        null);
  }

  private static List<GraphicsAdapter> readGraphicsAdapters() {
    List<GraphicsCard> cards = probe(system -> system.getHardware().getGraphicsCards(), List.of());
    return cards.stream()
        .map(
            card ->
                new GraphicsAdapter(card.getName(), cleanVendor(card.getVendor()), card.getVRam()))
        .toList();
  }

  /**
   * Drops the PCI identifier OSHI appends to the vendor, e.g. {@code NVIDIA Corporation (0x10de)}.
   */
  private static String cleanVendor(String vendor) {
    if (vendor == null) {
      return "";
    }
    int index = vendor.indexOf(" (0x"); // NON-NLS
    return (index > 0 ? vendor.substring(0, index) : vendor).trim();
  }

  private static String readComputerModel() {
    return probe(
        system -> {
          ComputerSystem computer = system.getHardware().getComputerSystem();
          return (computer.getManufacturer() + " " + computer.getModel()).trim();
        },
        "");
  }

  private static String readOperatingSystem() {
    return probe(
        system -> {
          OperatingSystem os = system.getOperatingSystem();
          return (os.getFamily() + " " + os.getVersionInfo()).trim();
        },
        "");
  }

  private static String lower(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private static <T> T probe(Function<oshi.ffm.SystemInfo, T> reader, T fallback) {
    oshi.ffm.SystemInfo system = Backend.SYSTEM;
    if (system == null) {
      return fallback;
    }
    try {
      T value = reader.apply(system);
      return value == null ? fallback : value;
    } catch (Throwable e) { // NOSONAR a failing probe must never break the caller
      LOGGER.debug("Hardware probe failed", e);
      return fallback;
    }
  }
}
