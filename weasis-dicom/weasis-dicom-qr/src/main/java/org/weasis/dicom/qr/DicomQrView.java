package org.weasis.dicom.qr;
/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.DropDownLabel;
import org.weasis.core.api.gui.util.GroupCheckBoxMenu;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.ImportDicom;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.RetrieveType;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.explorer.wado.LoadRemoteDicomManifest;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.op.CGet;
import org.weasis.dicom.op.CMove;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.qr.manisfest.ManifestBuilder;
import org.weasis.dicom.tool.DicomListener;

import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings.DateArea;
import com.github.lgooddatepicker.optionalusertools.DateChangeListener;

public class DicomQrView extends AbstractItemDialogPage implements ImportDicom {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomQrView.class);

    public enum Period {
        ALL("All dates", null),

        TODAY("Today", LocalDate.now()),

        YESTERDAY("Yesterday", LocalDate.now().minusDays(1)),

        BEFORE_YESTERDAY("Day before yesterday", LocalDate.now().minusDays(2)),

        CUR_WEEK("This week", LocalDate.now().with(WeekFields.of(LocalUtil.getLocaleFormat()).dayOfWeek(), 1),
                        LocalDate.now()),

        CUR_MONTH("This month", LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), LocalDate.now()),

        CUR_YEAR("This year", LocalDate.now().with(TemporalAdjusters.firstDayOfYear()), LocalDate.now()),

        LAST_DAY("Last 24 hours", LocalDate.now().minusDays(1), LocalDate.now()),

        LAST_2_DAYS("Last 2 days", LocalDate.now().minusDays(2), LocalDate.now()),

        LAST_3_DAYS("Last 3 days", LocalDate.now().minusDays(3), LocalDate.now()),

        LAST_WEEK("Last week", LocalDate.now().minusWeeks(1), LocalDate.now()),

        LAST_2_WEEKS("Last 2 weeks", LocalDate.now().minusWeeks(2), LocalDate.now()),

        LAST_MONTH("Last month", LocalDate.now().minusMonths(1), LocalDate.now()),

        LAST_3_MONTHS("Last 3 months", LocalDate.now().minusMonths(3), LocalDate.now()),

        LAST_6_MONTHS("Last 6 months", LocalDate.now().minusMonths(6), LocalDate.now()),

        LAST_YEAR("Last year", LocalDate.now().minusYears(1), LocalDate.now());

        private final String displayName;
        private final LocalDate start;
        private final LocalDate end;

        private Period(String name, LocalDate date) {
            this.displayName = name;
            this.start = date;
            this.end = date;
        }

        private Period(String name, LocalDate start, LocalDate end) {
            this.displayName = name;
            this.start = start;
            this.end = end;
        }

        public String getDisplayName() {
            return displayName;
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
    }

    private static final String LAST_SEL_NODE = "lastSelNode"; //$NON-NLS-1$
    private static final String LAST_CALLING_NODE = "lastCallingNode"; //$NON-NLS-1$
    private static final String LAST_RETRIEVE_TYPE = "lastRetrieveType"; //$NON-NLS-1$
    private static final File tempDir = FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "qr")); //$NON-NLS-1$ //$NON-NLS-2$

    private final Border spaceY = BorderFactory.createEmptyBorder(10, 3, 0, 3);

    private final JPanel panelBase = new JPanel();
    private final JPanel panelGroup = new JPanel();
    private final JComboBox<AbstractDicomNode> comboNode = new JComboBox<>();
    private final JTextField tfSearch = new JTextField();
    private final DicomModel dicomModel = new DicomModel();
    private final RetrieveTree tree = new RetrieveTree(dicomModel);
    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("rootNode", true); //$NON-NLS-1$

    private final JComboBox<TagW> comboTags = new JComboBox<>(TagD.getTagFromIDs(Tag.PatientName, Tag.PatientID,
        Tag.AccessionNumber, Tag.StudyID, Tag.StudyDescription, Tag.InstitutionName, Tag.ReferringPhysicianName,
        Tag.PerformingPhysicianName, Tag.NameOfPhysiciansReadingStudy));
    private final GroupCheckBoxMenu groupMod = new GroupCheckBoxMenu();
    private final DropDownButton modButton =
        new DropDownButton("search_mod", new DropDownLabel("Modalities", panelGroup), groupMod) {
            @Override
            protected JPopupMenu getPopupMenu() {
                JPopupMenu menu = (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
                menu.setInvoker(this);
                return menu;
            }
        };
    private final GroupRadioMenu<Period> groupDate = new GroupRadioMenu<Period>() {
        @Override
        public void contentsChanged(ListDataEvent e) {
            super.contentsChanged(e);
            Period p = getSelectedItem();
            if (p != null) {
                startDatePicker.removeDateChangeListener(dateChangeListener);
                endDatePicker.removeDateChangeListener(dateChangeListener);
                startDatePicker.setDate(p.getStart());
                endDatePicker.setDate(p.getEnd());
                startDatePicker.addDateChangeListener(dateChangeListener);
                endDatePicker.addDateChangeListener(dateChangeListener);
            }
        }
    };
    private final DropDownButton dateButton =
        new DropDownButton("search_date", new DropDownLabel("Dates", panelGroup), groupDate) {
            @Override
            protected JPopupMenu getPopupMenu() {
                JPopupMenu menu = (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
                menu.setInvoker(this);
                return menu;
            }
        };
    private final DateChangeListener dateChangeListener = a -> groupDate.setSelected(null);
    private final ActionListener destNodeListener = evt -> applySelectedArchive();
    private final DatePicker startDatePicker = buildDatePicker();
    private final DatePicker endDatePicker = buildDatePicker();
    JComboBox<RetrieveType> comboDicomRetrieveType = new JComboBox<>(RetrieveType.values());
    JComboBox<AbstractDicomNode> comboCallingNode = new JComboBox<>();
    private final DicomListener dicomListener;

    public DicomQrView() {
        super(Messages.getString("DicomQrView.title")); //$NON-NLS-1$
        initGUI();
        tree.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, "Result",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, FontTools.getFont12Bold(), Color.GRAY)));
        add(tree, BorderLayout.CENTER);
        initialize(true);

        DicomListener dcmListener = null;
        try {
            dcmListener = new DicomListener(tempDir);
        } catch (IOException e) {
            LOGGER.error("Cannot creast DICOM listener", e);
        }
        dicomListener = dcmListener;
    }

    public void initGUI() {
        setLayout(new BorderLayout());
        panelBase.setLayout(new BoxLayout(panelBase, BoxLayout.Y_AXIS));
        panelBase.add(getArchivePanel());
        panelBase.add(getCallingNodePanel());
        panelBase.add(getSearchPanel());
        panelBase.add(getCtrlSearchPanel());

        add(panelBase, BorderLayout.NORTH);
    }

    public JPanel getArchivePanel() {
        final JPanel sPanel = new JPanel();
        sPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        sPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));
        JLabel lblDest = new JLabel(Messages.getString("DicomQrView.arc") + StringUtil.COLON); //$NON-NLS-1$
        sPanel.add(lblDest);
        JMVUtils.setPreferredWidth(comboNode, 185, 185);
        AbstractDicomNode.addTooltipToComboList(comboNode);
        sPanel.add(comboNode);

        sPanel.add(Box.createHorizontalStrut(10));
        JLabel lblTetrieve = new JLabel("Retrieve" + StringUtil.COLON);
        sPanel.add(lblTetrieve);
        comboDicomRetrieveType.setToolTipText("Select the retrieve type");
        sPanel.add(comboDicomRetrieveType);
        return sPanel;
    }

    public JPanel getCallingNodePanel() {
        final JPanel sPanel = new JPanel();
        sPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        sPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));
        final JLabel lblDest = new JLabel("Calling Node" + StringUtil.COLON);
        sPanel.add(lblDest);

        JMVUtils.setPreferredWidth(comboCallingNode, 185, 185);
        AbstractDicomNode.addTooltipToComboList(comboCallingNode);
        sPanel.add(comboCallingNode);

        sPanel.add(Box.createHorizontalStrut(10));
        final JButton btnGerenralOptions = new JButton("More Options");
        btnGerenralOptions.setAlignmentX(Component.LEFT_ALIGNMENT);
        sPanel.add(btnGerenralOptions);
        btnGerenralOptions.addActionListener(e -> {
            PreferenceDialog dialog = new PreferenceDialog(SwingUtilities.getWindowAncestor(this));
            dialog.showPage(org.weasis.dicom.explorer.Messages.getString("DicomNodeListView.node_list"));
            JMVUtils.showCenterScreen(dialog);
            initNodeList();
        });
        return sPanel;
    }

    public JPanel getCtrlSearchPanel() {
        final JPanel panel5 = new JPanel();
        panel5.setAlignmentY(Component.TOP_ALIGNMENT);
        panel5.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel5.setLayout(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panel5.add(Box.createHorizontalStrut(350));
        JButton clearBtn = new JButton("Clear");
        clearBtn.setToolTipText("Clear the search parameters");
        clearBtn.addActionListener(e -> clearItems());
        panel5.add(clearBtn);
        JButton searchBtn = new JButton("Search");
        searchBtn.setToolTipText("Make a DICOM query");
        searchBtn.addActionListener(e -> cfind());
        panel5.add(searchBtn);
        return panel5;
    }

    public JPanel getSearchPanel() {
        final JPanel sPanel = new JPanel();
        sPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        sPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sPanel.setLayout(new BoxLayout(sPanel, BoxLayout.Y_AXIS));
        sPanel.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, "Search",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, FontTools.getFont12Bold(), Color.GRAY)));

        panelGroup.setLayout(new FlowLayout(FlowLayout.LEFT, 7, 3));

        List<Object> list = Stream.of(Modality.values()).collect(Collectors.toList());
        list.set(0, "All modalities");
        groupMod.setModel(list, true, true);

        modButton.setToolTipText("Select modality types");
        panelGroup.add(modButton);
        panelGroup.add(Box.createHorizontalStrut(10));

        Period[] listDate =
            { Period.ALL, Period.TODAY, Period.YESTERDAY, Period.BEFORE_YESTERDAY, Period.CUR_WEEK, Period.CUR_MONTH,
                Period.CUR_YEAR, Period.LAST_DAY, Period.LAST_2_DAYS, Period.LAST_3_DAYS, Period.LAST_WEEK,
                Period.LAST_2_WEEKS, Period.LAST_MONTH, Period.LAST_3_MONTHS, Period.LAST_6_MONTHS, Period.LAST_YEAR };
        ComboBoxModel<Period> dataModel = new DefaultComboBoxModel<>(listDate);
        groupDate.setModel(dataModel);

        panelGroup.add(dateButton);
        panelGroup.add(new JLabel("From"));
        panelGroup.add(startDatePicker);
        panelGroup.add(new JLabel("To"));
        panelGroup.add(endDatePicker);
        sPanel.add(panelGroup);

        final JPanel panel4 = new JPanel();
        panel4.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));
        panel4.add(comboTags);
        comboTags.setMaximumRowCount(15);
        // comboTags.setFont(FontTools.getFont11());
        JMVUtils.setPreferredWidth(comboTags, 180, 180);
        // Update UI before adding the Tooltip feature in the combobox list
        comboTags.updateUI();
        JMVUtils.addTooltipToComboList(comboTags);

        StringBuilder buf = new StringBuilder("<html>");
        buf.append("This field supports two wildcard characters: * and ?");
        buf.append("<br>&nbsp&nbsp&nbsp");
        buf.append("* matches zero or more non-space characters");
        buf.append("<br>&nbsp&nbsp&nbsp");
        buf.append("? matches exactly one non-space character");
        buf.append("</html>");
        tfSearch.setToolTipText(buf.toString());
        JMVUtils.setPreferredWidth(tfSearch, 370, 100);
        panel4.add(tfSearch);
        sPanel.add(panel4);

        return sPanel;
    }

    private DatePicker buildDatePicker() {
        DatePicker d = new DatePicker();
        d.getSettings().setFontInvalidDate(FontTools.getFont11());
        d.getSettings().setFontValidDate(FontTools.getFont11());
        d.getSettings().setFontVetoedDate(FontTools.getFont11());
        d.getSettings().setColor(DateArea.TextFieldBackgroundValidDate, tfSearch.getBackground());
        d.getSettings().setColor(DateArea.DatePickerTextValidDate, tfSearch.getForeground());
        d.getSettings().setColor(DateArea.TextFieldBackgroundDisallowedEmptyDate, tfSearch.getBackground());

        d.getSettings().setColor(DateArea.TextFieldBackgroundInvalidDate, tfSearch.getBackground());
        // d.getSettings().setColor(DateArea.DatePickerTextInvalidDate, tfSearch.getForeground());

        d.getSettings().setColor(DateArea.TextFieldBackgroundVetoedDate, tfSearch.getBackground());
        // d.getSettings().setColor(DateArea.DatePickerTextVetoedDate, tfSearch.getForeground());

        Color btnBack = d.getComponentToggleCalendarButton().getBackground();
        d.getSettings().setColor(DateArea.BackgroundOverallCalendarPanel, tfSearch.getBackground());
        d.getSettings().setColor(DateArea.BackgroundMonthAndYearNavigationButtons, btnBack);
        d.getSettings().setColor(DateArea.BackgroundMonthAndYearMenuButtons, btnBack);
        d.getSettings().setColor(DateArea.CalendarDefaultBackgroundHighlightedDates, tfSearch.getForeground());
        // d.getSettings().setColor(DateArea.CalendarBackgroundVetoedDates, Color.MAGENTA);
        d.getSettings().setColor(DateArea.BackgroundClearButton, btnBack);
        d.getSettings().setColor(DateArea.BackgroundTodayButton, btnBack);
        d.getSettings().setColor(DateArea.BackgroundTopLeftLabelAboveWeekNumbers, btnBack);

        d.getSettings().setColorBackgroundWeekNumberLabels(Color.ORANGE, true);

        // d.getSettings().setVisibleNextMonthButton(false);
        // d.getSettings().setVisibleNextYearButton(false);
        d.getSettings().setFormatForDatesCommonEra(LocalUtil.getDateFormatter());
        d.getSettings().setFormatForDatesBeforeCommonEra(LocalUtil.getDateFormatter());
        // JMVUtils.setPreferredWidth(d.getComponentDateTextField(), 95);
        JMVUtils.setPreferredWidth(d.getComponentToggleCalendarButton(), 35);
        d.addDateChangeListener(dateChangeListener);
        return d;
    }

    private void clearItems() {
        tfSearch.setText(null);
        groupMod.selectAll();
        startDatePicker.setDate(null);
        endDatePicker.setDate(null);
    }

    private void cfind() {
        SearchParameters searchParams = buildCurrentSearchParameters();
        List<DicomParam> p = searchParams.getParameters();
        // Clear model
        dicomModel.dispose();

        if (p.isEmpty()) {
            String message = "Do you really want to execute a query with empty parameters ?";
            int response = JOptionPane.showOptionDialog(this, message, getTitle(), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE, null, null, null);
            if (response != 0) {
                return;
            }
        }

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

        AbstractDicomNode selectedItem = (AbstractDicomNode) comboNode.getSelectedItem();
        AbstractDicomNode callingNode = (AbstractDicomNode) comboCallingNode.getSelectedItem();
        if (selectedItem instanceof DefaultDicomNode && callingNode instanceof DefaultDicomNode) {
            final DefaultDicomNode node = (DefaultDicomNode) selectedItem;
            AdvancedParams params = new AdvancedParams();
            ConnectOptions connectOptions = new ConnectOptions();
            connectOptions.setConnectTimeout(3000);
            connectOptions.setAcceptTimeout(5000);
            params.setConnectOptions(connectOptions);
            final DicomState state = CFind.process(params, ((DefaultDicomNode) callingNode).getDicomNode(),
                node.getDicomNode(), p.toArray(new DicomParam[p.size()]));
            if (state.getStatus() == Status.Success) {
                displayResult(state);
            } else {
                LOGGER.error("Dicom cfind error: {}", state.getMessage()); //$NON-NLS-1$
                GuiExecutor.instance().execute(() -> JOptionPane.showMessageDialog(panelBase, state.getMessage(), null,
                    JOptionPane.ERROR_MESSAGE));
            }
        } else if (selectedItem instanceof DicomWebNode) {
            throw new IllegalAccessError("Not implemented yet");
        }
    }

    private static void addReturnTags(List<DicomParam> list, DicomParam p) {
        if (!list.stream().anyMatch(d -> d.getTag() == p.getTag())) {
            list.add(p);
        }
    }

    private void displayResult(DicomState state) {
        List<Attributes> items = state.getDicomRSP();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                Attributes item = items.get(i);
                LOGGER.trace("===========================================");
                LOGGER.trace("CFind Item {}", (i + 1));
                LOGGER.trace("===========================================");
                LOGGER.trace("{}", item.toString(100, 150));

                String patientPseudoUID = DicomMediaUtils.buildPatientPseudoUID(item.getString(Tag.PatientID),
                    item.getString(Tag.IssuerOfPatientID), item.getString(Tag.PatientName));
                MediaSeriesGroup patient = dicomModel.getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
                if (patient == null) {
                    patient = new MediaSeriesGroupNode(TagW.PatientPseudoUID, patientPseudoUID,
                        DicomModel.patient.getTagView()) {
                        @Override
                        public String toString() {
                            StringBuilder buf = new StringBuilder(getDisplayValue(this, Tag.PatientName));
                            buf.append(" [");
                            buf.append(getDisplayValue(this, Tag.PatientID));
                            buf.append("] ");
                            buf.append(getDisplayValue(this, Tag.PatientBirthDate));
                            buf.append(" ");
                            buf.append(getDisplayValue(this, Tag.PatientSex));
                            return buf.toString();
                        }
                    };
                    DicomMediaUtils.writeMetaData(patient, item);
                    dicomModel.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
                }

                String studyUID = item.getString(Tag.StudyInstanceUID);
                MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, studyUID);
                if (study == null) {
                    study =
                        new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.getTagView()) {
                            @Override
                            public String toString() {
                                StringBuilder buf = new StringBuilder(getDisplayValue(this, Tag.StudyDescription));
                                buf.append(" [");
                                buf.append(getDisplayValue(this, Tag.ModalitiesInStudy));
                                buf.append("] ");
                                LocalDateTime studyDate = TagD.dateTime(Tag.StudyDate, Tag.StudyTime, this);
                                if (studyDate != null) {
                                    buf.append(TagUtil.formatDateTime(studyDate));
                                    buf.append(" ");
                                }
                                buf.append(getDisplayValue(this, Tag.AccessionNumber));
                                return buf.toString();
                            }
                        };
                    DicomMediaUtils.writeMetaData(study, item);
                    dicomModel.addHierarchyNode(patient, study);
                }
            }
        }

        tree.setCheckTreeModel(new RetrieveTreeModel(dicomModel));
        tree.revalidate();
        tree.repaint();
    }

    private String getDisplayValue(MediaSeriesGroupNode node, int tagID) {
        TagW tag = TagD.get(tagID);
        if (tag != null) {
            Object value = node.getTagValue(tag);
            if (value != null) {
                return tag.getFormattedTagValue(value, null);
            }
        }
        return StringUtil.EMPTY_STRING;

    }

    protected void initialize(boolean afirst) {
        if (afirst) {
            initNodeList();
        }
    }

    private void initNodeList() {
        comboCallingNode.removeAllItems();
        AbstractDicomNode.loadDicomNodes(comboCallingNode, AbstractDicomNode.Type.DICOM_CALLING,
            AbstractDicomNode.UsageType.RETRIEVE);
        restoreNodeSelection(comboCallingNode.getModel(), LAST_CALLING_NODE);

        comboNode.removeActionListener(destNodeListener);
        comboNode.removeAllItems();
        AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.DICOM, UsageType.RETRIEVE);
        // AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.WEB, UsageType.RETRIEVE);
        restoreNodeSelection(comboNode.getModel(), LAST_SEL_NODE);
        String lastType = DicomQrFactory.IMPORT_PERSISTENCE.getProperty(LAST_RETRIEVE_TYPE);
        if (lastType != null) {
            try {
                comboDicomRetrieveType.setSelectedItem(RetrieveType.valueOf(lastType));
            } catch (Exception e) {
                // Do nothing
            }
        }
        applySelectedArchive();
        comboNode.addActionListener(destNodeListener);
    }

    private void applySelectedArchive() {
        Object selectedItem = comboNode.getSelectedItem();
        boolean dcmOption = selectedItem instanceof DefaultDicomNode;
        comboDicomRetrieveType.setEnabled(dcmOption);
        comboCallingNode.setEnabled(dcmOption);
    }

    public void resetSettingsToDefault() {
        initialize(false);
    }

    private SearchParameters buildCurrentSearchParameters() {
        SearchParameters p = new SearchParameters("current");
        // Get value in text field
        String sTagValue = tfSearch.getText();
        TagW item = (TagW) comboTags.getSelectedItem();
        if (StringUtil.hasText(sTagValue) && item != null) {
            p.getParameters().add(new DicomParam(item.getId(), sTagValue));
        }

        // Get modalities selection
        if (groupMod.getModelList().stream().anyMatch(c -> !c.isSelected())) {
            p.getParameters()
                .add(new DicomParam(Tag.ModalitiesInStudy,
                    groupMod.getModelList().stream().filter(c -> c.isSelected() && c.getObject() instanceof Modality)
                        .map(c -> ((Modality) c.getObject()).name()).toArray(String[]::new)));
        }

        LocalDate sDate = startDatePicker.getDate();
        LocalDate eDate = endDatePicker.getDate();
        if (sDate != null || eDate != null) {
            StringBuilder range = new StringBuilder();
            range.append(TagD.formatDicomDate(sDate));
            range.append("-");
            range.append(TagD.formatDicomDate(eDate));
            p.getParameters().add(new DicomParam(Tag.StudyDate, range.toString()));
        }
        return p;
    }

    public void applyChange() {
        nodeSelectionPersistence((AbstractDicomNode) comboNode.getSelectedItem(), LAST_SEL_NODE);
        nodeSelectionPersistence((AbstractDicomNode) comboCallingNode.getSelectedItem(), LAST_CALLING_NODE);
        RetrieveType type = (RetrieveType) comboDicomRetrieveType.getSelectedItem();
        if (type != null) {
            DicomQrFactory.IMPORT_PERSISTENCE.setProperty(LAST_RETRIEVE_TYPE, type.name());
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

    protected void updateChanges() {
    }

    @Override
    public void closeAdditionalWindow() {
        applyChange();
    }

    @Override
    public void resetoDefaultValues() {
    }

    private List<String> getCheckedStudies(TreePath[] paths) {
        List<String> studies = new ArrayList<>();
        for (TreePath treePath : paths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
            if (node.getUserObject() instanceof MediaSeriesGroup) {
                MediaSeriesGroup study = (MediaSeriesGroup) node.getUserObject();
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
        ExplorerTask task = new ExplorerTask(AbstractDicomNode.UsageType.RETRIEVE.toString(), false,
            new CircularProgressBar(0, 100), false) {

            @Override
            protected Boolean doInBackground() throws Exception {
                return retrieveDICOM(explorerDcmModel, this);
            }

            @Override
            protected void done() {
                explorerDcmModel.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, explorerDcmModel, null, this));
            }
        };
        task.execute();
    }

    public boolean retrieveDICOM(DicomModel explorerDcmModel, final ExplorerTask task) {
        explorerDcmModel.firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, explorerDcmModel, null, task));

        String errorMessage = null;
        final CircularProgressBar progressBar = task.getBar();
        DicomProgress progress = new DicomProgress();
        progress.addProgressListener(p -> {
            GuiExecutor.instance().execute(() -> {
                int c = p.getNumberOfCompletedSuboperations() + p.getNumberOfFailedSuboperations();
                int r = p.getNumberOfRemainingSuboperations();
                int t = c + r;
                if (t > 0) {
                    progressBar.setValue((c * 100) / t);
                }
            });
        });
        
        task.addCancelListener(progress);

        List<String> studies = getCheckedStudies(tree.getCheckboxTree().getCheckingPaths());

        DicomParam[] dcmParams = { new DicomParam(Tag.StudyInstanceUID, studies.toArray(new String[studies.size()])) };

        Object selectedItem = comboNode.getSelectedItem();
        if (selectedItem instanceof DefaultDicomNode) {
            final DefaultDicomNode node = (DefaultDicomNode) selectedItem;
            DefaultDicomNode callingNode = (DefaultDicomNode) comboCallingNode.getSelectedItem();
            if (callingNode == null) {
                errorMessage = "No calling DICOM node configured for retrieve";
            } else {
                final DicomState state;
                RetrieveType type = (RetrieveType) comboDicomRetrieveType.getSelectedItem();
                AdvancedParams params = new AdvancedParams();
                ConnectOptions connectOptions = new ConnectOptions();
                connectOptions.setConnectTimeout(3000);
                connectOptions.setAcceptTimeout(5000);
                params.setConnectOptions(connectOptions);
                if (RetrieveType.CGET == type) {
                    progressBar.setIndeterminate(true);
                    File sopClass = ResourceUtil.getResource("store-tcs.properties");
                    URL url = null;
                    if(sopClass.canRead()) {
                        try {
                            url = sopClass.toURI().toURL();
                        } catch (MalformedURLException e) {
                            LOGGER.error("SOP Class url conversion", e);
                        }
                    }
                    state = CGet.process(params, callingNode.getDicomNode(), node.getDicomNode(), progress, tempDir,
                        url, dcmParams);
                } else if (RetrieveType.CMOVE == type) {
                    try {
                        if (dicomListener == null) {
                            errorMessage = "Cannot start a DICOM listener";
                        } else {
                            dicomListener.setParams(params);
                            if (dicomListener.isRunning()) {
                                errorMessage = "A DICOM C-Move already running";
                            } else {
                                dicomListener.start(callingNode.getDicomNode());
                            }
                        }
                    } catch (Exception e) {
                        dicomListener.stop();
                        errorMessage = String.format("Cannot a start DICOM listener: %s.", e.getMessage());
                        LOGGER.error("Start DICOM listener", e);
                    }

                    if (errorMessage != null) {
                        state = new DicomState(Status.UnableToProcess, errorMessage, null);
                    } else {
                        state = CMove.process(null, callingNode.getDicomNode(), node.getDicomNode(),
                            callingNode.getAeTitle(), progress, dcmParams);
                        dicomListener.stop();
                    }
                } else if (RetrieveType.WADO == type) {
                    List<AbstractDicomNode> webNodes = AbstractDicomNode.loadDicomNodes(AbstractDicomNode.Type.WEB,
                        AbstractDicomNode.UsageType.RETRIEVE);
                    String host = getHostname(node.getDicomNode().getHostname());
                    List<URL> wadoURLs = new ArrayList<>();
                    for (AbstractDicomNode n : webNodes) {
                        if (n instanceof DicomWebNode) {
                            DicomWebNode wn = (DicomWebNode) n;
                            URL url = wn.getUrl();
                            if (DicomWebNode.WebType.WADO == wn.getWebType() && url != null
                                && host.equals(getHostname(url.getHost()))) {
                                wadoURLs.add(url);
                            }
                        }
                    }
                    if (wadoURLs.isEmpty()) {
                        GuiExecutor.instance()
                            .execute(() -> JOptionPane.showMessageDialog(panelBase,
                                "No URL matchs with DICOM query hostname. Add a new WEB node.",
                                RetrieveType.WADO.toString(), JOptionPane.ERROR_MESSAGE));
                        return false;
                    }
                    if (wadoURLs.size() > 1) {
                        GuiExecutor.instance().invokeAndWait(() -> {
                            Object[] options = wadoURLs.toArray();
                            Object response = JOptionPane.showInputDialog(panelBase,
                                "Several URLs match, please select one", RetrieveType.WADO.toString(),
                                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

                            if (response != null) {
                                wadoURLs.clear();
                                wadoURLs.add((URL) response);
                            }
                        });
                    }

                    WadoParameters wadoParameters =
                        new WadoParameters(wadoURLs.get(0).toString(), false, null, null, null);
                    ManifestBuilder manifest = new ManifestBuilder();
                    manifest.fillSeries(params, callingNode.getDicomNode(), node.getDicomNode(), dicomModel, studies);
                    String wadoXmlGenerated = manifest.xmlManifest(wadoParameters, null);
                    if (wadoXmlGenerated == null) {
                        state = new DicomState(Status.UnableToProcess, "Cannot build WADO manifest", null);
                    } else {
                        List<String> xmlFiles = new ArrayList<>(1);
                        try {
                            File tempFile = File.createTempFile("wado_", ".xml", AppProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                            FileUtil.writeStreamWithIOException(
                                new ByteArrayInputStream(wadoXmlGenerated.getBytes(StandardCharsets.UTF_8)), tempFile);
                            xmlFiles.add(tempFile.getPath());

                        } catch (Exception e) {
                            LOGGER.info("ungzip manifest", e); //$NON-NLS-1$
                        }

                        DicomModel.LOADING_EXECUTOR.execute(new LoadRemoteDicomManifest(xmlFiles, explorerDcmModel));
                        return true;
                    }
                } else {
                    state = new DicomState(Status.UnableToProcess, "Not supported retrieve type", null);
                }

                if (state.getStatus() != Status.Success && state.getStatus() != Status.Cancel) {
                    errorMessage = state.getMessage();
                    if (!StringUtil.hasText(errorMessage)) {
                        DicomState.buildMessage(state, null, null);
                    }
                    if (!StringUtil.hasText(errorMessage)) {
                        errorMessage = "Unexpected DICOM error";
                    }
                    LOGGER.error("Dicom retrieve error: {}", errorMessage); //$NON-NLS-1$
                }
                DicomModel.LOADING_EXECUTOR
                    .execute(new LoadLocalDicom(new File[] { tempDir }, false, explorerDcmModel));
            }

        } else if (selectedItem instanceof DicomWebNode) {
            throw new IllegalAccessError("Not implemented yet");
        } else {
            errorMessage = "No calling DICOM node configured for retrieve";
        }

        if (errorMessage != null) {
            final String mes = errorMessage;
            final String errorTitle = StringUtil.getEmpty2NullObject(comboDicomRetrieveType.getSelectedItem());
            GuiExecutor.instance()
                .execute(() -> JOptionPane.showMessageDialog(panelBase, mes, errorTitle, JOptionPane.ERROR_MESSAGE));
        }

        return errorMessage == null;
    }

    private static String getHostname(String host) {
        if ("127.0.0.1".equals(host) || "::1".equals(host)) {
            return "localhost";
        }
        return host;
    }
}
