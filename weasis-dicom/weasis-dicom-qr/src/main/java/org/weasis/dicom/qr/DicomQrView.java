/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import com.formdev.flatlaf.FlatClientProperties;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;
import com.github.lgooddatepicker.optionalusertools.DateChangeListener;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.QueryRetrieveLevel;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.CheckBoxModel;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupCheckBoxMenu;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.CalendarUtil;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ImportDicom;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.PluginOpeningStrategy;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.RetrieveType;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode.WebType;
import org.weasis.dicom.explorer.rs.RsQueryParams;
import org.weasis.dicom.explorer.wado.DownloadManager;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.tool.DicomListener;
import org.weasis.dicom.util.DateUtil;

public class DicomQrView extends AbstractItemDialogPage implements ImportDicom {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomQrView.class);
  private DicomWebNode retrieveNode;

  public enum Period {
    ALL(Messages.getString("DicomQrView.all_dates"), null),

    TODAY(Messages.getString("DicomQrView.today"), LocalDate.now()),

    YESTERDAY(Messages.getString("DicomQrView.yesterday"), LocalDate.now().minusDays(1)),

    BEFORE_YESTERDAY(
        Messages.getString("DicomQrView.day_before_yest"), LocalDate.now().minusDays(2)),

    CUR_WEEK(
        Messages.getString("DicomQrView.this_week"),
        LocalDate.now().with(WeekFields.of(LocalUtil.getLocaleFormat()).dayOfWeek(), 1),
        LocalDate.now()),

    CUR_MONTH(
        Messages.getString("DicomQrView.this_month"),
        LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()),
        LocalDate.now()),

    CUR_YEAR(
        Messages.getString("DicomQrView.this_year"),
        LocalDate.now().with(TemporalAdjusters.firstDayOfYear()),
        LocalDate.now()),

    LAST_DAY(
        Messages.getString("DicomQrView.last_24h"), LocalDate.now().minusDays(1), LocalDate.now()),

    LAST_2_DAYS(
        Messages.getString("DicomQrView.last_2_d"), LocalDate.now().minusDays(2), LocalDate.now()),

    LAST_3_DAYS(
        Messages.getString("DicomQrView.last_3_d"), LocalDate.now().minusDays(3), LocalDate.now()),

    LAST_WEEK(
        Messages.getString("DicomQrView.last_w"), LocalDate.now().minusWeeks(1), LocalDate.now()),

    LAST_2_WEEKS(
        Messages.getString("DicomQrView.last_2_w"), LocalDate.now().minusWeeks(2), LocalDate.now()),

    LAST_MONTH(
        Messages.getString("DicomQrView.last_m"), LocalDate.now().minusMonths(1), LocalDate.now()),

    LAST_3_MONTHS(
        Messages.getString("DicomQrView.last_3_m"),
        LocalDate.now().minusMonths(3),
        LocalDate.now()),

    LAST_6_MONTHS(
        Messages.getString("DicomQrView.last_6_m"),
        LocalDate.now().minusMonths(6),
        LocalDate.now()),

    LAST_YEAR(
        Messages.getString("DicomQrView.last_year"),
        LocalDate.now().minusYears(1),
        LocalDate.now());

    private final String displayName;
    private final LocalDate start;
    private final LocalDate end;

    Period(String name, LocalDate date) {
      this.displayName = name;
      this.start = date;
      this.end = date;
    }

    Period(String name, LocalDate start, LocalDate end) {
      this.displayName = name;
      this.start = start;
      this.end = end;
    }

    public LocalDate getStart() {
      return start;
    }

    public LocalDate getEnd() {
      return end;
    }

    @Override
    public String toString() {
      return displayName;
    }

    public static Period getPeriod(String name) {
      if (StringUtil.hasText(name)) {
        try {
          return Period.valueOf(name);
        } catch (Exception e) {
          LOGGER.error("Cannot get Period from {}", name, e);
        }
      }
      return null;
    }
  }

  private static final String LAST_SEL_NODE = "lastSelNode";
  private static final String LAST_CALLING_NODE = "lastCallingNode";
  private static final String LAST_RETRIEVE_TYPE = "lastRetrieveType";
  private static final String LAST_RETRIEVE_LIMIT = "lastRetrieveLimit";
  static final File tempDir =
      FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "qr")); // NON-NLS

  private final JComboBox<AbstractDicomNode> comboDestinationNode = new JComboBox<>();
  private final JComboBox<SearchParameters> templateComboBox = new JComboBox<>();
  private final JTextField tfSearch = new JTextField(25);
  private final RetrieveTree tree = new RetrieveTree();

  private final JComboBox<TagW> comboTags =
      new JComboBox<>(
          TagD.getTagFromIDs(
              Tag.PatientName,
              Tag.PatientID,
              Tag.AccessionNumber,
              Tag.StudyID,
              Tag.StudyDescription,
              Tag.InstitutionName,
              Tag.ReferringPhysicianName,
              Tag.PerformingPhysicianName,
              Tag.NameOfPhysiciansReadingStudy));
  private final GroupCheckBoxMenu groupMod = new GroupCheckBoxMenu();
  private final DropDownButton modButton =
      new DropDownButton(
          "search_mod", // NON-NLS
          Messages.getString("DicomQrView.modalities"),
          GuiUtils.getDownArrowIcon(),
          groupMod) {
        @Override
        protected JPopupMenu getPopupMenu() {
          JPopupMenu menu =
              (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
          menu.setInvoker(this);
          return menu;
        }
      };
  private Period currentPeriod;
  private final GroupRadioMenu<Period> groupDate =
      new GroupRadioMenu<>() {
        @Override
        public void contentsChanged(ListDataEvent e) {
          super.contentsChanged(e);
          currentPeriod = getSelectedItem();
          if (currentPeriod != null) {
            startDatePicker.removeDateChangeListener(dateChangeListener);
            endDatePicker.removeDateChangeListener(dateChangeListener);
            startDatePicker.setDate(currentPeriod.getStart());
            endDatePicker.setDate(currentPeriod.getEnd());
            startDatePicker.addDateChangeListener(dateChangeListener);
            endDatePicker.addDateChangeListener(dateChangeListener);
          }
        }
      };
  private final DropDownButton dateButton =
      new DropDownButton(
          "search_date", // NON-NLS
          Messages.getString("DicomQrView.dates"),
          GuiUtils.getDownArrowIcon(),
          groupDate) {
        @Override
        protected JPopupMenu getPopupMenu() {
          JPopupMenu menu =
              (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
          menu.setInvoker(this);
          return menu;
        }
      };
  private final DateChangeListener dateChangeListener = a -> groupDate.setSelected(null);
  private final ActionListener destNodeListener = evt -> applySelectedArchive();
  private final ChangeListener queryListener = e -> dicomQuery();
  private final DatePicker startDatePicker = buildDatePicker();
  private final DatePicker endDatePicker = buildDatePicker();
  private final JComboBox<RetrieveType> comboDicomRetrieveType =
      new JComboBox<>(RetrieveType.values());
  private final JComboBox<AbstractDicomNode> comboCallingNode = new JComboBox<>();
  private final DicomListener dicomListener;
  private final ExecutorService executor =
      ThreadUtil.buildNewFixedThreadExecutor(3, "Dicom Q/R task"); // NON-NLS
  private final JSpinner limitSpinner = new JSpinner();
  private final JSpinner pageSpinner = new JSpinner();
  private QueryProcess process;
  private AuthMethod authMethod;
  private final CircularProgressBar progressBar = new CircularProgressBar();
  final JLabel lblDest =
      new JLabel(Messages.getString("DicomQrView.calling_node") + StringUtil.COLON);
  private final JLabel lblRetrieve =
      new JLabel(Messages.getString("DicomQrView.retrieve") + StringUtil.COLON);

  public DicomQrView() {
    super(Messages.getString("DicomQrView.title"));
    int limit =
        StringUtil.getInt(DicomQrFactory.IMPORT_PERSISTENCE.getProperty(LAST_RETRIEVE_LIMIT), 10);
    GuiUtils.setNumberModel(limitSpinner, limit, 0, 999, 5);
    GuiUtils.setNumberModel(pageSpinner, 1, 1, 99999, 1);
    SearchParameters.loadSearchParameters(templateComboBox);
    initGUI();
    initialize(true);

    DicomListener dcmListener = null;
    try {
      PluginOpeningStrategy openingStrategy =
          new PluginOpeningStrategy(DownloadManager.getOpeningViewer());
      openingStrategy.setResetVeto(true);
      DataExplorerView dicomView =
          org.weasis.core.ui.docking.UIManager.getExplorerplugin(DicomExplorer.NAME);
      if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel model) {
        DicomProgress progress = new DicomProgress();
        progress.addProgressListener(
            p -> {
              File current = p.getProcessedFile();
              if (current != null && p.getAttributes() == null) {
                LoadLocalDicom task =
                    new LoadLocalDicom(new File[] {current}, false, model, openingStrategy);
                DicomModel.LOADING_EXECUTOR.execute(task);
              }
            });
        dcmListener = new DicomListener(tempDir, progress);
      } else {
        dcmListener = new DicomListener(tempDir);
      }

    } catch (RuntimeException e) {
      LOGGER.error("Cannot start DICOM listener", e);
    }
    dicomListener = dcmListener;
  }

  public void initGUI() {
    JTabbedPane tabbedPane = new JTabbedPane();
    tabbedPane.setBorder(GuiUtils.getEmptyBorder(5));
    tabbedPane.putClientProperty(FlatClientProperties.TABBED_PANE_HAS_FULL_BORDER, true);
    JPanel sourcePanel =
        GuiUtils.getVerticalBoxLayoutPanel(
            getArchivePanel(), getCallingNodePanel(), GuiUtils.boxXLastElement(2));
    sourcePanel.setBorder(GuiUtils.getEmptyBorder(ITEM_SEPARATOR));
    tabbedPane.addTab(Messages.getString("dicom.source"), sourcePanel);
    tabbedPane.addTab(Messages.getString("search.criteria"), getSearchPanel());
    add(tabbedPane);

    add(GuiUtils.boxVerticalStrut(ITEM_SEPARATOR_LARGE));
    add(getCtrlSearchPanel());
    tree.setBorder(UIManager.getBorder("ScrollPane.border"));
    add(tree);
  }

  public JPanel getArchivePanel() {
    JLabel lblDest = new JLabel(Messages.getString("DicomQrView.arc") + StringUtil.COLON);
    AbstractDicomNode.addTooltipToComboList(comboDestinationNode);
    GuiUtils.setPreferredWidth(comboDestinationNode, 250, 150);

    comboDicomRetrieveType.setToolTipText(Messages.getString("DicomQrView.msg_sel_type"));
    return GuiUtils.getFlowLayoutPanel(
        ITEM_SEPARATOR_SMALL,
        ITEM_SEPARATOR,
        lblDest,
        comboDestinationNode,
        GuiUtils.boxHorizontalStrut(BLOCK_SEPARATOR),
        lblRetrieve,
        comboDicomRetrieveType);
  }

  public JPanel getCallingNodePanel() {
    GuiUtils.setPreferredWidth(comboCallingNode, 230, 150);
    AbstractDicomNode.addTooltipToComboList(comboCallingNode);

    final JButton btnGeneralOptions = new JButton(Messages.getString("DicomQrView.more_opt"));
    btnGeneralOptions.addActionListener(
        e -> {
          PreferenceDialog dialog = new PreferenceDialog(SwingUtilities.getWindowAncestor(this));
          dialog.showPage(
              org.weasis.dicom.explorer.Messages.getString("DicomNodeListView.node_list"));
          GuiUtils.showCenterScreen(dialog);
          initNodeList();
        });
    return GuiUtils.getFlowLayoutPanel(
        ITEM_SEPARATOR_SMALL,
        ITEM_SEPARATOR,
        lblDest,
        comboCallingNode,
        GuiUtils.boxHorizontalStrut(BLOCK_SEPARATOR),
        btnGeneralOptions);
  }

  public JPanel getCtrlSearchPanel() {
    JLabel labelLimit = new JLabel(Messages.getString("limit") + StringUtil.COLON);
    limitSpinner.setToolTipText(Messages.getString("no.limit"));

    JLabel labelPage = new JLabel(Messages.getString("page") + StringUtil.COLON);
    pageSpinner.addChangeListener(queryListener);
    progressBar.setEnabled(false);

    JButton searchBtn = new JButton(Messages.getString("DicomQrView.search"));
    searchBtn.setToolTipText(Messages.getString("DicomQrView.tips_dcm_query"));
    searchBtn.addActionListener(e -> dicomQuery());

    return GuiUtils.getFlowLayoutPanel(
        FlowLayout.LEADING,
        ITEM_SEPARATOR_SMALL,
        ITEM_SEPARATOR,
        labelLimit,
        limitSpinner,
        GuiUtils.boxHorizontalStrut(BLOCK_SEPARATOR),
        labelPage,
        pageSpinner,
        GuiUtils.boxHorizontalStrut(BLOCK_SEPARATOR),
        progressBar,
        GuiUtils.boxHorizontalStrut(BLOCK_SEPARATOR),
        searchBtn);
  }

  public JPanel getSearchPanel() {
    List<Object> list = Stream.of(Modality.values()).collect(Collectors.toList());
    list.set(0, Messages.getString("DicomQrView.all_mod"));
    groupMod.setModel(list, true, true);
    modButton.setToolTipText(Messages.getString("DicomQrView.select_mod"));

    Period[] listDate = {
      Period.ALL,
      Period.TODAY,
      Period.YESTERDAY,
      Period.BEFORE_YESTERDAY,
      Period.CUR_WEEK,
      Period.CUR_MONTH,
      Period.CUR_YEAR,
      Period.LAST_DAY,
      Period.LAST_2_DAYS,
      Period.LAST_3_DAYS,
      Period.LAST_WEEK,
      Period.LAST_2_WEEKS,
      Period.LAST_MONTH,
      Period.LAST_3_MONTHS,
      Period.LAST_6_MONTHS,
      Period.LAST_YEAR
    };
    ComboBoxModel<Period> dataModel = new DefaultComboBoxModel<>(listDate);
    groupDate.setModel(dataModel);

    JLabel labelFrom = new JLabel(Messages.getString("DicomQrView.from"));
    JLabel labelTo = new JLabel(Messages.getString("DicomQrView.to"));

    JPanel panel1 =
        GuiUtils.getFlowLayoutPanel(
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            modButton,
            GuiUtils.boxXLastElement(BLOCK_SEPARATOR),
            dateButton,
            GuiUtils.boxXLastElement(ITEM_SEPARATOR_LARGE),
            labelFrom,
            startDatePicker.getComponentDateTextField(),
            GuiUtils.boxXLastElement(ITEM_SEPARATOR),
            labelTo,
            endDatePicker.getComponentDateTextField());

    comboTags.setMaximumRowCount(15);
    GuiUtils.setPreferredWidth(comboTags, 230, 150);

    String buf =
        """
      <html>
        %s<br>
        &nbsp&nbsp&nbsp%s<br>
        &nbsp&nbsp&nbsp%s
      </html>
      """
            .formatted(
                Messages.getString("DicomQrView.tips_wildcard"),
                Messages.getString("DicomQrView.tips_star"),
                Messages.getString("DicomQrView.tips_question"));
    tfSearch.setToolTipText(buf);
    tfSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    JPanel panel2 =
        GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, comboTags, tfSearch);

    JButton clearBtn = new JButton(Messages.getString("DicomQrView.clear"));
    clearBtn.setToolTipText(Messages.getString("DicomQrView.clear_search"));
    clearBtn.addActionListener(e -> clearItems());

    JButton addButton = new JButton(Messages.getString("save"));
    JButton deleteButton = new JButton(Messages.getString("delete"));
    deleteButton.setToolTipText(Messages.getString("delete.the.selected.item"));
    deleteButton.addActionListener(
        evt -> {
          int response =
              JOptionPane.showConfirmDialog(
                  deleteButton,
                  String.format(
                      org.weasis.dicom.explorer.Messages.getString("AbstractDicomNode.delete_msg"),
                      templateComboBox.getSelectedItem()),
                  null,
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.WARNING_MESSAGE);

          if (response == 0) {
            templateComboBox.removeItemAt(templateComboBox.getSelectedIndex());
          }
        });
    addButton.addActionListener(
        evt -> {
          String message = Messages.getString("enter.a.name");
          String title = Messages.getString("search.template");

          String description =
              (String)
                  JOptionPane.showInputDialog(
                      addButton, message, title, JOptionPane.INFORMATION_MESSAGE, null, null, null);

          // description==null means the user canceled the input
          if (StringUtil.hasText(description)) {
            SearchParameters item = buildCurrentSearchParameters(description);
            templateComboBox.addItem(item);
            templateComboBox.setSelectedItem(item);
          }
        });

    templateComboBox.addItemListener(
        e -> {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            applyParametersFromSelected();
          }
        });

    JPanel panel3 =
        GuiUtils.getFlowLayoutPanel(
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            clearBtn,
            addButton,
            deleteButton,
            templateComboBox);
    JPanel searchPanel = GuiUtils.getVerticalBoxLayoutPanel(panel1, panel2, panel3);
    searchPanel.setBorder(GuiUtils.getEmptyBorder(ITEM_SEPARATOR));
    return searchPanel;
  }

  private void applyParametersFromSelected() {
    Object selectedItem = templateComboBox.getSelectedItem();
    if (selectedItem instanceof SearchParameters parameters) {
      for (DicomParam dicomParam : parameters.getParameters()) {
        if (dicomParam.getTag() == Tag.ModalitiesInStudy) {
          List<String> values =
              dicomParam.getValues() == null
                  ? Collections.emptyList()
                  : List.of(dicomParam.getValues());
          if (values.isEmpty()) {
            groupMod.getModelList().forEach(b -> b.setSelected(true));
          } else {
            for (CheckBoxModel box : groupMod.getModelList()) {
              if (box.getObject() instanceof Modality modality) {
                box.setSelected(values.contains(modality.name()));
              } else {
                box.setSelected(false);
              }
            }
          }
        } else if (dicomParam.getTag() == Tag.StudyDate) {
          String[] range = dicomParam.getValues()[0].split("-");
          LocalDate start = null;
          LocalDate end = null;
          if (range.length == 1) {
            if (dicomParam.getValues()[0].startsWith("-")) {
              end = DateUtil.getDicomDate(range[0]);
            } else {
              start = DateUtil.getDicomDate(range[0]);
            }
          } else if (range.length == 2) {
            start = DateUtil.getDicomDate(range[0]);
            end = DateUtil.getDicomDate(range[1]);
          }

          startDatePicker.setDate(start);
          endDatePicker.setDate(end);
        } else {
          comboTags.setSelectedItem(TagD.get(dicomParam.getTag()));
          tfSearch.setText(dicomParam.getValues()[0]);
        }
      }
      Period p = parameters.getPeriod();
      if (p != null) {
        groupDate.getModel().setSelectedItem(p);
      }
    }
  }

  private DatePicker buildDatePicker() {
    DatePicker picker = new DatePicker();
    DatePickerSettings settings = picker.getSettings();
    CalendarUtil.adaptCalendarColors(settings);

    JTextField textField = picker.getComponentDateTextField();
    settings.setFormatForDatesCommonEra(LocalUtil.getDateFormatter(FormatStyle.SHORT));
    settings.setFormatForDatesBeforeCommonEra(LocalUtil.getDateFormatter(FormatStyle.SHORT));
    GuiUtils.setPreferredWidth(textField, 145);
    picker.addDateChangeListener(dateChangeListener);

    textField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    JButton calendarButton = picker.getComponentToggleCalendarButton();
    calendarButton.setMargin(null);
    calendarButton.setText(null);
    calendarButton.setIcon(ResourceUtil.getIcon(OtherIcon.CALENDAR));
    calendarButton.setFocusPainted(true);
    calendarButton.setFocusable(true);
    calendarButton.revalidate();
    Arrays.stream(calendarButton.getMouseListeners()).forEach(calendarButton::removeMouseListener);
    calendarButton.addActionListener(e -> picker.openPopup());
    textField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, calendarButton);
    return picker;
  }

  private void clearItems() {
    tfSearch.setText(null);
    groupMod.selectAll();
    templateComboBox.setSelectedItem(null);
    startDatePicker.setDate(null);
    endDatePicker.setDate(null);
    currentPeriod = null;
    pageSpinner.removeChangeListener(queryListener);
    pageSpinner.setValue(1);
    pageSpinner.addChangeListener(queryListener);
  }

  private void dicomQuery() {
    stopCurrentProcess();
    SearchParameters searchParams = buildCurrentSearchParameters("custom"); // NON-NLS
    List<DicomParam> p = searchParams.getParameters();

    if (p.isEmpty() && (Integer) limitSpinner.getValue() < 1) {
      String message = Messages.getString("DicomQrView.msg_empty_query");
      int response =
          JOptionPane.showOptionDialog(
              WinUtil.getParentDialog(this),
              message,
              getTitle(),
              JOptionPane.YES_NO_OPTION,
              JOptionPane.WARNING_MESSAGE,
              null,
              null,
              null);
      if (response != 0) {
        return;
      }
    }

    AtomicBoolean running = new AtomicBoolean(true);

    AbstractDicomNode selectedItem = (AbstractDicomNode) comboDestinationNode.getSelectedItem();
    if (selectedItem instanceof final DefaultDicomNode node) {
      DefaultDicomNode callingNode = (DefaultDicomNode) comboCallingNode.getSelectedItem();

      // see http://dicom.nema.org/medical/dicom/current/output/html/part04.html#sect_C.6
      addReturnTags(p, CFind.PatientName);
      addReturnTags(p, CFind.PatientID);
      addReturnTags(p, CFind.PatientSex);
      addReturnTags(p, CFind.PatientBirthDate);
      addReturnTags(p, CFind.IssuerOfPatientID);

      addReturnTags(p, CFind.StudyInstanceUID);
      addReturnTags(p, CFind.StudyDescription);
      addReturnTags(p, CFind.StudyDate);
      addReturnTags(p, CFind.StudyTime);
      addReturnTags(p, CFind.AccessionNumber);
      addReturnTags(p, CFind.ReferringPhysicianName);
      addReturnTags(p, CFind.StudyID);
      addReturnTags(p, new DicomParam(Tag.InstitutionName));
      addReturnTags(p, new DicomParam(Tag.ModalitiesInStudy));
      addReturnTags(p, new DicomParam(Tag.NumberOfStudyRelatedSeries));
      addReturnTags(p, new DicomParam(Tag.NumberOfStudyRelatedInstances));

      AdvancedParams params = new AdvancedParams();
      ConnectOptions connectOptions = new ConnectOptions();
      connectOptions.setConnectTimeout(3000);
      connectOptions.setAcceptTimeout(5000);
      params.setConnectOptions(connectOptions);

      Runnable runnable =
          () -> {
            GuiExecutor.instance()
                .execute(
                    () -> {
                      progressBar.setEnabled(true);
                      progressBar.setIndeterminate(true);
                    });
            final DicomState state =
                CFind.process(
                    params,
                    callingNode.getDicomNodeWithOnlyAET(),
                    node.getDicomNode(),
                    (Integer) limitSpinner.getValue(),
                    QueryRetrieveLevel.STUDY,
                    p.toArray(new DicomParam[0]));

            if (running.get()) {
              GuiExecutor.instance()
                  .execute(
                      () -> {
                        progressBar.setEnabled(false);
                        progressBar.setIndeterminate(false);
                        displayResult(state);
                        if (state.getStatus() != Status.Success) {
                          LOGGER.error("Dicom cfind error: {}", state.getMessage());
                          JOptionPane.showMessageDialog(
                              this, state.getMessage(), null, JOptionPane.ERROR_MESSAGE);
                        }
                      });
            }
          };
      process = new QueryProcess(runnable, "DICOM C-FIND", running); // $NON-NLS-1$
      process.start();
    } else if (selectedItem instanceof final DicomWebNode node) {
      AuthMethod auth = AuthenticationPersistence.getAuthMethod(node.getAuthMethodUid());
      if (!OAuth2ServiceFactory.noAuth.equals(auth)) {
        String oldCode = auth.getCode();
        authMethod = auth;
        if (authMethod.getToken() == null) {
          return;
        }
        if (!Objects.equals(oldCode, authMethod.getCode())) {
          AuthenticationPersistence.saveMethod();
        }
      }

      Properties props = new Properties();
      props.setProperty(RsQueryParams.P_DICOMWEB_URL, node.getUrl().toString());
      Integer limit = (Integer) limitSpinner.getValue();
      Integer page = (Integer) pageSpinner.getValue();
      if (limit > 0) {
        props.setProperty(
            RsQueryParams.P_PAGE_EXT,
            String.format("&limit=%d&offset=%d", limit, (page - 1) * limit)); // NON-NLS
      }
      // props.setProperty(RsQueryParams.P_QUERY_EXT, "&includedefaults=false");
      this.retrieveNode = node;
      RsQuery rsquery = new RsQuery(new DicomModel(), props, p, authMethod, node.getHeaders());
      Runnable runnable =
          () -> {
            try {
              GuiExecutor.instance()
                  .execute(
                      () -> {
                        progressBar.setEnabled(true);
                        progressBar.setIndeterminate(true);
                      });
              rsquery.call();
              if (running.get()) {
                GuiExecutor.instance()
                    .execute(
                        () -> {
                          progressBar.setEnabled(false);
                          progressBar.setIndeterminate(false);
                          tree.setRetrieveTreeModel(new RetrieveTreeModel(rsquery.getDicomModel()));
                          tree.revalidate();
                          tree.repaint();
                        });
              }
            } catch (Exception e) {
              LOGGER.error("", e);
            }
          };
      process = new QueryProcess(runnable, "QIDO-RS", running); // $NON-NLS-1$
      process.start();
    }
  }

  private static void addReturnTags(List<DicomParam> list, DicomParam p) {
    if (list.stream().noneMatch(d -> d.getTag() == p.getTag())) {
      list.add(p);
    }
  }

  private void displayResult(DicomState state) {
    List<Attributes> items = state.getDicomRSP();
    DicomModel dicomModel = new DicomModel();
    if (items != null) {
      for (int i = 0; i < items.size(); i++) {
        Attributes item = items.get(i);
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("===========================================");
          LOGGER.trace("CFind Item {}", (i + 1));
          LOGGER.trace("===========================================");
          LOGGER.trace("{}", item.toString(100, 150));
        }

        RsQuery.populateDicomModel(dicomModel, item);
      }
    }
    tree.setRetrieveTreeModel(new RetrieveTreeModel(dicomModel));
    tree.revalidate();
    tree.repaint();
  }

  protected void initialize(boolean firstTime) {
    if (firstTime) {
      initNodeList();
    }
    clearItems();
  }

  private void initNodeList() {
    comboCallingNode.removeAllItems();
    AbstractDicomNode.loadDicomNodes(
        comboCallingNode,
        AbstractDicomNode.Type.DICOM_CALLING,
        AbstractDicomNode.UsageType.RETRIEVE);
    restoreNodeSelection(comboCallingNode.getModel(), LAST_CALLING_NODE);

    comboDestinationNode.removeActionListener(destNodeListener);
    comboDestinationNode.removeAllItems();
    AbstractDicomNode.loadDicomNodes(
        comboDestinationNode, AbstractDicomNode.Type.DICOM, UsageType.RETRIEVE);
    AbstractDicomNode.loadDicomNodes(
        comboDestinationNode, AbstractDicomNode.Type.WEB, UsageType.RETRIEVE, WebType.QIDORS);
    restoreNodeSelection(comboDestinationNode.getModel(), LAST_SEL_NODE);
    String lastType = DicomQrFactory.IMPORT_PERSISTENCE.getProperty(LAST_RETRIEVE_TYPE);
    if (lastType != null) {
      try {
        comboDicomRetrieveType.setSelectedItem(RetrieveType.valueOf(lastType));
      } catch (Exception e) {
        // Do nothing
      }
    }
    applySelectedArchive();
    comboDestinationNode.addActionListener(destNodeListener);
  }

  private void applySelectedArchive() {
    Object selectedItem = comboDestinationNode.getSelectedItem();
    boolean dcmOption = selectedItem instanceof DefaultDicomNode;
    lblDest.setEnabled(dcmOption);
    lblRetrieve.setEnabled(dcmOption);
    comboDicomRetrieveType.setEnabled(dcmOption);
    comboCallingNode.setEnabled(dcmOption);
    pageSpinner.setEnabled(!dcmOption);
    pageSpinner.removeChangeListener(queryListener);
    pageSpinner.setValue(1);
    pageSpinner.addChangeListener(queryListener);
    stopCurrentProcess();
  }

  public void resetSettingsToDefault() {
    initialize(false);
  }

  private SearchParameters buildCurrentSearchParameters(String title) {
    SearchParameters p = new SearchParameters(title);
    // Get value in text field
    String sTagValue = tfSearch.getText();
    TagW item = (TagW) comboTags.getSelectedItem();
    if (item != null) {
      p.getParameters().add(new DicomParam(item.getId(), sTagValue));
    }

    // Get modalities selection
    String[] list = null;
    if (groupMod.getModelList().stream().anyMatch(c -> !c.isSelected())) {
      list =
          groupMod.getModelList().stream()
              .filter(c -> c.isSelected() && c.getObject() instanceof Modality)
              .map(c -> ((Modality) c.getObject()).name())
              .toArray(String[]::new);
    }
    p.getParameters().add(new DicomParam(Tag.ModalitiesInStudy, list));

    LocalDate sDate = startDatePicker.getDate();
    LocalDate eDate = endDatePicker.getDate();
    String range = "";
    if (sDate != null || eDate != null) {
      range = TagD.formatDicomDate(sDate) + "-" + TagD.formatDicomDate(eDate);
    }
    p.getParameters().add(new DicomParam(Tag.StudyDate, range));
    p.setPeriod(currentPeriod);
    return p;
  }

  public void applyChange() {
    nodeSelectionPersistence(
        (AbstractDicomNode) comboDestinationNode.getSelectedItem(), LAST_SEL_NODE);
    nodeSelectionPersistence(
        (AbstractDicomNode) comboCallingNode.getSelectedItem(), LAST_CALLING_NODE);
    RetrieveType type = (RetrieveType) comboDicomRetrieveType.getSelectedItem();
    if (type != null) {
      DicomQrFactory.IMPORT_PERSISTENCE.setProperty(LAST_RETRIEVE_TYPE, type.name());
    }

    DicomQrFactory.IMPORT_PERSISTENCE.setProperty(
        LAST_RETRIEVE_LIMIT, String.valueOf(limitSpinner.getValue()));

    saveTemplates(templateComboBox);
  }

  public void saveTemplates(JComboBox<? extends SearchParameters> comboBox) {
    XMLStreamWriter writer = null;
    XMLOutputFactory factory = XMLOutputFactory.newInstance();
    final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
    try {
      writer =
          factory.createXMLStreamWriter(
              new FileOutputStream(
                  new File(BundlePreferences.getDataFolder(context), SearchParameters.FILENAME)),
              "UTF-8"); // NON-NLS

      writer.writeStartDocument("UTF-8", "1.0"); // NON-NLS
      writer.writeStartElement(SearchParameters.T_NODES);
      for (int i = 0; i < comboBox.getItemCount(); i++) {
        SearchParameters node = comboBox.getItemAt(i);
        writer.writeStartElement(SearchParameters.T_NODE);
        node.saveSearchParameters(writer);
        writer.writeEndElement();
      }
      writer.writeEndElement();
      writer.writeEndDocument();
      writer.flush();
    } catch (Exception e) {
      LOGGER.error("Error on writing DICOM node file", e);
    } finally {
      FileUtil.safeClose(writer);
    }
  }

  private void nodeSelectionPersistence(AbstractDicomNode node, String key) {
    if (node != null) {
      DicomQrFactory.IMPORT_PERSISTENCE.setProperty(key, node.getDescription());
    }
  }

  private void restoreNodeSelection(ComboBoxModel<AbstractDicomNode> model, String key) {
    if (model != null) {
      String desc = DicomQrFactory.IMPORT_PERSISTENCE.getProperty(key);
      if (StringUtil.hasText(desc)) {
        for (int i = 0; i < model.getSize(); i++) {
          if (desc.equals(model.getElementAt(i).getDescription())) {
            model.setSelectedItem(model.getElementAt(i));
            break;
          }
        }
      }
    }
  }

  protected void updateChanges() {}

  protected void stopCurrentProcess() {
    final QueryProcess t = process;
    if (t != null) {
      process = null;
      t.running.set(false);
      t.interrupt();
    }
    GuiExecutor.instance()
        .execute(
            () -> {
              progressBar.setEnabled(false);
              progressBar.setIndeterminate(false);
              tree.setRetrieveTreeModel(new RetrieveTreeModel());
              tree.revalidate();
              tree.repaint();
            });
  }

  @Override
  public void closeAdditionalWindow() {
    applyChange();
    executor.shutdown();
  }

  @Override
  public void resetToDefaultValues() {}

  private List<String> getCheckedStudies(TreePath[] paths) {
    List<String> studies = new ArrayList<>();
    for (TreePath treePath : paths) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
      if (node.getUserObject() instanceof MediaSeriesGroup study) {
        String uid = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
        if (StringUtil.hasText(uid)) {
          studies.add(uid);
        }
      }
    }
    return studies;
  }

  @Override
  public void importDICOM(DicomModel explorerDcmModel, JProgressBar info) {
    List<String> studies = getCheckedStudies(tree.getCheckboxTree().getCheckingPaths());
    if (!studies.isEmpty()) {
      Object selectedItem = getComboDestinationNode().getSelectedItem();
      if (selectedItem instanceof DicomWebNode webNode && webNode.getWebType() == WebType.QIDORS) {
        List<AbstractDicomNode> webNodes =
            AbstractDicomNode.loadDicomNodes(
                AbstractDicomNode.Type.WEB, AbstractDicomNode.UsageType.RETRIEVE, WebType.WADORS);
        String host = RetrieveTask.getHostname(((DicomWebNode) selectedItem).getUrl().getHost());
        String m1 = Messages.getString("DicomQrView.no.url.matches.with.the.qido");
        DicomWebNode wnode = RetrieveTask.getWadoUrl(this, host, webNodes, m1);
        if (wnode != null) {
          this.retrieveNode = wnode;
        }
      }
      executor.execute(new RetrieveTask(studies, explorerDcmModel, this));
    }
  }

  public JComboBox<AbstractDicomNode> getComboDestinationNode() {
    return comboDestinationNode;
  }

  public JComboBox<RetrieveType> getComboDicomRetrieveType() {
    return comboDicomRetrieveType;
  }

  public JComboBox<AbstractDicomNode> getComboCallingNode() {
    return comboCallingNode;
  }

  public DicomListener getDicomListener() {
    return dicomListener;
  }

  public DicomModel getDicomModel() {
    return tree.getRetrieveTreeModel().getDicomModel();
  }

  public DicomWebNode getRetrieveNode() {
    return retrieveNode;
  }

  public AuthMethod getAuthMethod() {
    return authMethod;
  }

  static class QueryProcess extends Thread {
    AtomicBoolean running;

    public QueryProcess(Runnable runnable, String name, AtomicBoolean running) {
      super(runnable, name);
      this.running = running;
    }
  }
}
