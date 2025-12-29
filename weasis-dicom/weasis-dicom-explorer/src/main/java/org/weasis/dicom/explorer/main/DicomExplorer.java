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

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import bibliothek.gui.dock.control.focus.DefaultFocusRequest;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.*;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SequenceHandler;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.ArrayListComboBoxModel;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.dicom.codec.*;
import org.weasis.dicom.explorer.*;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.exp.ExplorerTask;
import org.weasis.dicom.explorer.exp.ExportToolBar;
import org.weasis.dicom.explorer.imp.ImportToolBar;
import org.weasis.dicom.explorer.imp.LocalImport;

public class DicomExplorer extends PluginTool
    implements DataExplorerView, SeriesViewerListener, PropertyChangeListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomExplorer.class);

  public static final String NAME =
      org.weasis.dicom.explorer.Messages.getString("DicomExplorer.title");
  public static final String BUTTON_NAME =
      org.weasis.dicom.explorer.Messages.getString("DicomExplorer.btn_title");
  public static final String DESCRIPTION =
      org.weasis.dicom.explorer.Messages.getString("DicomExplorer.desc");
  public static final String ALL_PATIENTS =
      org.weasis.dicom.explorer.Messages.getString("DicomExplorer.sel_all_pat");
  public static final String ALL_STUDIES =
      org.weasis.dicom.explorer.Messages.getString("DicomExplorer.sel_all_st");

  // Position enumeration for navigation
  public enum ListPosition {
    FIRST,
    PREVIOUS,
    NEXT,
    LAST
  }

  private final DicomModel model;
  private final DicomPaneManager paneManager;
  private final SplitSeriesManager splitSeriesManager;
  private final DicomTaskManager taskManager;

  private final PatientPane selectedPatient;
  private final JScrollPane thumbnailView;
  private final LoadingPanel loadingPanel;
  private final SeriesSelectionModel selectionList;
  private final JButton koOpen;
  private JPanel panelMain;

  private final ArrayListComboBoxModel<Object> modelPatient;
  private final ArrayListComboBoxModel<Object> modelStudy;
  private final JComboBox<Object> patientCombobox;
  private final JComboBox<Object> studyCombobox;

  private final ItemListener studyItemListener;
  private final ItemListener patientItemListener;

  private final PatientSelectionManager patientSelectionManager;

  private final boolean verticalLayout = true;

  // ========== Constructors ==========

  public DicomExplorer() {
    this(null);
  }

  public DicomExplorer(DicomModel model) {
    super(NAME, POSITION.WEST, ExtendedMode.NORMALIZED, Insertable.Type.EXPLORER, 20);

    // Initialize core model and managers
    this.model = model != null ? model : new DicomModel();
    this.paneManager = new DicomPaneManager(this);
    this.splitSeriesManager = new SplitSeriesManager(this);
    this.taskManager = new DicomTaskManager(this);

    // Initialize UI components
    this.selectedPatient = new PatientPane(this);
    this.thumbnailView = new JScrollPane();
    this.loadingPanel = new LoadingPanel();
    this.selectionList = new SeriesSelectionModel(this);

    // Initialize combo box models
    this.modelPatient = new ArrayListComboBoxModel<>(DicomSorter.PATIENT_COMPARATOR);
    this.modelStudy = new ArrayListComboBoxModel<>(DicomSorter.STUDY_COMPARATOR);
    this.patientCombobox = new JComboBox<>(modelPatient);
    this.studyCombobox = new JComboBox<>(modelStudy);

    // Initialize KO button
    this.koOpen =
        new JButton(
            Messages.getString("DicomExplorer.open_ko"),
            ResourceUtil.getIcon(ResourceUtil.OtherIcon.KEY_IMAGE));

    // Create event listeners
    this.patientItemListener = createPatientItemListener();
    this.studyItemListener = createStudyItemListener();

    // Initialize patient selection manager
    this.patientSelectionManager = new PatientSelectionManager(this);
    this.patientSelectionManager.addPatientSelectionListener(selectedPatient);

    // Configure dockable
    dockable.setMaximizable(true);
    setupInitialSize();

    // Setup UI
    setupComboBoxes();
    setupThumbnailView();
    this.model.addPropertyChangeListener(this);
  }

  @Override
  public String getDescription() {
    return DESCRIPTION;
  }

  @Override
  public String getUIName() {
    return NAME;
  }

  @Override
  public String toString() {
    return NAME;
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    selectedPatient.refreshLayout();
  }

  /** Performs a simple layout refresh without recreating components. */
  private void rebuildLayout() {
    removeAll();
    buildLayout();
    selectedPatient.refreshLayout();
  }

  /** Applies the appropriate layout based on current settings. */
  private void buildLayout() {
    if (verticalLayout) {
      setLayout(new MigLayout("fillx, ins 0", "[grow,fill]", "[]rel[grow,fill]unrel[]"));
      add(getMainPanel(), "");
      add(thumbnailView, "newline, top");
      add(loadingPanel, "newline,");
    } else {
      setLayout(new MigLayout("fillx, ins 0", "[right]rel[grow,fill]"));
      add(GuiUtils.getVerticalBoxLayoutPanel(getMainPanel(), loadingPanel));
      add(thumbnailView);
    }
  }

  /** Gets the patient selection manager. */
  public PatientSelectionManager getPatientSelectionManager() {
    return patientSelectionManager;
  }

  /** Updates patient combo box to match the current selection. */
  private void updatePatientComboBoxSelection(MediaSeriesGroup patient) {
    if (patient == null
        || modelPatient.getSelectedItem() == patient
        || modelPatient.getIndexOf(patient) < 0) {
      return;
    }
    patientCombobox.removeItemListener(patientItemListener);
    try {
      patientCombobox.setSelectedItem(patient);
    } finally {
      patientCombobox.addItemListener(patientItemListener);
    }
  }

  public boolean isVerticalLayout() {
    return verticalLayout;
  }

  protected JPanel getMainPanel() {
    if (panelMain == null) {
      MigLayout layout =
          new MigLayout("fillx, ins 3", "[right]rel[grow,fill]", "[]10lp[]"); // NON-NLS
      panelMain = new JPanel(layout);

      final JLabel label = new JLabel(ResourceUtil.getIcon(ResourceUtil.OtherIcon.PATIENT, 24, 24));
      panelMain.add(label, GuiUtils.NEWLINE);
      label.setLabelFor(patientCombobox);
      panelMain.add(patientCombobox, "width 30lp:min:250lp"); // NON-NLS

      final JLabel labelStudy =
          new JLabel(ResourceUtil.getIcon(ResourceUtil.OtherIcon.CALENDAR, 24, 24));
      labelStudy.setLabelFor(studyCombobox);
      panelMain.add(labelStudy, GuiUtils.NEWLINE);
      panelMain.add(studyCombobox, "width 30lp:min:250lp"); // NON-NLS

      koOpen.setToolTipText(koOpen.getText());
      panelMain.add(
          koOpen, "newline, spanx, alignx left, width 30lp:pref:pref, hidemode 2"); // NON-NLS
      koOpen.addActionListener(
          e -> {
            final MediaSeriesGroup patient = getSelectedPatient();
            if (patient != null && e.getSource() instanceof JButton button) {
              List<KOSpecialElement> list =
                  HiddenSeriesManager.getHiddenElementsFromPatient(KOSpecialElement.class, patient);
              if (!list.isEmpty()) {
                if (list.size() == 1) {
                  model.openRelatedSeries(list.getFirst(), patient);
                } else {
                  list.sort(DicomSpecialElement.ORDER_BY_DATE);
                  JPopupMenu popupMenu = new JPopupMenu();
                  popupMenu.add(new TitleMenuItem(ActionW.KO_SELECTION.getTitle()));
                  popupMenu.addSeparator();

                  ButtonGroup group = new ButtonGroup();
                  for (final KOSpecialElement koSpecialElement : list) {
                    final JMenuItem item = new JMenuItem(koSpecialElement.getShortLabel());
                    item.addActionListener(_ -> model.openRelatedSeries(koSpecialElement, patient));
                    popupMenu.add(item);
                    group.add(item);
                  }
                  popupMenu.show(button, 0, button.getHeight());
                }
              }
            }
          });
      koOpen.setVisible(false);
    }
    return panelMain;
  }

  // ========== Initialization Methods ==========

  private void setupInitialSize() {
    int thumbnailSize = SeriesThumbnail.getThumbnailSizeFromPreferences();
    setDockableWidth(Math.max(thumbnailSize, Thumbnail.DEFAULT_SIZE) + 42);
  }

  private void setupComboBoxes() {
    // Patient combo box
    patientCombobox.setMaximumRowCount(15);
    patientCombobox.addItemListener(patientItemListener);

    // Study combo box
    studyCombobox.setMaximumRowCount(15);
    modelStudy.insertElementAt(ALL_STUDIES, 0);
    modelStudy.setSelectedItem(ALL_STUDIES);
    studyCombobox.addItemListener(studyItemListener);
  }

  private void setupThumbnailView() {
    thumbnailView.setBorder(BorderFactory.createEmptyBorder());
    thumbnailView.getVerticalScrollBar().setUnitIncrement(16);
    thumbnailView.setViewportView(selectedPatient);
    thumbnailView.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    thumbnailView.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    rebuildLayout();
    setTransferHandler(new SeriesHandler());
  }

  private static class SeriesHandler extends SequenceHandler {
    public SeriesHandler() {
      super(false, true);
    }

    protected boolean dropFiles(List<Path> files) {
      return DicomSeriesHandler.dropDicomFiles(files);
    }
  }

  // ========== Event Listener Creation ==========

  private ItemListener createPatientItemListener() {
    return e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        selectPatient((MediaSeriesGroup) e.getItem());
      }
    };
  }

  private ItemListener createStudyItemListener() {
    return e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        selectStudy();
      }
    };
  }

  // ========== Property Change Handling ==========

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt instanceof ObservableEvent event) {
      handleObservableEvent(event);
    }
  }

  private void handleObservableEvent(ObservableEvent event) {
    ObservableEvent.BasicAction action = event.getActionCommand();
    Object newVal = event.getNewValue();

    switch (action) {
      case SELECT -> handleSelectEvent(newVal);
      case ADD -> handleAddEvent(newVal);
      case REMOVE -> handleRemoveEvent(newVal);
      case UPDATE -> handleUpdateEvent(event);
      case REPLACE -> handleReplaceEvent(event);
      case LOADING_START -> showLoadingPanel(event);
      case LOADING_STOP, LOADING_CANCEL -> hideLoadingPanel(event);
      case LOADING_GLOBAL_MSG -> loadingPanelMessage(event);
      default -> LOGGER.debug("Unhandled observable event: {}", action);
    }
  }

  private void handleSelectEvent(Object newVal) {
    MediaSeriesGroup patient = null;
    if (newVal instanceof DicomSeries dcm) {
      patient = model.getParent(dcm, DicomModel.patient);
    } else if (newVal instanceof MediaSeriesGroup group) {
      TagW tagID = group.getTagID();
      if (TagD.getUID(TagD.Level.PATIENT).equals(tagID)) {
        patient = group;
      }
    }
    if (patient != null && !isSelectedPatient(patient)) {
      if (modelPatient.getIndexOf(patient) < 0) {
        modelPatient.addElement(patient);
      }

      modelPatient.setSelectedItem(patient);
      GuiUtils.getUICore()
          .getDockingControl()
          .getController()
          .setFocusedDockable(new DefaultFocusRequest(dockable.intern(), this, false, true, false));
    }
  }

  private void handleAddEvent(Object newVal) {
    if (newVal instanceof DicomSeries series) {
      addDicomSeries(series);
    }
  }

  private void handleRemoveEvent(Object newVal) {
    if (newVal instanceof MediaSeriesGroup group) {
      if (group.getTagID().equals(DicomModel.patient.tagElement())) {
        paneManager.removePatientPane(group);
      } else if (group.getTagID().equals(DicomModel.study.tagElement())) {
        paneManager.removeStudyPane(group);
      } else if (group.getTagID().equals(DicomModel.series.tagElement())) {
        paneManager.removeSeriesPane(group);
      }
    }
  }

  private void handleUpdateEvent(ObservableEvent event) {
    Object newVal = event.getNewValue();
    if (newVal instanceof DicomSeries series) {
      updateSplitSeries(series);
    }
  }

  private void handleReplaceEvent(ObservableEvent event) {
    // Handle series replacement
    Object oldVal = event.getOldValue();
    Object newVal = event.getNewValue();

    if (oldVal instanceof DicomSeries oldSeries && newVal instanceof DicomSeries newSeries) {
      handleSeriesReplacement(oldSeries, newSeries);
    }
  }

  // ========== Model Management Methods ==========

  /** Updates the selected patient through the manager. */
  public void selectPatient(MediaSeriesGroup patient) {
    if (!patientSelectionManager.isCurrentPatient(patient)) {
      patientSelectionManager.setCurrentPatient(patient);
      updatePatientComboBoxSelection(patient);
      updateStudyComboBoxSelection(patient);
      selectedPatient.repaint();
    }
  }

  private void updateStudyComboBoxSelection(MediaSeriesGroup patient) {
    studyCombobox.removeItemListener(studyItemListener);
    try {
      modelStudy.removeAllElements();
      // do not use addElement
      modelStudy.insertElementAt(ALL_STUDIES, 0);
      List<StudyPane> studies = paneManager.getStudyList(patient);
      if (studies != null) {
        for (StudyPane studyPane : studies) {
          modelStudy.addElement(studyPane.getDicomStudy());
        }
      }
      modelStudy.setSelectedItem(ALL_STUDIES);
    } finally {
      studyCombobox.addItemListener(studyItemListener);
      selectStudy();
      koOpen.setVisible(
          HiddenSeriesManager.hasHiddenSpecialElements(KOSpecialElement.class, patient));
      // Send message for selecting related plug-ins window
      model.firePropertyChange(
          new ObservableEvent(ObservableEvent.BasicAction.SELECT, model, null, patient));
    }
  }

  /** Gets the currently selected patient. */
  public MediaSeriesGroupNode getSelectedPatient() {
    return (MediaSeriesGroupNode) selectedPatient.getCurrentPatient().orElse(null);
  }

  /** Checks if the given patient is currently selected. */
  private boolean isSelectedPatient(MediaSeriesGroup patient) {
    return patientSelectionManager.isCurrentPatient(patient);
  }

  private void addDicomSeries(DicomSeries series) {
    if (DicomModel.isHiddenModality(series)) {
      // Up to now nothing has to be done in the explorer view about specialModality
      return;
    }
    LOGGER.info("Add series: {}", series);
    MediaSeriesGroup study = paneManager.getStudyForSeries(series);
    MediaSeriesGroup patient = paneManager.getPatientForStudy(study);
    if (patient == null) {
      LOGGER.warn("No patient found for series: {}", series);
      return;
    }

    if (modelPatient.getIndexOf(patient) < 0) {
      modelPatient.addElement(patient);
      if (modelPatient.getSize() == 1) {
        modelPatient.setSelectedItem(patient);
      }
    }

    List<StudyPane> studies = paneManager.getStudyList(patient);
    Object selectedStudy = modelStudy.getSelectedItem();
    int[] positionStudy = new int[1];
    StudyPane studyPane = paneManager.createStudyPaneInstance(study, positionStudy);

    int[] positionSeries = new int[1];
    paneManager.createSeriesPaneInstance(series, positionSeries);
    if (isSelectedPatient(patient) && positionSeries[0] != -1) {
      // If new study
      if (positionStudy[0] != -1) {
        if (modelStudy.getIndexOf(study) < 0) {
          modelStudy.addElement(study);
        }
        // if modelStudy has the value "All studies"
        if (ALL_STUDIES.equals(selectedStudy)) {
          selectedPatient.removeAll();
          for (StudyPane s : studies) {
            selectedPatient.addPane(s);
          }
          selectedPatient.revalidate();
        }
      }
      if (selectedPatient.isStudyVisible(study)) {
        int thumbnailSize = SeriesThumbnail.getThumbnailSizeFromPreferences();
        studyPane.removeAll();
        List<SeriesPane> seriesList = paneManager.getSeriesList(study);
        for (int i = 0; i < seriesList.size(); i++) {
          studyPane.addPane(seriesList.get(i), i, thumbnailSize);
        }
        studyPane.revalidate();
        studyPane.repaint();
      }
    }
  }

  public void updateRemovedPatient(MediaSeriesGroup patient) {
    SwingUtilities.invokeLater(
        () -> {
          modelPatient.removeElement(patient);
          if (modelPatient.getSize() == 0) {
            modelStudy.removeAllElements();
            modelStudy.insertElementAt(ALL_STUDIES, 0);
            modelStudy.setSelectedItem(ALL_STUDIES);
            koOpen.setVisible(false);
          }
          if (selectedPatient.isPatient(patient)) {
            selectedPatient.refreshLayout();
          }
        });
  }

  public void updateRemovedStudy(StudyPane studyPane, MediaSeriesGroup study) {
    SwingUtilities.invokeLater(
        () -> {
          if (selectedPatient.isStudyVisible(study)) {
            selectedPatient.remove(studyPane);
            modelStudy.removeElement(study);
            selectedPatient.revalidate();
            selectedPatient.repaint();
          }
        });
  }

  private void handleSeriesReplacement(DicomSeries oldSeries, DicomSeries newSeries) {
    // Mark old series for replacement
    splitSeriesManager.markSeriesAsReplacement(oldSeries);

    // Process the new series
    if (newSeries instanceof DicomSeries dicomSeries) {
      updateSplitSeries(dicomSeries);
    }
  }

  // ========== Split Series Management ==========

  public void updateSplitSeries(DicomSeries dcmSeries) {
    if (dcmSeries != null) {
      splitSeriesManager.updateSplitSeries(dcmSeries);
    }
  }

  /**
   * Gets all split series for a given DICOM series. This method provides compatibility with the old
   * DicomExplorer API.
   */
  public List<DicomSeries> getSplitSeries(DicomSeries series) {
    return splitSeriesManager.getSplitSeries(series);
  }

  // ========== UI Selection Methods ==========

  public PatientPane getSelectedPatientPane() {
    return selectedPatient;
  }

  private void selectStudy() {
    selectionList.clear();
    Object selectedItem = modelStudy.getSelectedItem();
    if (ALL_STUDIES.equals(selectedItem)) {
      MediaSeriesGroupNode patient = getSelectedPatient();
      if (patient != null) {
        selectedPatient.showAllStudies();
      } else {
        selectedPatient.removeAll();
      }
    } else if (selectedItem instanceof MediaSeriesGroup study) {
      selectedPatient.showSpecificStudy(study);
    }
    selectedPatient.repaint();
  }

  // ========== Thumbnail Size Management ==========

  public void updateThumbnailSize(int thumbnailSize) {
    updateDockableWidth(Math.max(thumbnailSize, Thumbnail.DEFAULT_SIZE) + 42);
    MediaSeriesGroup patient = getSelectedPatient();
    for (StudyPane studyPane : paneManager.getStudyList(patient)) {
      studyPane.updateThumbnailSize(thumbnailSize);
      studyPane.doLayout();
    }
    selectedPatient.revalidate();
    selectedPatient.repaint();
  }

  // ========== Navigation Methods ==========

  public MediaSeries<? extends MediaElement> movePatient(
      ViewCanvas<DicomImageElement> view, ListPosition position) {
    if (view == null) {
      return null;
    }

    MediaSeriesGroup patientGroup;
    MediaSeriesGroup seriesGroup;
    MediaSeriesGroup series = view.getSeries();

    if (series == null) {
      seriesGroup = getSeries(null, null, ListPosition.FIRST);
    } else {
      MediaSeriesGroup studyGroup = model.getParent(series, DicomModel.study);
      patientGroup = model.getParent(studyGroup, DicomModel.patient);
      seriesGroup = getSeriesGroupFromPatient(patientGroup, position);
    }

    if (seriesGroup instanceof DicomSeries dicomSeries) {
      ThumbnailMouseAndKeyAdapter.openSeriesInDefaultPlugin(dicomSeries, model);
    }

    return null;
  }

  public MediaSeries<? extends MediaElement> moveStudy(
      ViewCanvas<DicomImageElement> view, ListPosition position) {
    if (view == null) {
      return null;
    }

    MediaSeriesGroup seriesGroup;
    MediaSeriesGroup series = view.getSeries();

    if (series == null) {
      seriesGroup = getSeries(null, null, ListPosition.FIRST);
    } else {
      seriesGroup = getSeriesGroupFromStudy(model.getParent(series, DicomModel.study), position);
    }

    return displaySeries(view, seriesGroup);
  }

  public MediaSeries<? extends MediaElement> moveSeries(
      ViewCanvas<DicomImageElement> view, ListPosition position) {
    if (view == null) {
      return null;
    }

    MediaSeriesGroup seriesGroup = getSeriesGroup(view.getSeries(), position);
    return displaySeries(view, seriesGroup);
  }

  public Set<DicomSeries> getSelectedPatientOpenSeries() {
    MediaSeriesGroupNode patient = getSelectedPatient();
    if (patient != null) {
      Set<DicomSeries> openSeriesSet = new LinkedHashSet<>();
      synchronized (model) {
        for (MediaSeriesGroup study : model.getChildren(patient)) {
          for (MediaSeriesGroup seq : model.getChildren(study)) {
            if (seq instanceof DicomSeries series
                && Boolean.TRUE.equals(seq.getTagValue(TagW.SeriesOpen))) {
              openSeriesSet.add(series);
            }
          }
        }
      }
      return openSeriesSet;
    }
    return Set.of();
  }

  // ========== Navigation Helper Methods ==========

  public MediaSeriesGroup getSeriesGroupFromPatient(
      MediaSeriesGroup patientGroup, ListPosition position) {
    if (patientGroup == null) {
      return getSeries(null, null, ListPosition.FIRST);
    } else {
      MediaSeriesGroup patient = getPatientFromList(patientGroup, position);
      return getSeries(getFirstStudy(patient), null, ListPosition.FIRST);
    }
  }

  public MediaSeriesGroup getSeriesGroupFromStudy(
      MediaSeriesGroup studyGroup, ListPosition position) {
    if (studyGroup == null) {
      return getSeries(null, null, ListPosition.FIRST);
    } else {
      MediaSeriesGroup study = getStudyFromList(studyGroup, position);
      return getSeries(study, null, ListPosition.FIRST);
    }
  }

  public MediaSeriesGroup getSeriesGroup(MediaSeriesGroup series, ListPosition position) {
    if (series == null) {
      return getSeries(null, null, position);
    } else {
      MediaSeriesGroup studyGroup = model.getParent(series, DicomModel.study);
      return getSeries(studyGroup, series, position);
    }
  }

  private MediaSeriesGroup getSeries(
      MediaSeriesGroup studyGroup, MediaSeriesGroup series, ListPosition position) {
    if (studyGroup == null || series == null) {
      List<StudyPane> studyList =
          paneManager.getStudyList(paneManager.getPatientForStudy(studyGroup));
      for (StudyPane studyPane : studyList) {
        List<SeriesPane> seriesList = paneManager.getSeriesList(studyPane.getDicomStudy());
        if (!seriesList.isEmpty()) {
          if (position == ListPosition.LAST) {
            return seriesList.getLast().getDicomSeries();
          } else {
            return seriesList.getFirst().getDicomSeries();
          }
        }
      }
    } else {
      return getSeriesFromList(studyGroup, series, position);
    }
    return null;
  }

  private MediaSeriesGroup getSeriesFromList(
      MediaSeriesGroup studyGroup, MediaSeriesGroup series, ListPosition position) {
    List<SeriesPane> seriesList = paneManager.getSeriesList(studyGroup);
    if (!seriesList.isEmpty()) {
      if (series == null) {
        if (position == ListPosition.LAST) {
          return seriesList.getLast().getDicomSeries();
        } else {
          return seriesList.getFirst().getDicomSeries();
        }
      }

      int index = 0;
      if (position == ListPosition.LAST) {
        index = seriesList.size() - 1;
      } else if (position == ListPosition.PREVIOUS) {
        index = getSeriesIndex(seriesList, series) - 1;
      } else if (position == ListPosition.NEXT) {
        index = getSeriesIndex(seriesList, series) + 1;
      }

      if (index >= 0 && index < seriesList.size()) {
        return seriesList.get(index).getDicomSeries();
      }
    }
    return null;
  }

  // ========== Helper Methods for Navigation ==========

  private MediaSeriesGroup getPatientFromList(
      MediaSeriesGroup patientGroup, ListPosition position) {
    if (patientGroup == null) {
      return getSelectedPatient();
    }

    if (modelPatient.getSize() > 0) {
      int index = 0;
      if (position == ListPosition.LAST) {
        index = modelPatient.getSize() - 1;
      } else if (position == ListPosition.PREVIOUS) {
        index = getPatientIndex(patientGroup) - 1;
      } else if (position == ListPosition.NEXT) {
        index = getPatientIndex(patientGroup) + 1;
      }

      if (index >= 0 && index < modelPatient.getSize()) {
        Object object = modelPatient.getElementAt(index);
        if (object instanceof MediaSeriesGroupNode patient) {
          return patient;
        }
      }
    }
    return null;
  }

  private MediaSeriesGroup getStudyFromList(MediaSeriesGroup studyGroup, ListPosition position) {
    MediaSeriesGroup patientGroup = paneManager.getPatientForStudy(studyGroup);
    if (patientGroup == null || studyGroup == null) {
      return getFirstStudy(patientGroup);
    }

    List<StudyPane> studyPanes = paneManager.getStudyList(patientGroup);
    if (studyPanes != null && !studyPanes.isEmpty()) {
      int index = 0;
      if (position == ListPosition.LAST) {
        index = studyPanes.size() - 1;
      } else if (position == ListPosition.PREVIOUS) {
        index = getStudyIndex(studyPanes, studyGroup) - 1;
      } else if (position == ListPosition.NEXT) {
        index = getStudyIndex(studyPanes, studyGroup) + 1;
      }

      if (index >= 0 && index < studyPanes.size()) {
        return studyPanes.get(index).getDicomStudy();
      }
    }
    return null;
  }

  private MediaSeriesGroup getFirstStudy(MediaSeriesGroup patient) {
    List<StudyPane> studyList = paneManager.getStudyList(patient);
    if (!studyList.isEmpty()) {
      return studyList.getFirst().getDicomStudy();
    }
    return null;
  }

  // ========== Index Helper Methods ==========

  private int getPatientIndex(MediaSeriesGroup patientGroup) {
    if (patientGroup != null) {
      synchronized (modelPatient) {
        for (int i = 0; i < modelPatient.getSize(); i++) {
          if (patientGroup.equals(modelPatient.getElementAt(i))) {
            return i;
          }
        }
      }
    }
    return 0;
  }

  private int getStudyIndex(List<StudyPane> studyList, MediaSeriesGroup studyGroup) {
    if (studyList != null && !studyList.isEmpty()) {
      for (int i = 0; i < studyList.size(); i++) {
        StudyPane st = studyList.get(i);
        if (st.isStudy(studyGroup)) {
          return i;
        }
      }
    }
    return 0;
  }

  private int getSeriesIndex(List<SeriesPane> seriesList, MediaSeriesGroup seriesGroup) {
    if (seriesList != null && !seriesList.isEmpty()) {
      for (int i = 0; i < seriesList.size(); i++) {
        SeriesPane se = seriesList.get(i);
        if (se.isSeries(seriesGroup)) {
          return i;
        }
      }
    }
    return 0;
  }

  // ========== Display Helper Methods ==========

  private MediaSeries<DicomImageElement> displaySeries(
      ViewCanvas<DicomImageElement> view, MediaSeriesGroup seriesGroup) {
    if (view != null && seriesGroup instanceof DicomSeries dicomSeries) {
      view.setSeries(null);
      view.setSeries(dicomSeries, null);
      return dicomSeries;
    }
    return null;
  }

  // ========== UI State Management ==========

  private void loadingPanelMessage(ObservableEvent event) {
    if (event.getNewValue() instanceof String message) {
      loadingPanel.setGlobalMessage(message);
    }
  }

  private void showLoadingPanel(ObservableEvent event) {
    if (event.getNewValue() instanceof ExplorerTask<?, ?> task) {
      GuiExecutor.invokeAndWait(
          () -> {
            loadingPanel.addTask(task);
            revalidate();
            repaint();
          });
    }
  }

  private void hideLoadingPanel(ObservableEvent event) {
    if (event.getNewValue() instanceof ExplorerTask<?, ?> task) {
      GuiExecutor.invokeAndWait(
          () -> {
            if (loadingPanel.removeTask(task)) {
              revalidate();
              repaint();
            }
          });
    }
    MediaSeriesGroupNode patient = getSelectedPatient();
    if (patient != null) {
      koOpen.setVisible(
          HiddenSeriesManager.hasHiddenSpecialElements(KOSpecialElement.class, patient));
    }
  }

  // ========== Utility Methods ==========

  private MediaSeriesGroupNode getPatient(Object item) {
    if (item instanceof MediaSeriesGroupNode patient) {
      return patient;
    }
    return null;
  }

  // ========== Public API ==========

  @Override
  public DicomModel getDataExplorerModel() {
    return model;
  }

  @Override
  public List<Action> getOpenImportDialogAction() {
    ArrayList<Action> actions = new ArrayList<>(2);
    actions.add(ImportToolBar.buildImportAction(this, model, BUTTON_NAME));
    DefaultAction importCDAction =
        new DefaultAction(
            Messages.getString("DicomExplorer.dcmCD"),
            ResourceUtil.getIcon(ResourceUtil.OtherIcon.CDROM),
            event ->
                ImportToolBar.openImportDicomCdAction(
                    this, model, Messages.getString("DicomExplorer.dcmCD")));
    actions.add(importCDAction);
    return actions;
  }

  @Override
  public List<Action> getOpenExportDialogAction() {
    return Collections.singletonList(ExportToolBar.buildExportAction(this, model, BUTTON_NAME));
  }

  @Override
  public void importFiles(File[] files, boolean recursive) {
    if (files != null) {
      HangingProtocols.OpeningViewer openingViewer =
          HangingProtocols.OpeningViewer.getOpeningViewerByLocalKey(
              LocalImport.LAST_OPEN_VIEWER_MODE);
      DicomModel.LOADING_EXECUTOR.execute(
          new LoadLocalDicom(files, recursive, model, openingViewer));
    }
  }

  @Override
  public boolean canImportFiles() {
    return true;
  }

  public SeriesSelectionModel getSelectionList() {
    return selectionList;
  }

  public DicomTaskManager getTaskManager() {
    return taskManager;
  }

  public DicomPaneManager getPaneManager() {
    return paneManager;
  }

  public SplitSeriesManager getSplitSeriesManager() {
    return splitSeriesManager;
  }

  // ========== SeriesViewerListener Implementation ==========

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    SeriesViewerEvent.EVENT type = event.getEventType();
    if (SeriesViewerEvent.EVENT.SELECT_VIEW.equals(type)
        && event.getSeriesViewer() instanceof ImageViewerPlugin) {
      ViewCanvas<?> pane = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane();
      if (pane != null) {
        MediaSeries<?> s = pane.getSeries();
        if (s != null
            && !getSelectionList().isOpeningSeries()
            && paneManager.containsSeriesInPatient(selectedPatient, s)) {
          SeriesPane p = paneManager.getSeriesPane(s);
          if (p != null) {
            JViewport vp = thumbnailView.getViewport();
            Rectangle bound = vp.getViewRect();
            Point ptmin = SwingUtilities.convertPoint(p, new Point(0, 0), selectedPatient);
            Point ptmax =
                SwingUtilities.convertPoint(p, new Point(0, p.getHeight()), selectedPatient);
            if (!bound.contains(ptmin.x, ptmin.y) || !bound.contains(ptmax.x, ptmax.y)) {
              Point pt = vp.getViewPosition();
              pt.y = ptmin.y + (ptmax.y - ptmin.y) / 2;
              pt.y -= vp.getHeight() / 2;
              int maxHeight = (int) (vp.getViewSize().getHeight() - vp.getExtentSize().getHeight());
              if (pt.y < 0) {
                pt.y = 0;
              } else if (pt.y > maxHeight) {
                pt.y = maxHeight;
              }
              vp.setViewPosition(pt);
              // Clear the selection when another view is selected
              getSelectionList().clear();
            }
          }
        }
      }
    }
  }

  // ========== Cleanup ==========

  @Override
  public void dispose() {
    if (model != null) {
      model.removePropertyChangeListener(this);
    }
    if (patientSelectionManager != null) {
      patientSelectionManager.removePatientSelectionListener(selectedPatient);
    }

    if (taskManager != null) {
      taskManager.shutdown();
    }
    if (paneManager != null) {
      paneManager.dispose();
    }
    if (splitSeriesManager != null) {
      splitSeriesManager.dispose();
    }

    super.closeDockable();
    LOGGER.info("DicomExplorer disposed");
  }
}
