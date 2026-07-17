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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.swing.SwingUtilities;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.*;

public class DicomPaneManager {

  private final HashMap<MediaSeriesGroup, List<StudyPane>> patient2study = new HashMap<>();
  private final HashMap<MediaSeriesGroup, List<SeriesPane>> study2series = new HashMap<>();

  private final DicomExplorer explorer;

  public DicomPaneManager(DicomExplorer explorer) {
    this.explorer = explorer;
  }

  /** Returns the shared session filter applied to the thumbnail grid. */
  public SeriesFilter getSeriesFilter() {
    return explorer.getSeriesFilter();
  }

  // ========== Patient Pane Management ==========

  /**
   * Removes the patient pane and all associated study panes.
   *
   * @param patient the patient to remove
   */
  public void removePatientPane(MediaSeriesGroup patient) {
    if (patient != null) {
      List<StudyPane> studies = patient2study.remove(patient);
      if (studies != null) {
        for (StudyPane studyPane : studies) {
          study2series.remove(studyPane.getDicomStudy());
        }
      }
      explorer.updateRemovedPatient(patient);
    }
  }

  // ========== Study Pane Management ==========

  /**
   * Retrieves the StudyPane for the given study.
   *
   * @param study the study to find
   * @return the StudyPane if found, otherwise null
   */
  public StudyPane getStudyPane(MediaSeriesGroup study) {
    MediaSeriesGroup patient = getPatientForStudy(study);
    if (patient != null) {
      List<StudyPane> studyList = getStudyList(patient);
      if (studyList != null) {
        return studyList.stream().filter(pane -> pane.isStudy(study)).findFirst().orElse(null);
      }
    }
    return null;
  }

  /**
   * Creates a new StudyPane instance for the given study. If a StudyPane already exists, it returns
   * that instance.
   *
   * @param study the study to create a pane for
   * @param position an array to hold the position of the new pane in the list, can be null
   * @return the created or existing StudyPane instance
   */
  public StudyPane createStudyPaneInstance(MediaSeriesGroup study, int[] position) {
    StudyPane studyPane = getStudyPane(study);
    if (studyPane == null) {
      studyPane = new StudyPane(study);
      MediaSeriesGroup patient = getPatientForStudy(study);
      if (patient != null) {
        List<StudyPane> studies = getStudyList(patient);
        if (studies != Collections.EMPTY_LIST) {
          int index = Collections.binarySearch(studies, studyPane, DicomSorter.STUDY_COMPARATOR);
          if (index < 0) {
            index = -(index + 1);
          }
          if (position != null) {
            position[0] = index;
          }
          studies.add(index, studyPane);
        }
      }
    } else if (position != null) {
      position[0] = -1;
    }
    return studyPane;
  }

  /**
   * Retrieves the list of StudyPanes for the given patient.
   *
   * @param patient the patient to find studies for
   * @return a list of StudyPanes associated with the patient, or an empty list if none found
   */
  public List<StudyPane> getStudyList(MediaSeriesGroup patient) {
    if (patient == null) {
      return Collections.emptyList();
    }
    return patient2study.computeIfAbsent(patient, _ -> new ArrayList<>());
  }

  /**
   * Removes the StudyPane associated with the given study. If the study has no remaining series, it
   * also removes the patient if it has no studies left.
   *
   * @param study the study to remove
   */
  public void removeStudyPane(MediaSeriesGroup study) {
    DicomModel model = explorer.getDataExplorerModel();
    MediaSeriesGroup patient = getPatientForStudy(study);
    List<StudyPane> studies = getStudyList(patient);
    for (int i = studies.size() - 1; i >= 0; i--) {
      StudyPane studyPane = studies.get(i);
      if (studyPane.isStudy(study)) {
        studies.remove(i);
        if (studies.isEmpty()) {
          patient2study.remove(patient);
          // throw a new event for removing the patient
          model.removePatient(patient);
          break;
        }
        study2series.remove(study);
        explorer.updateRemovedStudy(studyPane, study);
        return;
      }
    }

    study2series.remove(study);
  }

  // ========== Series Pane Management ==========

  /**
   * Retrieves the SeriesPane for the given series.
   *
   * @param series the series to find
   * @return the SeriesPane if found, otherwise null
   */
  public SeriesPane getSeriesPane(MediaSeriesGroup series) {
    MediaSeriesGroup study = getStudyForSeries(series);
    if (study == null) {
      return null;
    }

    List<SeriesPane> seriesList = study2series.get(study);
    if (seriesList != null) {
      return seriesList.stream().filter(pane -> pane.isSeries(series)).findFirst().orElse(null);
    }
    return null;
  }

  /**
   * Creates a new SeriesPane instance for the given DicomSeries. If a SeriesPane already exists, it
   * returns that instance.
   *
   * @param series the DicomSeries to create a pane for
   * @param position an array to hold the position of the new pane in the list, can be null
   * @return the created or existing SeriesPane instance
   */
  public synchronized SeriesPane createSeriesPaneInstance(DicomSeries series, int[] position) {
    SeriesPane seriesPane = getSeriesPane(series);
    if (seriesPane == null) {
      DicomModel model = explorer.getDataExplorerModel();
      seriesPane = new SeriesPane(series, model);
      List<SeriesPane> seriesList = getSeriesList(getStudyForSeries(series));
      if (seriesList != Collections.EMPTY_LIST) {
        int index = Collections.binarySearch(seriesList, seriesPane, DicomSorter.SERIES_COMPARATOR);
        if (index < 0) {
          index = -(index + 1);
        }
        if (position != null) {
          position[0] = index;
        }
        seriesList.add(index, seriesPane);
      }
    } else if (position != null) {
      position[0] = -1;
    }
    return seriesPane;
  }

