/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.weasis.core.api.media.data.MediaSeriesGroup;

/** Centralized manager for patient selection state between DicomExplorer and PatientPane. */
public class PatientSelectionManager {

  private final DicomExplorer explorer;
  private final List<PatientSelectionListener> listeners = new CopyOnWriteArrayList<>();

  public interface PatientSelectionListener {
    void patientSelected(MediaSeriesGroup patient);

    void patientCleared();
  }

  public PatientSelectionManager(DicomExplorer explorer) {
    this.explorer = Objects.requireNonNull(explorer);
  }

  /**
   * Sets the current patient and notifies all listeners.
   *
   * @param patient the patient to set as current
   */
  public void setCurrentPatient(MediaSeriesGroup patient) {
    if (!isCurrentPatient(patient)) {
      notifyPatientChanged(patient);
    }
  }

  /** Clears the current patient and notifies all listeners. */
  public void clearCurrentPatient() {
    if (getCurrentPatient().isPresent()) {
      notifyPatientCleared();
    }
  }

  /**
   * Gets the current patient.
   *
   * @return an Optional containing the current patient, or empty if no patient is selected
   */
  public Optional<MediaSeriesGroup> getCurrentPatient() {
    return explorer.getSelectedPatientPane().getCurrentPatient();
  }

  /**
   * Checks if the given patient is the current patient.
   *
   * @param patient the patient to check
   * @return true if the given patient is the current patient, false otherwise
   */
  public boolean isCurrentPatient(MediaSeriesGroup patient) {
    return Objects.equals(getCurrentPatient().orElse(null), patient);
  }

  /**
   * Adds a listener to be notified of patient selection changes.
   *
   * @param listener the listener to add
   */
  public void addPatientSelectionListener(PatientSelectionListener listener) {
    listeners.add(listener);
  }

  /**
   * Removes a listener from patient selection changes.
   *
   * @param listener the listener to remove
   */
  public void removePatientSelectionListener(PatientSelectionListener listener) {
    listeners.remove(listener);
  }

  private void notifyPatientChanged(MediaSeriesGroup patient) {
    for (PatientSelectionListener listener : listeners) {
      listener.patientSelected(patient);
    }
  }

  private void notifyPatientCleared() {
    for (PatientSelectionListener listener : listeners) {
      listener.patientCleared();
    }
  }
}
