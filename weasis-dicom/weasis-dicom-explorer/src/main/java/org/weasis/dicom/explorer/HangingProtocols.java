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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

public class HangingProtocols {
  private static final Logger LOGGER = LoggerFactory.getLogger(HangingProtocols.class);

  public enum OpeningViewer {
    NONE(Messages.getString("none")),
    ONE_PATIENT(Messages.getString("only.the.first.patient")),
    ONE_PATIENT_CLEAN(Messages.getString("only.the.first.patient.remove.previous")),
    ALL_PATIENTS(Messages.getString("all.the.patients")),
    ALL_PATIENTS_CLEAN(Messages.getString("all.the.patients.remove.previous"));

    private final String title;

    OpeningViewer(String title) {
      this.title = title;
    }

    public String getTitle() {
      return title;
    }

    @Override
    public String toString() {
      return title;
    }

    public static OpeningViewer getOpeningViewer(String name, OpeningViewer defaultOpeningViewer) {
      if (StringUtil.hasText(name)) {
        try {
          return OpeningViewer.valueOf(name);
        } catch (Exception e) {
          LOGGER.error("Cannot get OpeningViewer from {}", name, e);
        }
      }
      return defaultOpeningViewer;
    }
  }
}