  /**
   * Checks if the given series is contained within the specified patient pane.
   *
   * @param patientPane the patient pane to check
   * @param series the series to look for
   * @return true if the series is found in the patient pane, false otherwise
   */
  public boolean containsSeriesInPatient(PatientPane patientPane, MediaSeriesGroup series) {
    MediaSeriesGroup study = getStudyForSeries(series);
    MediaSeriesGroup patient = getPatientForStudy(study);
    if (patient == null
        || study == null
        || patientPane == null
        || !patientPane.isPatient(patient)) {
      return false;
    }
    List<SeriesPane> seriesList = getSeriesList(study);
    return !seriesList.isEmpty() && seriesList.stream().anyMatch(pane -> pane.isSeries(series));
  }

  /**
   * Retrieves the list of SeriesPanes for the given study.
   *
   * @param study the study to find series for
   * @return a list of SeriesPanes associated with the study, or an empty list if none found
   */
  public List<SeriesPane> getSeriesList(MediaSeriesGroup study) {
    if (study == null) {
      return Collections.emptyList();
    }
    return study2series.computeIfAbsent(study, _ -> new ArrayList<>());
  }

  /**
   * Removes the SeriesPane associated with the given series. If the study has no remaining series,
   * it also removes the study if it has no studies left.
   *
   * @param series the series to remove
   */
  public void removeSeriesPane(MediaSeriesGroup series) {
    MediaSeriesGroup study = getStudyForSeries(series);
    if (study == null) {
      return;
    }
    List<SeriesPane> seriesList = getSeriesList(study);
    if (!seriesList.isEmpty()) {
      DicomModel model = explorer.getDataExplorerModel();
      for (int j = seriesList.size() - 1; j >= 0; j--) {
        SeriesPane se = seriesList.get(j);
        if (se.isSeries(series)) {
          seriesList.remove(j);
          if (seriesList.isEmpty()) {
            study2series.remove(study);
            // throw a new event for removing the patient
            model.removeStudy(study);
            break;
          }
          se.removeAll();

          StudyPane studyPane = getStudyPane(study);
          if (studyPane != null && studyPane.isSeriesVisible(series)) {
            studyPane.remove(se);
            studyPane.revalidate();
            studyPane.repaint();
          }
          break;
        }
      }
    }
  }

  // ========== Pane Refresh and Layout ==========

  public void refreshAllPanes() {
    SwingUtilities.invokeLater(
        () -> {
          patient2study.values().stream().flatMap(List::stream).forEach(StudyPane::refreshLayout);
        });
  }

  public void refreshStudyPane(MediaSeriesGroup study) {
    StudyPane studyPane = getStudyPane(study);
    if (studyPane != null) {
      SwingUtilities.invokeLater(studyPane::refreshLayout);
    }
  }

  public void refreshSeriesPane(MediaSeriesGroup series) {
    SeriesPane seriesPane = getSeriesPane(series);
    if (seriesPane != null) {
      SwingUtilities.invokeLater(seriesPane::updateText);
    }
  }

  // ========== Utility Methods ==========

  /**
   * Retrieves the patient associated with the given study.
   *
   * @param study the study to find the patient for
   * @return the MediaSeriesGroup representing the patient, or null if not found
   */
  public MediaSeriesGroup getPatientForStudy(MediaSeriesGroup study) {
    return explorer.getDataExplorerModel().getParent(study, DicomModel.patient);
  }

  /**
   * Retrieves the study associated with the given series.
   *
   * @param series the series to find the study for
   * @return the MediaSeriesGroup representing the study, or null if not found
   */
  public MediaSeriesGroup getStudyForSeries(MediaSeriesGroup series) {
    return explorer.getDataExplorerModel().getParent(series, DicomModel.study);
  }

  private int getStudyPaneIndex(MediaSeriesGroup patient, StudyPane studyPane) {
    List<StudyPane> studyList = patient2study.get(patient);
    return studyList != null ? studyList.indexOf(studyPane) : -1;
  }

  private int getSeriesPaneIndex(MediaSeriesGroup study, SeriesPane seriesPane) {
    List<SeriesPane> seriesList = study2series.get(study);
    return seriesList != null ? seriesList.indexOf(seriesPane) : -1;
  }

  // ========== Information ==========

  /**
   * Checks if the given study has any visible series.
   *
   * @param study the study to check
   * @return true if the study has visible series, false otherwise
   */
  public boolean hasVisibleSeries(MediaSeriesGroup study) {
    List<SeriesPane> seriesList = study2series.get(study);
    return seriesList != null && !seriesList.isEmpty();
  }

  /**
   * Checks if the given patient has any visible studies.
   *
   * @param patient the patient to check
   * @return true if the patient has visible studies, false otherwise
   */
  public boolean hasVisibleStudies(MediaSeriesGroup patient) {
    List<StudyPane> studyList = patient2study.get(patient);
    return studyList != null && !studyList.isEmpty();
  }

  // ========== Cleanup ==========

  public void dispose() {
    patient2study.clear();
    study2series.clear();
  }
}
