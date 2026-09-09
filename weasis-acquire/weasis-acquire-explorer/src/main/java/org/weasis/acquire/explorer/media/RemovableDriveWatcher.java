/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.media;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.util.HardwareInfo;
import org.weasis.core.api.util.HardwareInfo.Drive;
import org.weasis.core.api.util.ThreadUtil;

/**
 * Watches the removable drives — USB sticks, memory cards, external and optical disks — reported by
 * {@link HardwareInfo}, and reports what appears and disappears.
 *
 * <p>No operating system offers a portable notification for this, so the mounted volumes are polled
 * on a daemon thread and diffed by mount point. Listeners are called on the Event Dispatch Thread.
 *
 * <p>Nothing is probed on the calling thread: the first scan costs a few hundred milliseconds (more
 * on Windows, which queries WMI) and this watcher is created from a Swing constructor, so even the
 * initial listing runs on the polling thread and reaches the listener asynchronously.
 */
public class RemovableDriveWatcher implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(RemovableDriveWatcher.class);

  private static final long POLL_INTERVAL_SECONDS = 3;

  /** Notified on the Event Dispatch Thread when the set of removable drives changes. */
  public interface Listener {

    /**
     * The drives already mounted when the watcher started. They are not a user action, unlike
     * {@link #driveConnected}, so a listener should offer them without stealing the selection.
     */
    void drivesDetected(Collection<Drive> drives);

    void driveConnected(Drive drive);

    void driveDisconnected(Drive drive);
  }

  private final Listener listener;
  private final ScheduledExecutorService scheduler;

  /** Last known drives, keyed by mount point; only the polling thread touches it. */
  private Map<String, Drive> drives = Map.of();

  /**
   * Distinguishes the startup listing from a later hot-plug; only the polling thread touches it.
   */
  private boolean firstScan = true;

  public RemovableDriveWatcher(Listener listener) {
    this.listener = listener;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            ThreadUtil.namedDaemonThreadFactory("weasis-drive-watcher", true)); // NON-NLS
    scheduler.scheduleWithFixedDelay(this::poll, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  private void poll() {
    try {
      Map<String, Drive> previous = drives;
      Map<String, Drive> current = byMount(HardwareInfo.removableDrives());
      drives = current;

      if (firstScan) {
        firstScan = false;
        Collection<Drive> initial = current.values();
        if (!initial.isEmpty()) {
          GuiExecutor.execute(() -> listener.drivesDetected(initial));
        }
        return;
      }

      for (Drive drive : current.values()) {
        if (!previous.containsKey(drive.mount())) {
          LOGGER.info("Removable drive connected: {} ({})", drive.name(), drive.mount());
          GuiExecutor.execute(() -> listener.driveConnected(drive));
        }
      }
      for (Drive drive : previous.values()) {
        if (!current.containsKey(drive.mount())) {
          LOGGER.info("Removable drive disconnected: {} ({})", drive.name(), drive.mount());
          GuiExecutor.execute(() -> listener.driveDisconnected(drive));
        }
      }
    } catch (Exception e) { // NOSONAR a scheduled task must never let anything cancel its schedule
      LOGGER.debug("Cannot list the removable drives", e);
    }
  }

  private static Map<String, Drive> byMount(List<Drive> list) {
    Map<String, Drive> map = new LinkedHashMap<>();
    list.forEach(drive -> map.put(drive.mount(), drive));
    return Map.copyOf(map);
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }
}
