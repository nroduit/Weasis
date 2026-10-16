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

  private HpSettings() {}
}
