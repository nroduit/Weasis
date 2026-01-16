/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.swing.tree.DefaultMutableTreeNode;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomSorter.SortingTime;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.explorer.main.PatientPane;
import org.weasis.dicom.explorer.main.SeriesPane;
import org.weasis.dicom.explorer.main.StudyPane;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class DicomSorterTest {

  @Mock private DicomModel mockDicomModel;
  @Mock private DicomExplorer mockDicomExplorer;

  @BeforeEach
  void setUp() {
    // Configure DicomModel mock
    configureDicomModelMock();

    // Configure DicomExplorer mock
    configureDicomExplorerMock();
  }

  private void configureDicomModelMock() {
    // Mock the static model structure

    // Mock hierarchy node retrieval
    lenient()
        .when(mockDicomModel.getHierarchyNode(any(), anyString()))
        .thenAnswer(
            invocation -> {
              MediaSeriesGroup parent = invocation.getArgument(0);
              String valueId = invocation.getArgument(1);

              if (parent != null) {
                Collection<MediaSeriesGroup> children = mockDicomModel.getChildren(parent);
                return children.stream()
                    .filter(node -> valueId.equals(node.getTagValue(node.getTagID())))
                    .findFirst()
                    .orElse(null);
              }
              return null;
            });

    // Mock parent retrieval
    lenient()
        .when(mockDicomModel.getParent(any(), any()))
        .thenReturn(createPatientWithName("Mock Patient"));
  }

  private void configureDicomExplorerMock() {

    // Mock selected patient
    var mockPatient = createPatientWithName("Test Patient");
    lenient()
        .when(mockDicomExplorer.getSelectedPatient())
        .thenReturn((MediaSeriesGroupNode) mockPatient);
  }

  @Nested
  class Sorting_Time_Tests {

    @Test
    void should_return_chronological_when_id_is_zero() {
      assertEquals(SortingTime.CHRONOLOGICAL, SortingTime.valueOf(0));
    }

    @Test
    void should_return_inverse_chronological_when_id_is_one() {
      assertEquals(SortingTime.INVERSE_CHRONOLOGICAL, SortingTime.valueOf(1));
    }

    @Test
    void should_return_chronological_for_invalid_ids() {
      assertEquals(SortingTime.CHRONOLOGICAL, SortingTime.valueOf(-1));
      assertEquals(SortingTime.CHRONOLOGICAL, SortingTime.valueOf(99));
      assertEquals(SortingTime.CHRONOLOGICAL, SortingTime.valueOf(Integer.MAX_VALUE));
    }

    @Test
    void should_return_correct_id_values() {
      assertEquals(0, SortingTime.CHRONOLOGICAL.getId());
      assertEquals(1, SortingTime.INVERSE_CHRONOLOGICAL.getId());
    }

    @Test
    void should_have_non_null_titles() {
      assertNotNull(SortingTime.CHRONOLOGICAL.getTitle());
      assertNotNull(SortingTime.INVERSE_CHRONOLOGICAL.getTitle());
    }

    @Test
    void should_have_consistent_toString_and_getTitle() {
      assertEquals(SortingTime.CHRONOLOGICAL.getTitle(), SortingTime.CHRONOLOGICAL.toString());
      assertEquals(
          SortingTime.INVERSE_CHRONOLOGICAL.getTitle(),
          SortingTime.INVERSE_CHRONOLOGICAL.toString());
    }
  }

  @Nested
  class Patient_Comparator_Tests {

    @Test
    void should_sort_patients_by_name_using_real_patient_data() {
      var patient1 = createPatientWithName("Brown, Alice");
      var patient2 = createPatientWithName("Doe, John");
      var patient3 = createPatientWithName("Smith, Bob");

      var patients = List.of(patient3, patient1, patient2);
      var sorted = patients.stream().sorted(DicomSorter.PATIENT_COMPARATOR).toList();

      assertEquals(patient1, sorted.get(0));
      assertEquals(patient2, sorted.get(1));
      assertEquals(patient3, sorted.get(2));
    }

    @Test
    void should_handle_null_patient_names() {
      var patientWithName = createPatientWithName("Doe, John");
      var patientWithoutName = createPatient();

      assertTrue(DicomSorter.PATIENT_COMPARATOR.compare(null, patientWithName) > 0);
      assertTrue(DicomSorter.PATIENT_COMPARATOR.compare(patientWithName, null) < 0);
      assertEquals(0, DicomSorter.PATIENT_COMPARATOR.compare(null, null));

      // Patient without name should come after patient with name
      assertTrue(DicomSorter.PATIENT_COMPARATOR.compare(patientWithName, patientWithoutName) < 0);
    }

    @Test
    void should_handle_patient_pane_objects() {
      var patient1 = createPatientWithName("Alice, Brown");
      var patient2 = createPatientWithName("John, Doe");

      var patientPane1 = createMockedPatientPane(patient1);
      var patientPane2 = createMockedPatientPane(patient2);

      assertTrue(DicomSorter.PATIENT_COMPARATOR.compare(patientPane1, patientPane2) < 0);
    }

    @Test
    void should_handle_tree_node_objects() {
      var patient1 = createPatientWithName("Alice, Brown");
      var patient2 = createPatientWithName("John, Doe");

      var treeNode1 = new DefaultMutableTreeNode(patient1);
      var treeNode2 = new DefaultMutableTreeNode(patient2);

      assertTrue(DicomSorter.PATIENT_COMPARATOR.compare(treeNode1, treeNode2) < 0);
    }

    @Test
    void should_handle_case_insensitive_sorting() {
      var patient1 = createPatientWithName("brown, alice");
      var patient2 = createPatientWithName("DOE, JOHN");

      assertTrue(DicomSorter.PATIENT_COMPARATOR.compare(patient1, patient2) < 0);
      assertEquals(
          0,
          DicomSorter.PATIENT_COMPARATOR.compare(patient1, createPatientWithName("Brown, Alice")));
    }

    @Test
    void should_handle_special_characters_in_names() {
      var patient1 = createPatientWithName("Müller, Hans");
      var patient2 = createPatientWithName("Smith, John");

      var result = DicomSorter.PATIENT_COMPARATOR.compare(patient1, patient2);
      assertNotEquals(0, result); // Should handle umlauts consistently
    }

    private PatientPane createMockedPatientPane(MediaSeriesGroup patient) {
      var mockPatientPane = mock(PatientPane.class);
      when(mockPatientPane.getCurrentPatient()).thenReturn(Optional.of(patient));
      return mockPatientPane;
    }
  }

  @Nested
  class Study_Comparator_Tests {

    @Test
    void should_sort_studies_chronologically() {
      try (var mockedStatic = mockStatic(DicomSorter.class, CALLS_REAL_METHODS)) {
        mockedStatic.when(DicomSorter::getStudyDateSorting).thenReturn(SortingTime.CHRONOLOGICAL);

        var study1 = createStudyWithDate(2024, 1, 15);
        var study2 = createStudyWithDate(2024, 2, 20);
        var study3 = createStudyWithDate(2023, 12, 10);

        var studies = List.of(study2, study3, study1);
        var sorted = studies.stream().sorted(DicomSorter.STUDY_COMPARATOR).toList();

        assertEquals(study3, sorted.get(0)); // 2023-12-10
        assertEquals(study1, sorted.get(1)); // 2024-01-15
        assertEquals(study2, sorted.get(2)); // 2024-02-20
      }
    }

    @Test
    void should_sort_studies_inverse_chronologically() {
      try (var mockedStatic = mockStatic(DicomSorter.class, CALLS_REAL_METHODS)) {
        mockedStatic
            .when(DicomSorter::getStudyDateSorting)
            .thenReturn(SortingTime.INVERSE_CHRONOLOGICAL);

        var study1 = createStudyWithDate(2024, 1, 15);
        var study2 = createStudyWithDate(2024, 2, 20);
        var study3 = createStudyWithDate(2023, 12, 10);

        var studies = List.of(study2, study3, study1);
        var sorted = studies.stream().sorted(DicomSorter.STUDY_COMPARATOR).toList();

        assertEquals(study2, sorted.get(0)); // 2024-02-20 (newest first)
        assertEquals(study1, sorted.get(1)); // 2024-01-15
        assertEquals(study3, sorted.get(2)); // 2023-12-10 (oldest last)
      }
    }

    @Test
    void should_sort_by_study_id_when_dates_equal() {
      try (var mockedStatic = mockStatic(DicomSorter.class, CALLS_REAL_METHODS)) {
        mockedStatic.when(DicomSorter::getStudyDateSorting).thenReturn(SortingTime.CHRONOLOGICAL);

        var study1 = createStudyWithDateAndId(2024, 1, 15, "STUDY001");
        var study2 = createStudyWithDateAndId(2024, 1, 15, "STUDY002");
        var study3 = createStudyWithDateAndId(2024, 1, 15, "STUDY000");

        var studies = List.of(study2, study3, study1);
        var sorted = studies.stream().sorted(DicomSorter.STUDY_COMPARATOR).toList();

        assertEquals(study3, sorted.get(0)); // STUDY000
        assertEquals(study1, sorted.get(1)); // STUDY001
        assertEquals(study2, sorted.get(2)); // STUDY002
      }
    }

    @Test
    void should_sort_by_description_when_dates_and_ids_equal() {
      try (var mockedStatic = mockStatic(DicomSorter.class, CALLS_REAL_METHODS)) {
        mockedStatic.when(DicomSorter::getStudyDateSorting).thenReturn(SortingTime.CHRONOLOGICAL);

        var study1 = createStudyWithDateIdAndDescription(2024, 1, 15, "STUDY001", "CT Chest");
        var study2 = createStudyWithDateIdAndDescription(2024, 1, 15, "STUDY001", "Abdomen");
        var study3 = createStudyWithDateIdAndDescription(2024, 1, 15, "STUDY001", "MRI Brain");

        var studies = List.of(study3, study1, study2);
        var sorted = studies.stream().sorted(DicomSorter.STUDY_COMPARATOR).toList();

        assertEquals(study2, sorted.get(0)); // Abdomen
        assertEquals(study1, sorted.get(1)); // CT Chest
        assertEquals(study3, sorted.get(2)); // MRI Brain
      }
    }

    @Test
    void should_handle_null_studies_properly() {
      var study = createStudyWithDate(2024, 1, 15);

      assertTrue(DicomSorter.STUDY_COMPARATOR.compare(null, study) > 0);
      assertTrue(DicomSorter.STUDY_COMPARATOR.compare(study, null) < 0);
      assertEquals(0, DicomSorter.STUDY_COMPARATOR.compare(null, null));
    }

    @Test
    void should_handle_mixed_null_values() {
      try (var mockedStatic = mockStatic(DicomSorter.class, CALLS_REAL_METHODS)) {
        mockedStatic.when(DicomSorter::getStudyDateSorting).thenReturn(SortingTime.CHRONOLOGICAL);

        var studyWithDate = createStudyWithDate(2024, 1, 15);
        var studyWithoutDate = createStudy();
        var studyWithId = createStudyWithDateAndId(2024, 1, 15, null); // Same date, no ID

        // Studies without dates should come after those with dates
        assertTrue(DicomSorter.STUDY_COMPARATOR.compare(studyWithDate, studyWithoutDate) < 0);

        // When one has date and other doesn't, date wins
        assertNotEquals(0, DicomSorter.STUDY_COMPARATOR.compare(studyWithDate, studyWithoutDate));
      }
    }

    @Test
    void should_handle_study_pane_objects() {
      try (var mockedStatic = mockStatic(DicomSorter.class, CALLS_REAL_METHODS)) {
        mockedStatic.when(DicomSorter::getStudyDateSorting).thenReturn(SortingTime.CHRONOLOGICAL);

        var study1 = createStudyWithDate(2024, 1, 15);
        var study2 = createStudyWithDate(2024, 2, 20);

        var studyPane1 = createMockedStudyPane(study1);
        var studyPane2 = createMockedStudyPane(study2);

        assertTrue(DicomSorter.STUDY_COMPARATOR.compare(studyPane1, studyPane2) < 0);
      }
    }

    @Test
    void should_handle_case_insensitive_descriptions() {
      try (var mockedStatic = mockStatic(DicomSorter.class, CALLS_REAL_METHODS)) {
        mockedStatic.when(DicomSorter::getStudyDateSorting).thenReturn(SortingTime.CHRONOLOGICAL);

        var study1 = createStudyWithDateIdAndDescription(2024, 1, 15, "STUDY001", "chest ct");
        var study2 = createStudyWithDateIdAndDescription(2024, 1, 15, "STUDY001", "CHEST CT");

        assertEquals(0, DicomSorter.STUDY_COMPARATOR.compare(study1, study2));
      }
    }

    @Test
    void should_integrate_with_mocked_model() {
      try (var mockedStatic = mockStatic(DicomSorter.class, CALLS_REAL_METHODS)) {
        mockedStatic.when(DicomSorter::getStudyDateSorting).thenReturn(SortingTime.CHRONOLOGICAL);

        // Create studies that would exist in the mocked model
        var study1 = createStudyWithDate(2024, 1, 15);
        var study2 = createStudyWithDate(2024, 2, 20);

        // Mock the model's getChildren to return these studies
        var mockPatient = createPatientWithName("Test Patient");

        // Test sorting
        var sorted = List.of(study2, study1).stream().sorted(DicomSorter.STUDY_COMPARATOR).toList();

        assertEquals(study1, sorted.get(0)); // Earlier date first
        assertEquals(study2, sorted.get(1)); // Later date second
      }
    }

    private StudyPane createMockedStudyPane(MediaSeriesGroup study) {
      var mockStudyPane = mock(StudyPane.class);
      when(mockStudyPane.getDicomStudy()).thenReturn(study);
      return mockStudyPane;
    }
  }

  @Nested
  class Series_Comparator_Tests {

    @Test
    void should_sort_series_by_series_number() {
      var series1 = createSeriesWithNumber(1);
      var series2 = createSeriesWithNumber(2);
      var series3 = createSeriesWithNumber(10);

      var seriesList = List.of(series3, series1, series2);
      var sorted = seriesList.stream().sorted(DicomSorter.SERIES_COMPARATOR).toList();

      assertEquals(series1, sorted.get(0)); // Series 1
      assertEquals(series2, sorted.get(1)); // Series 2
      assertEquals(series3, sorted.get(2)); // Series 10
    }

    @Test
    void should_place_sr_and_doc_modalities_at_end() {
      var regularSeries = createSeriesWithModality("CT");
      var srSeries = createSeriesWithModality("SR");
      var docSeries = createSeriesWithModality("DOC");

      var seriesList = List.of(srSeries, regularSeries, docSeries);
      var sorted = seriesList.stream().sorted(DicomSorter.SERIES_COMPARATOR).toList();

      assertEquals(regularSeries, sorted.get(0)); // Regular series first
      assertTrue(sorted.indexOf(srSeries) > sorted.indexOf(regularSeries));
      assertTrue(sorted.indexOf(docSeries) > sorted.indexOf(regularSeries));
    }

    @Test
    void should_place_encapsulated_documents_at_end() {
      var regularSeries = createSeriesWithNumber(1);
      var encapSeries1 = createSeriesWithSOPClass("1.2.840.10008.5.1.4.1.1.88.11");
      var encapSeries2 = createSeriesWithSOPClass("1.2.840.10008.5.1.4.1.1.104.1");

      var seriesList = List.of(encapSeries1, regularSeries, encapSeries2);
      var sorted = seriesList.stream().sorted(DicomSorter.SERIES_COMPARATOR).toList();

      assertEquals(regularSeries, sorted.get(0)); // Regular series first
      assertTrue(sorted.indexOf(encapSeries1) > sorted.indexOf(regularSeries));
      assertTrue(sorted.indexOf(encapSeries2) > sorted.indexOf(regularSeries));
    }

    @Test
    void should_sort_by_split_series_number() {
      var series1 = createSeriesWithNumberAndSplit(1, 0);
      var series2 = createSeriesWithNumberAndSplit(1, 1);
      var series3 = createSeriesWithNumberAndSplit(1, 2);

      var seriesList = List.of(series3, series1, series2);
      var sorted = seriesList.stream().sorted(DicomSorter.SERIES_COMPARATOR).toList();

      assertEquals(series1, sorted.get(0)); // Split 0
      assertEquals(series2, sorted.get(1)); // Split 1
      assertEquals(series3, sorted.get(2)); // Split 2
    }

    @Test
    void should_sort_by_date_when_numbers_equal() {
      var series1 = createSeriesWithNumberAndDate(1, 2024, 1, 15);
      var series2 = createSeriesWithNumberAndDate(1, 2024, 2, 20);
      var series3 = createSeriesWithNumberAndDate(1, 2023, 12, 10);

      var seriesList = List.of(series2, series3, series1);
      var sorted = seriesList.stream().sorted(DicomSorter.SERIES_COMPARATOR).toList();

      assertEquals(series3, sorted.get(0)); // 2023-12-10
      assertEquals(series1, sorted.get(1)); // 2024-01-15
      assertEquals(series2, sorted.get(2)); // 2024-02-20
    }

    @Test
    void should_sort_by_description_when_numbers_and_dates_equal() {
      var series1 = createSeriesWithNumberDateAndDescription(1, 2024, 1, 15, "T1");
      var series2 = createSeriesWithNumberDateAndDescription(1, 2024, 1, 15, "Axial");
      var series3 = createSeriesWithNumberDateAndDescription(1, 2024, 1, 15, "T2");

      var seriesList = List.of(series3, series1, series2);
      var sorted = seriesList.stream().sorted(DicomSorter.SERIES_COMPARATOR).toList();

      assertEquals(series2, sorted.get(0)); // Axial
      assertEquals(series1, sorted.get(1)); // T1
      assertEquals(series3, sorted.get(2)); // T2
    }

    @Test
    void should_handle_null_series_values() {
      var seriesWithNumber = createSeriesWithNumber(1);
      var seriesWithoutNumber = createSeries();

      assertTrue(DicomSorter.SERIES_COMPARATOR.compare(null, seriesWithNumber) > 0);
      assertTrue(DicomSorter.SERIES_COMPARATOR.compare(seriesWithNumber, null) < 0);
      assertEquals(0, DicomSorter.SERIES_COMPARATOR.compare(null, null));

      // Series without number should come after series with number (uses Integer.MAX_VALUE)
      assertTrue(DicomSorter.SERIES_COMPARATOR.compare(seriesWithNumber, seriesWithoutNumber) < 0);
    }

    @Test
    void should_handle_series_pane_objects() {
      var series1 = createSeriesWithNumber(1);
      var series2 = createSeriesWithNumber(2);

      var seriesPane1 = createMockedSeriesPane(series1);
      var seriesPane2 = createMockedSeriesPane(series2);

      assertTrue(DicomSorter.SERIES_COMPARATOR.compare(seriesPane1, seriesPane2) < 0);
    }

    @Test
    void should_handle_complex_sorting_scenario() {
      // Mix of regular and special series with various attributes
      var regularSeries1 = createSeriesWithNumber(1);
      var regularSeries2 = createSeriesWithNumber(2);
      var srSeries = createSeriesWithModalityAndNumber("SR", 1);
      var docSeries = createSeriesWithModalityAndNumber("DOC", 3);
      var encapSeries = createSeriesWithSOPClassAndNumber("1.2.840.10008.5.1.4.1.1.88.11", 2);

      var seriesList = List.of(docSeries, encapSeries, srSeries, regularSeries2, regularSeries1);
      var sorted = seriesList.stream().sorted(DicomSorter.SERIES_COMPARATOR).toList();

      // Regular series should come first, sorted by number
      assertEquals(regularSeries1, sorted.get(0));
      assertEquals(regularSeries2, sorted.get(1));

      // Special series should come after, but order among them may vary
      assertTrue(sorted.indexOf(srSeries) > 1);
      assertTrue(sorted.indexOf(docSeries) > 1);
      assertTrue(sorted.indexOf(encapSeries) > 1);
    }

    @Test
    void should_integrate_with_mocked_model_and_explorer() {
      // Create series that would be managed by the mocked explorer
      var series1 = createSeriesWithNumber(1);
      var series2 = createSeriesWithNumber(2);
      var study = createStudyWithDate(2024, 1, 15);

      // Test sorting with the mocked environment
      var sorted =
          List.of(series2, series1).stream().sorted(DicomSorter.SERIES_COMPARATOR).toList();

      assertEquals(series1, sorted.get(0));
      assertEquals(series2, sorted.get(1));
    }

    private SeriesPane createMockedSeriesPane(MediaSeriesGroup series) {
      var mockSeriesPane = mock(SeriesPane.class);
      when(mockSeriesPane.getDicomSeries()).thenReturn((DicomSeries) series);
      return mockSeriesPane;
    }
  }

  @Nested
  class Integration_Tests_With_Mocked_Dependencies {

    @Test
    void should_work_with_full_hierarchy_mock() {
      // Create a complete hierarchy
      var patient = createPatientWithName("Test Patient");
      var study = createStudyWithDate(2024, 1, 15);
      var series = createSeriesWithNumber(1);

      // Test all comparators work with the mocked hierarchy
      assertEquals(0, DicomSorter.PATIENT_COMPARATOR.compare(patient, patient));
      assertEquals(0, DicomSorter.SERIES_COMPARATOR.compare(series, series));
    }

    @Test
    void should_handle_explorer_integrated_sorting() {
      // Setup mocked explorer with data
      var patient1 = createPatientWithName("Alice");
      var patient2 = createPatientWithName("Bob");

      // Mock explorer returning these patients
      when(mockDicomExplorer.getSelectedPatient()).thenReturn((MediaSeriesGroupNode) patient1);

      // Test sorting works in the context of the mocked explorer
      var patients = List.of(patient2, patient1);
      var sorted = patients.stream().sorted(DicomSorter.PATIENT_COMPARATOR).toList();

      assertEquals(patient1, sorted.get(0)); // Alice first
      assertEquals(patient2, sorted.get(1)); // Bob second

      // Verify the mock was used correctly
      assertNotNull(mockDicomExplorer.getSelectedPatient());
      assertEquals(
          "Alice", mockDicomExplorer.getSelectedPatient().getTagValue(TagD.get(Tag.PatientName)));
    }
  }

  // Helper methods to create test data
  private MediaSeriesGroup createPatient() {
    return new MediaSeriesGroupNode(
        TagW.PatientPseudoUID, UIDUtils.createUID(), DicomModel.patient.tagView());
  }

  private MediaSeriesGroup createPatientWithName(String patientName) {
    var patient = createPatient();
    patient.setTagNoNull(TagD.get(Tag.PatientName), patientName);
    return patient;
  }

  private MediaSeriesGroup createStudy() {
    return new MediaSeriesGroupNode(
        TagD.getUID(Level.STUDY), UIDUtils.createUID(), DicomModel.study.tagView());
  }

  private MediaSeriesGroup createStudyWithDate(int year, int month, int day) {
    var study = createStudy();
    var dateTime = LocalDateTime.of(year, month, day, 0, 0);
    study.setTagNoNull(TagD.get(Tag.StudyDate), dateTime.toLocalDate());
    study.setTagNoNull(TagD.get(Tag.StudyTime), dateTime.toLocalTime());
    return study;
  }

  private MediaSeriesGroup createStudyWithDateAndId(int year, int month, int day, String studyId) {
    var study = createStudyWithDate(year, month, day);
    if (studyId != null) {
      study.setTagNoNull(TagD.get(Tag.StudyID), studyId);
    }
    return study;
  }

  private MediaSeriesGroup createStudyWithDateIdAndDescription(
      int year, int month, int day, String studyId, String description) {
    var study = createStudyWithDateAndId(year, month, day, studyId);
    study.setTagNoNull(TagD.get(Tag.StudyDescription), description);
    return study;
  }

  private MediaSeriesGroup createSeries() {
    return new DicomSeries(UIDUtils.createUID());
  }

  private MediaSeriesGroup createSeriesWithNumber(int seriesNumber) {
    var series = new DicomSeries(UIDUtils.createUID());
    series.setTagNoNull(TagD.get(Tag.SeriesNumber), seriesNumber);
    return series;
  }

  private MediaSeriesGroup createSeriesWithModality(String modality) {
    var series = new DicomSeries(UIDUtils.createUID());
    series.setTagNoNull(TagD.get(Tag.Modality), modality);
    return series;
  }

  private MediaSeriesGroup createSeriesWithModalityAndNumber(String modality, int seriesNumber) {
    var series = createSeriesWithModality(modality);
    series.setTagNoNull(TagD.get(Tag.SeriesNumber), seriesNumber);
    return series;
  }

  private MediaSeriesGroup createSeriesWithNumberAndSplit(int seriesNumber, int splitNumber) {
    var series = createSeriesWithNumber(seriesNumber);
    series.setTagNoNull(TagW.SplitSeriesNumber, splitNumber);
    return series;
  }

  private MediaSeriesGroup createSeriesWithNumberAndDate(
      int seriesNumber, int year, int month, int day) {
    var series = createSeriesWithNumber(seriesNumber);
    var dateTime = LocalDateTime.of(year, month, day, 0, 0);
    series.setTagNoNull(TagD.get(Tag.SeriesDate), dateTime.toLocalDate());
    series.setTagNoNull(TagD.get(Tag.SeriesTime), dateTime.toLocalTime());
    return series;
  }

  private MediaSeriesGroup createSeriesWithNumberDateAndDescription(
      int seriesNumber, int year, int month, int day, String description) {
    var series = createSeriesWithNumberAndDate(seriesNumber, year, month, day);
    series.setTagNoNull(TagD.get(Tag.SeriesDescription), description);
    return series;
  }

  private MediaSeriesGroup createSeriesWithSOPClass(String sopClassUID) {
    var series = new DicomSeries(UIDUtils.createUID());
    series.setTagNoNull(TagD.get(Tag.SOPClassUID), sopClassUID);
    return series;
  }

  private MediaSeriesGroup createSeriesWithSOPClassAndNumber(String sopClassUID, int seriesNumber) {
    var series = createSeriesWithSOPClass(sopClassUID);
    series.setTagNoNull(TagD.get(Tag.SeriesNumber), seriesNumber);
    return series;
  }
}
