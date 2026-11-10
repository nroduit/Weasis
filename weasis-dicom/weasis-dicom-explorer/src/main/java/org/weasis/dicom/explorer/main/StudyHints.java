/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import java.util.List;
import java.util.Optional;
import javax.swing.SwingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.hp.HpStudies;
import org.weasis.dicom.explorer.hp.Session;
import org.weasis.dicom.explorer.hp.SessionActions;

/**
 * The hint a study row shows after its title: the protocol that would hang the study and the kept
 * session offered for it. Computed off the event thread, since it matches the protocols.
 */
public final class StudyHints {

  private static final Logger LOGGER = LoggerFactory.getLogger(StudyHints.class);

  private StudyHints() {}

  /** Computes the hint of a study pane and sets it. */
  public static void refresh(StudyPane pane, DicomModel model) {
    MediaSeriesGroup study = pane.getDicomStudy();
    new SwingWorker<String, Void>() {
      @Override
      protected String doInBackground() {
        List<ProtocolScore> candidates;
        try {
          candidates = HpStudies.candidates(model, study);
        } catch (RuntimeException e) {
          LOGGER.debug("No protocol hint for {}", study, e);
          candidates = List.of();
        }
        return hint(candidates, SessionActions.offerFor(study));
      }

      @Override
      protected void done() {
        try {
          pane.setHint(get());
        } catch (Exception e) {
          LOGGER.debug("No hint for {}", study, e);
        }
      }
    }.execute();
  }

  /** The hint text: the best protocol's name, then the session; null when there is neither. */
  static String hint(List<ProtocolScore> candidates, Optional<Session> session) {
    StringBuilder hint = new StringBuilder();
    if (!candidates.isEmpty()) {
      ProtocolScore best = candidates.getFirst();
      String name = best.document().protocol().getHangingProtocolName();
      hint.append(name == null ? best.document().id() : name);
    }
    session.ifPresent(
        s -> {
          if (!hint.isEmpty()) {
            hint.append(", ");
          }
          hint.append(Messages.format("StudyHints.session", SessionActions.label(s)));
        });
    return hint.isEmpty() ? null : hint.toString();
  }
}
