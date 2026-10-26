/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import org.weasis.core.api.setting.Setting;
import org.weasis.core.api.setting.SettingType;

/** The settings of the hanging protocols, declared in the catalog by the explorer. */
public final class HpSettings {

  private static final String GROUP = "weasis-dicom-explorer"; // NON-NLS

  /** How a study opens: hung by its best protocol, or on its first series as before. */
  public enum Opening {
    /** The best protocol hangs the study once its series are known; the first series otherwise. */
    PROTOCOL,
    /** The first series received opens the viewer, as before the hanging protocols. */
    FIRST_SERIES
  }

  public static final Setting<Opening> OPENING =
      Setting.builder(SettingType.of(Opening.class), GROUP, "weasis.hp.open", Opening.PROTOCOL)
          .since("5.0") // NON-NLS
          .description(
              "How a study opens: hung by its best hanging protocol, or on its first series")
          .declare();

  /** What happens when a study with a kept session is opened again. */
  public enum Restore {
    /** The latest session of the study is restored instead of hanging a protocol. */
    AUTO,
    /** The session is offered: a hint on the study row and an entry of the protocol bar. */
    OFFER,
    /** Sessions are restored on request only ({@code state:restore}). */
    NEVER
  }

  /** Whether the display is kept as a session when Weasis closes. */
  public static final Setting<Boolean> KEEP_ON_CLOSE =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.hp.keepOnClose", false) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Keep the display as a session when Weasis closes")
          .declare();

  /** How a study with a kept session opens again. */
  public static final Setting<Restore> RESTORE =
      Setting.builder(SettingType.of(Restore.class), GROUP, "weasis.hp.restore", Restore.OFFER)
          .since("5.0") // NON-NLS
          .description(
              "When a study with a kept session is opened again: restore it automatically, offer "
                  + "it, or never")
          .declare();

  /** The sessions kept per user, the oldest unpinned ones purged beyond it; 0 for no limit. */
  public static final Setting<Integer> SESSIONS_MAX =
      Setting.builder(SettingType.INTEGER, GROUP, "weasis.hp.sessions.max", 100) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Maximum number of sessions kept per user, 0 for no limit")
          .declare();

  /** Sessions not kept or restored for that many days are purged, pinned too; 0 for no limit. */
  public static final Setting<Integer> SESSIONS_MAX_DAYS =
      Setting.builder(SettingType.INTEGER, GROUP, "weasis.hp.sessions.max.days", 90) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Days after which a session not used again is deleted, 0 for no limit")
          .declare();

  private HpSettings() {}
}
