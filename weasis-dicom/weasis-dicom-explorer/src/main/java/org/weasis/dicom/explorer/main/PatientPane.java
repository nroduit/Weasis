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

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;

/**
 * A panel component that displays patient information and manages study panes within a DICOM
 * explorer interface.
 */
public class PatientPane extends JPanel
    implements PatientSelectionManager.PatientSelectionListener {

  private static final String VERTICAL_LAYOUT_CONSTRAINTS = "fillx, flowy, insets 0";
  private static final String HORIZONTAL_LAYOUT_CONSTRAINTS = "fillx, flowx, insets 0";
  private static final String COLUMN_CONSTRAINTS = "[fill]";
  private static final Dimension STUDY_PANE_SIZE = new Dimension(50, 50);
  private final DicomExplorer explorer;
  private MediaSeriesGroup currentPatient;

  public PatientPane(DicomExplorer explorer) {
    this.explorer = Objects.requireNonNull(explorer);
    initializeComponent();
  }

  private void initializeComponent() {
    setAlignmentX(LEFT_ALIGNMENT);
    setAlignmentY(TOP_ALIGNMENT);
    setFocusable(false);
  }

  @Override
  public void patientSelected(MediaSeriesGroup patient) {
    this.currentPatient = patient;
    showTitle(false);
    refreshLayout();
  }

  @Override
  public void patientCleared() {
    this.currentPatient = null;
    removeAll();
    revalidate();
    repaint();
  }

  /**
   * Shows or hides the title border based on the current patient(s).
   *
   * @param show true to show the title, false to hide it
   */
  public void showTitle(boolean show) {
    Optional<MediaSeriesGroup> patient = getCurrentPatient();
    if (show && patient.isPresent()) {
      TitledBorder title = GuiUtils.getTitledBorder(patient.get().toString());
      setBorder(BorderFactory.createCompoundBorder(GuiUtils.getEmptyBorder(0, 3, 5, 3), title));
    } else {
      setBorder(BorderFactory.createEmptyBorder());
    }
  }

  /**
   * Checks if a specific study is currently visible in this patient pane.
   *
   * @param study the study to check for visibility
   * @return true if the study is visible, false otherwise
   */
  public boolean isStudyVisible(MediaSeriesGroup study) {
    if (study == null) {
      return false;
    }
    return getStudyPanes().anyMatch(studyPane -> studyPane.isStudy(study));
  }

  /** Refreshes the layout of the patient pane, reorganizing all study panes. */
  void refreshLayout() {
    updateLayout();

    Optional<MediaSeriesGroup> patient = getCurrentPatient();
    if (patient.isEmpty()) {
      removeAll();
      revalidate();
      return;
    }
    List<StudyPane> studies = explorer.getPaneManager().getStudyList(patient.get());
    removeAll();

    if (studies != null) {
      studies.stream().filter(this::hasVisibleContent).forEach(this::addStudyPane);
    }
    revalidate();
  }

  /** Shows all studies for the current patient. */
  public void showAllStudies() {
    removeAll();

    Optional<MediaSeriesGroup> patient = getCurrentPatient();
    if (patient.isEmpty()) {
      revalidate();
      return;
    }

    var paneManager = explorer.getPaneManager();
    List<StudyPane> studies = paneManager.getStudyList(patient.get());
    if (studies != null) {
      studies.forEach(
          studyPane -> {
            studyPane.showAllSeries(paneManager);
            if (hasVisibleContent(studyPane)) {
              addPane(studyPane);
            }
            studyPane.doLayout();
          });
    }
    revalidate();
  }

  /**
   * Displays the specified study in the patient pane.
   *
   * @param study the study to be displayed in this patient pane; if null, all studies for the
   *     current patient will be shown
   */
  public void showSpecificStudy(MediaSeriesGroup study) {
    removeAll();
    if (study == null) {
      showAllStudies();
    } else {
      var paneManager = explorer.getPaneManager();
      StudyPane studyPane = paneManager.getStudyPane(study);
      if (studyPane != null) {
        studyPane.showAllSeries(paneManager);
        if (hasVisibleContent(studyPane)) {
          addPane(studyPane);
        }
        studyPane.doLayout();
      }
    }
    revalidate();
  }

  /**
   * Adds a study pane to this patient pane.
   *
   * @param studyPane the study pane to add
   */
  public void addPane(StudyPane studyPane) {
    if (studyPane != null) {
      add(studyPane);
      studyPane.refreshLayout();
    }
  }

  /**
   * Checks if the given patient matches the currently selected patient.
   *
   * @param patient the patient to check
   * @return true if the patient matches the selected patient, false otherwise
   */
  public boolean isPatient(MediaSeriesGroup patient) {
    return Objects.equals(currentPatient, patient);
  }

  /**
   * Gets the currently selected patient.
   *
   * @return an Optional containing the current patient, or empty if none selected
   */
  public Optional<MediaSeriesGroup> getCurrentPatient() {
    return Optional.ofNullable(currentPatient);
  }

  /** Updates the layout manager based on the explorer's layout orientation. */
  private void updateLayout() {
    String layoutConstraints =
        explorer.isVerticalLayout() ? VERTICAL_LAYOUT_CONSTRAINTS : HORIZONTAL_LAYOUT_CONSTRAINTS;
    setLayout(new MigLayout(layoutConstraints, COLUMN_CONSTRAINTS));
  }

  private boolean hasVisibleContent(StudyPane studyPane) {
    return studyPane != null && studyPane.getComponentCount() > 0;
  }

  private void addStudyPane(StudyPane studyPane) {
    studyPane.setSize(STUDY_PANE_SIZE);
    addPane(studyPane);
    studyPane.doLayout();
  }

  private Stream<StudyPane> getStudyPanes() {
    return Arrays.stream(getComponents())
        .filter(StudyPane.class::isInstance)
        .map(StudyPane.class::cast);
  }
}
