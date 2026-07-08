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
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatSearchIcon;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.*;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SequenceHandler;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.ArrayListComboBoxModel;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.SearchableComboBox;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.*;
import org.weasis.dicom.explorer.*;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.exp.ExplorerTask;
import org.weasis.dicom.explorer.exp.ExportToolBar;
import org.weasis.dicom.explorer.imp.ImportToolBar;

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
  private final SearchableComboBox<Object> patientCombobox;

  private final ItemListener patientItemListener;

  // Series filter, memorized per patient for the whole session; the active mode selects the
  // dimension
  private final Map<MediaSeriesGroup, SeriesFilter> filtersByPatient = new HashMap<>();
  private SeriesFilter seriesFilter = new SeriesFilter();
  private final SearchableComboBox<Object> seriesFilterField = new SearchableComboBox<>();
  private final JButton modeButton = new JButton();
  private final JPopupMenu modePopup = new JPopupMenu();
  private final Map<SeriesFilter.Mode, JRadioButtonMenuItem> modeItems =
      new EnumMap<>(SeriesFilter.Mode.class);
  private final JLabel filterStatusLabel = new JLabel();
  private final javax.swing.Timer filterDebounce = new javax.swing.Timer(250, _ -> runFilter());
  private final Set<String> availableModalities = new TreeSet<>();
  private JPanel filterBar;
  private JTextField seriesFilterEditor;
  private boolean adjustingFilterUi;

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
    this.patientCombobox = new SearchableComboBox<>(modelPatient);

    // Initialize KO button
    this.koOpen =
        new JButton(
            Messages.getString("DicomExplorer.open_ko"),
            ResourceUtil.getIcon(ResourceUtil.OtherIcon.KEY_IMAGE));

    // Create event listeners
    this.patientItemListener = createPatientItemListener();

    // Initialize patient selection manager
    this.patientSelectionManager = new PatientSelectionManager(this);
    this.patientSelectionManager.addPatientSelectionListener(selectedPatient);

    // Configure dockable
    dockable.setMaximizable(true);
    setupInitialSize();

    // Setup UI
    setupComboBoxes();
    setupFilterBar();
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
      showSelectedPatientFromStart();
    } finally {
      patientCombobox.addItemListener(patientItemListener);
    }
  }

  /**
   * Re-displays the current patient in the combo, e.g. after the user cleared the search field but
   * the patient's thumbnails are still shown and one gets selected or opened.
   */
  public void ensurePatientComboSelection() {
    updatePatientComboBoxSelection(getSelectedPatient());
  }

  public boolean isVerticalLayout() {
    return verticalLayout;
  }

  protected JPanel getMainPanel() {
    if (panelMain == null) {
      MigLayout layout =
          new MigLayout("fillx, ins 5 0 5 0", "[right]rel[grow,fill]", "[]10lp[]"); // NON-NLS
      panelMain = new JPanel(layout);

      // Small preferred width so the combos grow to fill the panel but never drive its width
      JPanel patientBar = new JPanel(new MigLayout("ins 1, fillx", "[grow,fill]", "")); // NON-NLS
      patientBar.add(patientCombobox, "growx, width 30lp:30lp:250lp"); // NON-NLS
      panelMain.add(patientBar, "newline, spanx, growx"); // NON-NLS

      filterBar = new JPanel(new MigLayout("ins 1, fillx", "[grow,fill]", "")); // NON-NLS
      filterBar.add(seriesFilterField, "growx, width 30lp:30lp:250lp"); // NON-NLS
      panelMain.add(filterBar, "newline, spanx, growx"); // NON-NLS
      panelMain.add(filterStatusLabel, "newline, spanx, growx, hidemode 3"); // NON-NLS
      updateFilterIndicator();

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
    setDockableWidth(computeThumbnailPanelWidth(SeriesThumbnail.getThumbnailSizeFromPreferences()));
  }

  /**
   * Logical width to pass to {@code setDockableWidth}, which fits exactly one thumbnail column and
   * its chrome (study-pane border + sub-panel insets). The vertical scrollbar width is always
   * reserved so the panel width stays stable whether or not the scrollbar is shown. The
   * device-pixel sum is converted back to logical units because {@code setDockableWidth} re-applies
   * UI scaling; otherwise the width would be scaled twice (too wide when zoomed).
   */
  private int computeThumbnailPanelWidth(int thumbnailSize) {
    int scrollBar = thumbnailView.getVerticalScrollBar().getPreferredSize().width;
    int margin = GuiUtils.getScaleLength(2);
    int column = measuredSingleColumnWidth();
    if (column <= 0) {
      // Fallback estimate before any study pane exists; corrected by adjustDockableWidth() on load
      column =
          GuiUtils.getScaleLength(Math.max(thumbnailSize, Thumbnail.DEFAULT_SIZE))
              + StudyPane.getHorizontalChrome(selectedPatient);
    }
    return Math.round((column + scrollBar + margin) / UIScale.getUserScaleFactor());
  }

  /** Widest exact one-column width among the selected patient's study panes, or 0 if none. */
  private int measuredSingleColumnWidth() {
    int max = 0;
    for (StudyPane studyPane : paneManager.getStudyList(getSelectedPatient())) {
      max = Math.max(max, studyPane.getSingleColumnWidth());
    }
    return max;
  }

  /** Re-applies the dock width from the actual laid-out study panes; a no-op if already correct. */
  private void adjustDockableWidth() {
    int width = computeThumbnailPanelWidth(SeriesThumbnail.getThumbnailSizeFromPreferences());
    if (width != getDockableWidth()) {
      updateDockableWidth(width);
    }
  }

  private void setupComboBoxes() {
    patientCombobox.setMaximumRowCount(15);
    patientCombobox.setFont(FontItem.SMALL_SEMIBOLD.getFont());
    patientCombobox.putClientProperty(FlatClientProperties.STYLE, "padding: 3,2,3,0"); // NON-NLS
    JTextField patientEditor = (JTextField) patientCombobox.getEditor().getEditorComponent();
    patientCombobox.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT, Messages.getString("DicomExplorer.search_patient"));
    JLabel patientIcon = new JLabel(ResourceUtil.getIcon(ResourceUtil.OtherIcon.PATIENT, 16, 16));
    patientEditor.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, patientIcon);
    patientCombobox.addItemListener(patientItemListener);
  }

  // ========== Series Filter ==========

  public SeriesFilter getSeriesFilter() {
    return seriesFilter;
  }

  private void setupFilterBar() {
    filterDebounce.setRepeats(false);

    seriesFilterField.setMaximumRowCount(15);
    seriesFilterField.setFont(FontItem.SMALL_SEMIBOLD.getFont());
    seriesFilterField.putClientProperty(FlatClientProperties.STYLE, "padding: 3,2,3,0"); // NON-NLS
    seriesFilterEditor = (JTextField) seriesFilterField.getEditor().getEditorComponent();
    seriesFilterField.setSearchCallback(_ -> onFilterInput());
    seriesFilterField.addActionListener(_ -> onFilterSelection());
    // Rebuild the suggestion list on open so it reflects series loaded after the patient selection
    seriesFilterField.addPopupMenuListener(
        new PopupMenuListener() {
          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            refreshSuggestions();
          }

          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            // no-op
          }

          @Override
          public void popupMenuCanceled(PopupMenuEvent e) {
            // no-op
          }
        });

    modeButton.setFocusable(false);
    modeButton.putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    modeButton.setBorder(BorderFactory.createEmptyBorder());
    modeButton.setMargin(new Insets(0, 0, 0, 0));
    modeButton.addActionListener(_ -> modePopup.show(modeButton, 0, modeButton.getHeight()));
    // Place the mode selector inside the field, in place of the default search icon
    seriesFilterEditor.putClientProperty(
        FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, modeButton);

    buildModePopup();

    filterStatusLabel.setFont(FontItem.SMALL.getFont());
    filterStatusLabel.setVisible(false);

    applyMode(SeriesFilter.Mode.TEXT);
  }

  private void buildModePopup() {
    ButtonGroup group = new ButtonGroup();
    for (SeriesFilter.Mode mode : SeriesFilter.Mode.values()) {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(modeLabel(mode), modeIcon(mode));
      item.setSelected(mode == seriesFilter.getMode());
      item.addActionListener(_ -> applyMode(mode));
      group.add(item);
      modePopup.add(item);
      modeItems.put(mode, item);
    }
  }

  private void applyMode(SeriesFilter.Mode mode) {
    seriesFilter.setMode(mode);
    syncFilterToState(getSelectedPatient());
    runFilter();
  }

  /** Activates the given patient's memorized filter (creating a default one on first visit). */
  private void loadFilterForPatient(MediaSeriesGroup patient) {
    seriesFilter =
        patient == null
            ? new SeriesFilter()
            : filtersByPatient.computeIfAbsent(patient, _ -> new SeriesFilter());
    syncFilterToState(patient);
  }

  /** Reflects the active filter's mode and criteria in the mode button and search field. */
  private void syncFilterToState(MediaSeriesGroup patient) {
    SeriesFilter.Mode mode = seriesFilter.getMode();
    modeButton.setIcon(modeIcon(mode));
    modeButton.setToolTipText(
        MessageFormat.format(Messages.getString("DicomExplorer.filter_mode"), modeLabel(mode)));
    JRadioButtonMenuItem modeItem = modeItems.get(mode);
    if (modeItem != null) {
      modeItem.setSelected(true);
    }
    availableModalities.clear();
    availableModalities.addAll(collectSeriesValues(patient, Tag.Modality));
    adjustingFilterUi = true;
    try {
      seriesFilterField.putClientProperty(
          FlatClientProperties.PLACEHOLDER_TEXT, modePlaceholder(mode));
      switch (mode) {
        case TEXT -> {
          Set<String> suggestions = collectSeriesValues(patient, Tag.SeriesDescription);
          suggestions.addAll(collectStudyValues(patient, Tag.StudyDescription));
          setFieldItems(suggestions.toArray());
          seriesFilterEditor.setText(seriesFilter.getText());
        }
        case MODALITY -> {
          setFieldItems(buildModalitySuggestions(patient).toArray());
          seriesFilterEditor.setText(String.join(", ", seriesFilter.getModalities()));
        }
        case DATE -> {
          MediaSeriesGroup study = seriesFilter.getStudy();
          if (study != null && paneManager.getStudyPane(study) == null) {
            seriesFilter.setStudy(null); // stored study no longer exists
            study = null;
          }
          setFieldItems(buildStudyItems(patient));
          seriesFilterField.setSelectedItem(study != null ? study : ALL_STUDIES);
          seriesFilterEditor.setText(StringUtil.EMPTY_STRING);
        }
      }
    } finally {
      adjustingFilterUi = false;
    }
    showFilterValueFromStart();
    updateFilterIndicator();
  }

  /**
   * Rebuilds the suggestion list for the current mode from the up-to-date panes, keeping the user's
   * typed text or study selection. Called when the dropdown opens, as series can be registered
   * after the patient was first selected.
   */
  private void refreshSuggestions() {
    if (adjustingFilterUi || seriesFilterField.isFiltering()) {
      return;
    }
    MediaSeriesGroup patient = getSelectedPatient();
    availableModalities.clear();
    availableModalities.addAll(collectSeriesValues(patient, Tag.Modality));
    adjustingFilterUi = true;
    try {
      String current = seriesFilterEditor.getText();
      switch (seriesFilter.getMode()) {
        case TEXT -> {
          Set<String> suggestions = collectSeriesValues(patient, Tag.SeriesDescription);
          suggestions.addAll(collectStudyValues(patient, Tag.StudyDescription));
          setFieldItems(suggestions.toArray());
          seriesFilterEditor.setText(current);
        }
        case MODALITY -> {
          setFieldItems(buildModalitySuggestions(patient).toArray());
          seriesFilterEditor.setText(current);
        }
        case DATE -> {
          MediaSeriesGroup study = seriesFilter.getStudy();
          setFieldItems(buildStudyItems(patient));
          seriesFilterField.setSelectedItem(study != null ? study : ALL_STUDIES);
        }
      }
    } finally {
      adjustingFilterUi = false;
    }
  }

  private void onFilterInput() {
    if (adjustingFilterUi) {
      return;
    }
    String value = seriesFilterEditor.getText();
    switch (seriesFilter.getMode()) {
      case TEXT -> seriesFilter.setText(value);
      case MODALITY -> seriesFilter.setModalities(parseModalities(value));
      case DATE -> {
        // Study selection is applied on item selection (onFilterSelection)
      }
    }
    filterDebounce.restart();
  }

  private void onFilterSelection() {
    if (adjustingFilterUi) {
      return;
    }
    if (seriesFilter.getMode() == SeriesFilter.Mode.DATE) {
      Object selected = seriesFilterField.getSelectedItem();
      seriesFilter.setStudy(selected instanceof MediaSeriesGroup study ? study : null);
      filterDebounce.restart();
    } else {
      onFilterInput();
    }
    // On a real selection (not while filtering), show a long value from its start
    if (!seriesFilterField.isFiltering()) {
      showFilterValueFromStart();
    }
  }

  /** Scrolls the filter field back to the beginning so a long value is not shown from its end. */
  private void showFilterValueFromStart() {
    if (seriesFilterEditor != null) {
      SwingUtilities.invokeLater(() -> seriesFilterEditor.setCaretPosition(0));
    }
  }

  private void runFilter() {
    applyFilterLayout();
  }

  /** Re-lays out the selected patient applying the current filter, then refreshes the indicator. */
  private void applyFilterLayout() {
    selectionList.clear();
    if (getSelectedPatient() != null) {
      selectedPatient.showAllStudies();
    } else {
      selectedPatient.removeAll();
    }
    selectedPatient.repaint();
    updateFilterIndicator();
    // Once study panes are laid out, size the dock to their exact one-column width
    SwingUtilities.invokeLater(this::adjustDockableWidth);
  }

  private void setFieldItems(Object[] items) {
    seriesFilterField.setModel(new DefaultComboBoxModel<>(items));
    // Avoid auto-selecting the first suggestion so an empty field shows its placeholder
    seriesFilterField.setSelectedItem(null);
  }

  private Object[] buildStudyItems(MediaSeriesGroup patient) {
    List<Object> items = new ArrayList<>();
    items.add(ALL_STUDIES);
    if (patient != null) {
      for (StudyPane studyPane : paneManager.getStudyList(patient)) {
        items.add(studyPane.getDicomStudy());
      }
    }
    return items.toArray();
  }

  private List<String> buildModalitySuggestions(MediaSeriesGroup patient) {
    List<String> suggestions = new ArrayList<>(availableModalities);
    if (patient != null) {
      Set<String> combinations = new TreeSet<>();
      for (StudyPane studyPane : paneManager.getStudyList(patient)) {
        Set<String> perStudy = new TreeSet<>();
        for (SeriesPane seriesPane : paneManager.getSeriesList(studyPane.getDicomStudy())) {
          String modality =
              TagD.getTagValue(seriesPane.getDicomSeries(), Tag.Modality, String.class);
          if (StringUtil.hasText(modality)) {
            perStudy.add(modality);
          }
        }
        if (perStudy.size() > 1) {
          combinations.add(String.join(", ", perStudy));
        }
      }
      suggestions.addAll(combinations);
    }
    return suggestions;
  }

  private Set<String> parseModalities(String value) {
    Set<String> result = new LinkedHashSet<>();
    if (StringUtil.hasText(value)) {
      for (String token : value.split("[,\\s]+")) { // NON-NLS
        if (StringUtil.hasText(token)) {
          result.add(token.trim().toUpperCase());
        }
      }
    }
    return result;
  }

  private Set<String> collectSeriesValues(MediaSeriesGroup patient, int tagId) {
    Set<String> values = new TreeSet<>();
    if (patient != null) {
      for (StudyPane studyPane : paneManager.getStudyList(patient)) {
        for (SeriesPane seriesPane : paneManager.getSeriesList(studyPane.getDicomStudy())) {
          String value = TagD.getTagValue(seriesPane.getDicomSeries(), tagId, String.class);
          if (StringUtil.hasText(value)) {
            values.add(value);
          }
        }
      }
    }
    return values;
  }

  private Set<String> collectStudyValues(MediaSeriesGroup patient, int tagId) {
    Set<String> values = new TreeSet<>();
    if (patient != null) {
      for (StudyPane studyPane : paneManager.getStudyList(patient)) {
        String value = TagD.getTagValue(studyPane.getDicomStudy(), tagId, String.class);
        if (StringUtil.hasText(value)) {
          values.add(value);
        }
      }
    }
    return values;
  }

  private void updateFilterIndicator() {
    boolean active = seriesFilter.isActive();
    if (filterBar == null) {
      return;
    }
    if (active) {
      int[] counts = countSeries();
      Color accent = FlatUIUtils.getUIColor("Component.accentColor", Color.BLUE); // NON-NLS
      filterBar.setBorder(BorderFactory.createLineBorder(accent));
      filterStatusLabel.setForeground(accent);
      filterStatusLabel.setText(
          MessageFormat.format(
              Messages.getString("DicomExplorer.filter_status"), counts[0], counts[1]));
      filterStatusLabel.setVisible(true);
    } else {
      filterBar.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
      filterStatusLabel.setText(StringUtil.EMPTY_STRING);
      filterStatusLabel.setVisible(false);
    }
    filterBar.revalidate();
    filterBar.repaint();
  }

  /** Counts the series of the selected patient as {@code {matching, total}}. */
  private int[] countSeries() {
    MediaSeriesGroup patient = getSelectedPatient();
    int matching = 0;
    int total = 0;
    if (patient != null) {
      for (StudyPane studyPane : paneManager.getStudyList(patient)) {
        MediaSeriesGroup study = studyPane.getDicomStudy();
        for (SeriesPane seriesPane : paneManager.getSeriesList(study)) {
          total++;
          if (seriesFilter.test(seriesPane.getDicomSeries(), study)) {
            matching++;
          }
        }
      }
    }
    return new int[] {matching, total};
  }

  private static String modeLabel(SeriesFilter.Mode mode) {
    return switch (mode) {
      case TEXT -> Messages.getString("DicomExplorer.filter_mode_text");
      case DATE -> Messages.getString("DicomExplorer.filter_mode_date");
      case MODALITY -> Messages.getString("DicomExplorer.filter_mode_modality");
    };
  }

  private static String modePlaceholder(SeriesFilter.Mode mode) {
    return switch (mode) {
      case TEXT -> Messages.getString("DicomExplorer.filter_ph_text");
      case DATE -> Messages.getString("DicomExplorer.filter_ph_date");
      case MODALITY -> Messages.getString("DicomExplorer.filter_ph_modality");
    };
  }

  private static Icon modeIcon(SeriesFilter.Mode mode) {
    return switch (mode) {
      case TEXT -> new FlatSearchIcon();
      case DATE -> ResourceUtil.getIcon(ResourceUtil.OtherIcon.CALENDAR, 16, 16);
      case MODALITY -> ResourceUtil.getIcon(ResourceUtil.OtherIcon.MODALITY, 16, 16);
    };
  }

  private void setupThumbnailView() {
    thumbnailView.setBorder(BorderFactory.createEmptyBorder());
    thumbnailView.getVerticalScrollBar().setUnitIncrement(16);
    thumbnailView.setViewportView(selectedPatient);
    thumbnailView.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    thumbnailView.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    // Study panes keep their content width, so recompute their columns on viewport resize
    thumbnailView
        .getViewport()
        .addComponentListener(
            new ComponentAdapter() {
              @Override
              public void componentResized(ComponentEvent e) {
                paneManager
                    .getStudyList(getSelectedPatient())
                    .forEach(StudyPane::refreshLayoutAsync);
              }
            });

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
      // Ignore the transient String selections emitted while the search field is filtering
      if (e.getStateChange() == ItemEvent.SELECTED
          && !patientCombobox.isFiltering()
          && e.getItem() instanceof MediaSeriesGroup patient) {
        selectPatient(patient);
        showSelectedPatientFromStart();
      }
    };
  }

  /** Scrolls the patient field back to the beginning so a long name is not shown from its end. */
  private void showSelectedPatientFromStart() {
    if (patientCombobox.getEditor().getEditorComponent() instanceof JTextField field) {
      SwingUtilities.invokeLater(() -> field.setCaretPosition(0));
    }
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
      default -> LOGGER.trace("Unhandled observable event: {}", action);
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
      onPatientChanged(patient);
      selectedPatient.repaint();
    }
  }

  private void onPatientChanged(MediaSeriesGroup patient) {
    loadFilterForPatient(patient);
    applyFilterLayout();
    koOpen.setVisible(
        HiddenSeriesManager.hasHiddenSpecialElements(KOSpecialElement.class, patient));
    // Send message for selecting related plug-ins window
    model.firePropertyChange(
        new ObservableEvent(ObservableEvent.BasicAction.SELECT, model, null, patient));
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

    int[] positionStudy = new int[1];
    StudyPane studyPane = paneManager.createStudyPaneInstance(study, positionStudy);

    int[] positionSeries = new int[1];
    paneManager.createSeriesPaneInstance(series, positionSeries);
    if (isSelectedPatient(patient) && positionSeries[0] != -1) {
      if (positionStudy[0] != -1) {
        // New study: re-lay out all studies in sorted order applying the filter
        applyFilterLayout();
      } else {
        addSeriesToStudyView(study, studyPane, series);
      }
    }
  }

  /**
   * Inserts a single newly added series into an already displayed study at its sorted position,
   * without tearing down and rebuilding the whole study pane. Rebuilding the entire study on every
   * new series (as happens during a Q/R retrieve, where series are discovered one downloaded image
   * at a time) makes all sibling thumbnails flicker.
   */
  private void addSeriesToStudyView(
      MediaSeriesGroup study, StudyPane studyPane, DicomSeries series) {
    if (!seriesFilter.test(series, study)) {
      return; // Series hidden by the current filter
    }
    SeriesPane seriesPane = paneManager.getSeriesPane(series);
    if (seriesPane == null) {
      return;
    }
    boolean wasVisible = selectedPatient.isStudyVisible(study);
    studyPane.addPane(
        seriesPane,
        visibleSeriesIndex(study, series),
        SeriesThumbnail.getThumbnailSizeFromPreferences());
    if (!wasVisible) {
      selectedPatient.addPane(studyPane);
    }
    studyPane.refreshLayout();
    selectedPatient.revalidate();
    selectedPatient.repaint();
  }

  /** Index of {@code series} among the currently visible (filtered) series of the study. */
  private int visibleSeriesIndex(MediaSeriesGroup study, DicomSeries series) {
    int index = 0;
    for (SeriesPane pane : paneManager.getSeriesList(study)) {
      DicomSeries current = pane.getDicomSeries();
      if (current.equals(series)) {
        break;
      }
      if (seriesFilter.test(current, study)) {
        index++;
      }
    }
    return index;
  }

  public void updateRemovedPatient(MediaSeriesGroup patient) {
    SwingUtilities.invokeLater(
        () -> {
          modelPatient.removeElement(patient);
          filtersByPatient.remove(patient);
          if (modelPatient.getSize() == 0) {
            koOpen.setVisible(false);
            resetFilter();
          }
          if (selectedPatient.isPatient(patient)) {
            selectedPatient.refreshLayout();
          }
        });
  }

  private void resetFilter() {
    filtersByPatient.clear();
    seriesFilter = new SeriesFilter();
    syncFilterToState(null);
  }

  public void updateRemovedStudy(StudyPane studyPane, MediaSeriesGroup study) {
    SwingUtilities.invokeLater(
        () -> {
          if (selectedPatient.isStudyVisible(study)) {
            selectedPatient.remove(studyPane);
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

  // ========== Thumbnail Size Management ==========

  public void updateThumbnailSize(int thumbnailSize) {
    updateDockableWidth(computeThumbnailPanelWidth(thumbnailSize));
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
      DicomModel.LOADING_EXECUTOR.execute(
          new LoadLocalDicom(files, recursive, model, OpeningViewer.ALL_PATIENTS));
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
      ViewCanvas<?> pane = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedViewCanvas();
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
