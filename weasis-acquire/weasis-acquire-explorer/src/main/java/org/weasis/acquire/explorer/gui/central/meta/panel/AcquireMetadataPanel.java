/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.central.meta.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;

import org.dcm4che3.data.Tag;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;

import com.github.lgooddatepicker.components.DatePickerSettings;
import com.github.lgooddatepicker.components.DatePickerSettings.DateArea;
import com.github.lgooddatepicker.tableeditors.DateTableEditor;
import com.github.lgooddatepicker.tableeditors.TimeTableEditor;

@SuppressWarnings("serial")
public abstract class AcquireMetadataPanel extends JPanel implements TableModelListener {
    protected final String title;
    protected final JLabel label = new JLabel();
    protected final JTable table;
    protected final JScrollPane tableScroll;
    protected AcquireImageInfo imageInfo;
    protected TitledBorder titleBorder;

    public AcquireMetadataPanel(String title) {
        setLayout(new BorderLayout());
        this.title = title;
        this.titleBorder =
            new TitledBorder(null, getDisplayText(), TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION);

        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(7, 3, 0, 3), titleBorder));

        tableScroll = new JScrollPane();
        tableScroll.setBorder(BorderFactory.createEmptyBorder(7, 3, 0, 3));
        table = new JTable();
        // Force to commit value when losing the focus
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE); //$NON-NLS-1$
        table.setFont(FontTools.getFont11()); // Default size
        table.getTableHeader().setReorderingAllowed(false);
        updateTable();

        setMetaVisible(false);

        add(tableScroll, BorderLayout.CENTER);
    }

    public abstract AcquireMetadataTableModel newTableModel();

    public String getDisplayText() {
        return title;
    }

    public void stopEditing() {
        TableCellEditor editor = table.getCellEditor();
        if (editor != null) {
            editor.stopCellEditing();
        }
    }

    @Override
    public Font getFont() {
        return FontTools.getFont12Bold();
    }

    public void setImageInfo(AcquireImageInfo imageInfo) {
        this.imageInfo = imageInfo;
        this.titleBorder.setTitle(getDisplayText());
        setMetaVisible(imageInfo != null);
        update();
    }

    public void setMetaVisible(boolean visible) {
        tableScroll.setVisible(visible);
    }

    public void update() {
        updateLabel();
        updateTable();
    }

    public void updateLabel() {
        this.titleBorder.setTitle(getDisplayText());
    }

    public void updateTable() {
        AcquireMetadataTableModel model = newTableModel();
        model.addTableModelListener(this);
        table.setModel(model);
        table.getColumnModel().getColumn(1).setCellRenderer(new TagRenderer());
        table.getColumnModel().getColumn(1).setCellEditor(new AcquireImageCellEditor());
        int height = Optional.ofNullable(table.getFont()).map(f -> f.getSize() + 10).orElse(24);
        for (int i = 0; i < table.getRowCount(); i++) {
            table.setRowHeight(i, height);
        }

        JPanel tableContainer = new JPanel(new BorderLayout());
        int cheight =
            (height + table.getRowMargin()) * table.getRowCount() + table.getRowHeight() + table.getRowMargin();
        tableContainer.setPreferredSize(new Dimension(150, cheight));
        tableContainer.add(table.getTableHeader(), BorderLayout.PAGE_START);
        tableContainer.add(table, BorderLayout.CENTER);
        tableScroll.setViewportView(tableContainer);
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        // DO NOTHING
    }

    @SuppressWarnings("serial")
    public static class TagRenderer extends DefaultTableCellRenderer {

        public TagRenderer() {
            setFont(FontTools.getFont11()); // Default size
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
            Component val = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            AcquireMetadataTableModel model = (AcquireMetadataTableModel) table.getModel();
            if (model.isCellEditable(row, column)) {
                Color c = model.isValueRequired(row) && isEmptyValue(value) ? Color.RED : getForeground();
                setBorder(BorderFactory.createDashedBorder(c, 2, 2));
            }

            Object tag = model.getValueAt(row, 0);
            if (tag instanceof TagW) {
                setValue(((TagW) tag).getFormattedTagValue(value, null));
            }
            return val;
        }

        private boolean isEmptyValue(Object value) {
            if (value == null) {
                return true;
            }
            if (value instanceof String) {
                return !StringUtil.hasText((String) value);
            }
            return false;
        }

    }

    @SuppressWarnings("serial")
    public static class AcquireImageCellEditor extends AbstractCellEditor implements TableCellEditor {
        // TODO more anatomy: http://dicom.nema.org/medical/dicom/2016c/output/chtml/part03/sect_10.5.html
        private static final JComboBox<String> bodyPartsCombo =
            new JComboBox<>(getBodyPartValues("weasis.acquire.meta.body.part")); //$NON-NLS-1$
        private static final JComboBox<TagD.Sex> sexCombo = new JComboBox<>(TagD.Sex.values());
        private static final JComboBox<Modality> modalityCombo =
            new JComboBox<>(Modality.getAllModalitiesExceptDefault());
        private static final JComboBox<String> studyDescCombo =
            new JComboBox<>(getValues("weasis.acquire.meta.study.description", null)); //$NON-NLS-1$
        private static final JComboBox<String> seriesDescCombo =
            new JComboBox<>(getValues("weasis.acquire.meta.series.description", null)); //$NON-NLS-1$
        static {
            initCombo(bodyPartsCombo);
            initCombo(sexCombo);
            initCombo(modalityCombo);
            initCombo(studyDescCombo);
            initCombo(seriesDescCombo);
        }

        private Optional<TableCellEditor> editor;

        @Override
        public Object getCellEditorValue() {
            return editor.map(e -> convertValue(e.getCellEditorValue())).orElse(null);
        }

        private Object convertValue(Object val) {
            if (val instanceof TagD.Sex) {
                return ((TagD.Sex) val).getValue();
            } else if (val instanceof Modality) {
                return ((Modality) val).name();
            }
            return val;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row,
            int column) {
            TableCellEditor cellEditor;
            Object tag = table.getModel().getValueAt(row, 0);
            int tagID = 0;
            boolean date = false;
            boolean time = false;
            if (tag instanceof TagW) {
                tagID = ((TagW) tag).getId();
                TagType type = ((TagW) tag).getType();
                date = TagType.DICOM_DATE == type || TagType.DATE == type;
                time = TagType.DICOM_TIME == type || TagType.TIME == type;
            }
            if (tagID == Tag.BodyPartExamined) {
                cellEditor = new DefaultCellEditor(bodyPartsCombo);
            } else if (tagID == Tag.PatientSex) {
                cellEditor = new DefaultCellEditor(sexCombo);
            } else if (tagID == Tag.Modality) {
                cellEditor = new DefaultCellEditor(modalityCombo);
            } else if (tagID == Tag.StudyDescription) {
                cellEditor = getCellEditor(studyDescCombo);
            } else if (tagID == Tag.SeriesDescription) {
                cellEditor = getCellEditor(seriesDescCombo);
            } else if (date) {
                DateTableEditor teditor = buildDatePicker();
                JMVUtils.setPreferredHeight(teditor.getDatePicker().getComponentToggleCalendarButton(),
                    table.getRowHeight(row));
                cellEditor = teditor;
            } else if (time) {
                TimeTableEditor teditor = new TimeTableEditor(false, true, true);
                teditor.getTimePickerSettings().fontInvalidTime = FontTools.getFont11(); // Default size
                teditor.getTimePickerSettings().fontValidTime = FontTools.getFont11(); // Default size
                teditor.getTimePickerSettings().fontVetoedTime = FontTools.getFont11(); // Default size
                JMVUtils.setPreferredHeight(teditor.getTimePicker().getComponentToggleTimeMenuButton(),
                    table.getRowHeight(row));
                cellEditor = teditor;
            } else {
                cellEditor = new DefaultCellEditor(new JTextField());
            }
            editor = Optional.of(cellEditor);
            Component c = cellEditor.getTableCellEditorComponent(table, value, isSelected, row, column);
            c.setFont(FontTools.getFont11()); // Default size
            return c;
        }

        private static DefaultCellEditor getCellEditor(JComboBox<?> combo) {
            if (combo.getItemCount() == 0) {
                return new DefaultCellEditor(new JTextField());
            } else {
                return new DefaultCellEditor(combo);
            }
        }

        private static void initCombo(JComboBox<?> combo) {
            // Set before tooltip, otherwise update UI => remove selection listener
            combo.setFont(FontTools.getFont11());
            combo.setMaximumRowCount(15);
            JMVUtils.setPreferredWidth(combo, 80);
            // Update UI before adding the Tooltip feature in the combobox list
            combo.updateUI();
            JMVUtils.addTooltipToComboList(combo);
        }

        private DateTableEditor buildDatePicker() {
            DateTableEditor d = new DateTableEditor(false, true, true);
            DatePickerSettings settings = d.getDatePickerSettings();
            settings.setFontInvalidDate(FontTools.getFont11());
            settings.setFontValidDate(FontTools.getFont11());
            settings.setFontVetoedDate(FontTools.getFont11());
            Color btnBack = d.getDatePicker().getBackground();
            JTextField tfSearch = new JTextField();
            settings.setColor(DateArea.BackgroundOverallCalendarPanel, tfSearch.getBackground());
            settings.setColor(DateArea.BackgroundMonthAndYearNavigationButtons, btnBack);
            settings.setColor(DateArea.CalendarBackgroundNormalDates, btnBack);

            // settings.setColor(DateArea.CalendarDefaultBackgroundHighlightedDates, tfSearch.getForeground());
            // settings.setColor(DateArea.CalendarDefaultTextHighlightedDates, Color.ORANGE);
            // settings.setColor(DateArea.CalendarBackgroundVetoedDates, Color.MAGENTA);
            settings.setColor(DateArea.BackgroundClearLabel, btnBack);
            settings.setColor(DateArea.BackgroundMonthAndYearNavigationButtons, btnBack);
            settings.setColor(DateArea.BackgroundTodayLabel, btnBack);
            settings.setColor(DateArea.BackgroundTopLeftLabelAboveWeekNumbers, btnBack);
            settings.setColor(DateArea.BackgroundMonthAndYearMenuLabels, btnBack);

            settings.setColor(DateArea.CalendarTextNormalDates, tfSearch.getForeground());
            settings.setColor(DateArea.CalendarTextWeekdays, tfSearch.getForeground());
            settings.setColor(DateArea.CalendarTextWeekNumbers, tfSearch.getForeground());

            settings.setFormatForDatesCommonEra(LocalUtil.getDateFormatter());
            settings.setFormatForDatesBeforeCommonEra(LocalUtil.getDateFormatter());
            return d;
        }

        public static String[] getBodyPartValues(String property) {
            String values = BundleTools.SYSTEM_PREFERENCES.getProperty(property, null);
            if (values == null || !StringUtil.hasText(values)) {
                return new String[] { "ABDOMEN", "ABDOMENPELVIS", "ADRENAL", "ANKLE", "AORTA", "ARM", "AXILLA", "BACK", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
                    "BLADDER", "BRAIN", "BREAST", "BRONCHUS", "BUTTOCK", "CALCANEUS", "CALF", "CAROTID", "CEREBELLUM", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
                    "CERVIX", "CHEEK", "CHEST", "CHESTABDOMEN", "CHESTABDPELVIS", "CIRCLEOFWILLIS", "CLAVICLE", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
                    "COCCYX", "COLON", "CORNEA", "CORONARYARTERY", "CSPINE", "CTSPINE", "DUODENUM", "EAR", "ELBOW", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
                    "ESOPHAGUS", "EXTREMITY", "EYE", "EYELID", "FACE", "FEMUR", "FINGER", "FOOT", "GALLBLADDER", "HAND", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
                    "HEAD", "HEADNECK", "HEART", "HIP", "HUMERUS", "IAC", "ILEUM", "ILIUM", "JAW", "JEJUNUM", "KIDNEY", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$
                    "KNEE", "LARYNX", "LEG", "LIVER", "LSPINE", "LSSPINE", "LUNG", "MAXILLA", "MEDIASTINUM", "MOUTH", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
                    "NECK", "NECKCHEST", "NECKCHESTABDOMEN", "NECKCHESTABDPELV", "NOSE", "ORBIT", "OVARY", "PANCREAS", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
                    "PAROTID", "PATELLA", "PELVIS", "PENIS", "PHARYNX", "PROSTATE", "RADIUS", "RADIUSULNA", "RECTUM", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
                    "RIB", "SCALP", "SCAPULA", "SCLERA", "SCROTUM", "SHOULDER", "SKULL", "SPINE", "SPLEEN", "SSPINE", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
                    "STERNUM", "STOMACH", "SUBMANDIBULAR", "TESTIS", "THIGH", "THUMB", "THYMUS", "THYROID", "TIBIA", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
                    "TIBIAFIBULA", "TLSPINE", "TMJ", "TOE", "TONGUE", "TRACHEA", "TSPINE", "ULNA", "URETER", "URETHRA", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
                    "UTERUS", "VAGINA", "VULVA", "WHOLEBODY", "WRIST", "ZYGOMA" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
            }
            String[] val = values.split(","); //$NON-NLS-1$
            List<String> list = new ArrayList<>(val.length);
            for (String s : val) {
                String v = s.trim();
                // VR must be CS
                if (StringUtil.hasText(v) && v.length() <= 16) {
                    list.add(v);
                }
            }
            return list.toArray(new String[list.size()]);
        }

        public static String[] getValues(String property, String defaultValues) {
            String values = BundleTools.SYSTEM_PREFERENCES.getProperty(property, defaultValues);
            if (values == null) {
                return new String[0];
            }
            String[] val = values.split(","); //$NON-NLS-1$
            List<String> list = new ArrayList<>(val.length);
            for (String s : val) {
                if (StringUtil.hasText(s)) {
                    list.add(s.trim());
                }
            }
            return list.toArray(new String[list.size()]);
        }
    }
}
