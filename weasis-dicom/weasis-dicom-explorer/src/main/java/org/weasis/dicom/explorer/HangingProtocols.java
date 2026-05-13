/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

public class HangingProtocols {

  /** Key for the "close previous viewers" flag in local persistence. */
  public static final String CLOSE_PREVIOUS_KEY = "weasis.open.viewer.clean";

  /**
   * Controls whether a viewer tab should be opened for a patient.
   *
   * <p>{@code NONE} is used only programmatically for internal DICOM objects (KO, SR) that must be
   * imported without opening a viewer. {@code ALL_PATIENTS} is the standard mode: every patient
   * gets a tab, with the first one focused and subsequent ones opening in the background.
   */
  public enum OpeningViewer {
    NONE,
    ALL_PATIENTS
  }

  public static boolean isClosePreviousFromPreferences() {
    return Boolean.parseBoolean(
        LocalPersistence.getProperties().getProperty(CLOSE_PREVIOUS_KEY, "false"));
  }

  public static void setClosePrevious(boolean closePrevious) {
    LocalPersistence.getProperties()
        .setProperty(CLOSE_PREVIOUS_KEY, Boolean.toString(closePrevious));
  }
}
