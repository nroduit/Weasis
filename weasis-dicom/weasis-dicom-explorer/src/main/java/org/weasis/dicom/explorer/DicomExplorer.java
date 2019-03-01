/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.font.FontRenderContext;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.dcm4che3.data.Tag;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.ArrayListComboBoxModel;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.core.ui.util.WrapLayout;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.wado.LoadSeries;

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;

public class DicomExplorer extends PluginTool implements DataExplorerView, SeriesViewerListener {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(DicomExplorer.class);

    public static final String NAME = Messages.getString("DicomExplorer.title"); //$NON-NLS-1$
    public static final String PREFERENCE_NODE = "dicom.explorer"; //$NON-NLS-1$
    public static final String BUTTON_NAME = Messages.getString("DicomExplorer.btn_title"); //$NON-NLS-1$
    public static final String DESCRIPTION = Messages.getString("DicomExplorer.desc"); //$NON-NLS-1$
    public static final String ALL_PATIENTS = Messages.getString("DicomExplorer.sel_all_pat"); //$NON-NLS-1$
    public static final String ALL_STUDIES = Messages.getString("DicomExplorer.sel_all_st"); //$NON-NLS-1$
    public static final Icon PATIENT_ICON = new ImageIcon(DicomExplorer.class.getResource("/icon/16x16/patient.png")); //$NON-NLS-1$
    public static final Icon KO_ICON = new ImageIcon(DicomExplorer.class.getResource("/icon/16x16/key-images.png")); // $NON-NLS-0$ //$NON-NLS-1$

    private JPanel panel = null;
    private PatientPane selectedPatient = null;

    private final List<PatientPane> patientPaneList = new ArrayList<>();
    private final HashMap<MediaSeriesGroup, List<StudyPane>> patient2study = new HashMap<>();
    private final HashMap<MediaSeriesGroup, List<SeriesPane>> study2series = new HashMap<>();
    private final JScrollPane thumnailView = new JScrollPane();
    private final LoadingPanel loadingPanel = new LoadingPanel();
    private final SeriesSelectionModel selectionList;

    private final DicomModel model;

    private final ArrayListComboBoxModel<Object> modelPatient =
        new ArrayListComboBoxModel<>(DicomSorter.PATIENT_COMPARATOR);
    private final ArrayListComboBoxModel<Object> modelStudy =
        new ArrayListComboBoxModel<>(DicomSorter.STUDY_COMPARATOR);
    private final JComboBox<?> patientCombobox = new JComboBox<>(modelPatient);
    private final JComboBox<?> studyCombobox = new JComboBox<>(modelStudy);
    protected final PatientContainerPane patientContainer = new PatientContainerPane();
    private final transient ItemListener patientChangeListener = e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            Object item = modelPatient.getSelectedItem();
            if (item instanceof MediaSeriesGroupNode) {
                MediaSeriesGroupNode patient = (MediaSeriesGroupNode) item;
                selectPatient(patient);
            } else if (item != null) {
                selectPatient(null);
            }
            patientContainer.revalidate();
            patientContainer.repaint();
        }
    };
    private final transient ItemListener studyItemListener = e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            selectStudy();
        }
    };
    private final JSlider slider = new JSlider(Thumbnail.MIN_SIZE, Thumbnail.MAX_SIZE, Thumbnail.DEFAULT_SIZE);
    private JPanel panelMain = null;
    private final JToggleButton btnMoreOptions = new JToggleButton(Messages.getString("DicomExplorer.more_opt")); //$NON-NLS-1$
    private boolean verticalLayout = true;

    private final JButton koOpen = new JButton(Messages.getString("DicomExplorer.open_ko"), KO_ICON); //$NON-NLS-1$

    public DicomExplorer() {
        this(null);
    }

    public DicomExplorer(DicomModel model) {
        super(BUTTON_NAME, NAME, POSITION.WEST, ExtendedMode.NORMALIZED, PluginTool.Type.EXPLORER, 20);
        setLayout(new BorderLayout());
        setDockableWidth(180);
        dockable.setMaximizable(true);
        this.model = model == null ? new DicomModel() : model;
        this.selectionList = new SeriesSelectionModel(patientContainer);
        thumnailView.getVerticalScrollBar().setUnitIncrement(16);
        thumnailView.setViewportView(patientContainer);
        changeToolWindowAnchor(getDockable().getBaseLocation());

    }

    public SeriesSelectionModel getSelectionList() {
        return selectionList;
    }

    private void removePatientPane(MediaSeriesGroup patient) {
        for (int i = 0; i < patientPaneList.size(); i++) {
            PatientPane p = patientPaneList.get(i);
            if (p.isPatient(patient)) {
                patientPaneList.remove(i);
                List<StudyPane> studies = patient2study.remove(patient);
                if (studies != null) {
                    for (StudyPane studyPane : studies) {
                        study2series.remove(studyPane.dicomStudy);
                    }
                }
                patientContainer.remove(p);
                modelPatient.removeElement(patient);
                if (modelPatient.getSize() == 0) {
                    modelStudy.removeAllElements();
                    modelStudy.insertElementAt(ALL_STUDIES, 0);
                    modelStudy.setSelectedItem(ALL_STUDIES);
                    koOpen.setVisible(false);
                }
                return;
            }
        }
    }

    private void removeStudy(MediaSeriesGroup study) {
        MediaSeriesGroup patient = model.getParent(study, DicomModel.patient);
        List<StudyPane> studies = patient2study.get(patient);
        if (studies != null) {
            for (int i = 0; i < studies.size(); i++) {
                StudyPane st = studies.get(i);
                if (st.isStudy(study)) {
                    studies.remove(i);
                    if (studies.isEmpty()) {
                        patient2study.remove(patient);
                        // throw a new event for removing the patient
                        model.removePatient(patient);
                        break;
                    }
                    study2series.remove(study);
                    PatientPane patientPane = getPatientPane(patient);
                    if (patientPane != null && patientPane.isStudyVisible(study)) {
                        patientPane.remove(st);
                        modelStudy.removeElement(study);
                        patientPane.revalidate();
                        patientPane.repaint();
                    }
                    return;
                }
            }
        }
        study2series.remove(study);
    }

    private void removeSeries(MediaSeriesGroup series) {
        MediaSeriesGroup study = model.getParent(series, DicomModel.study);
        List<SeriesPane> seriesList = study2series.get(study);
        if (seriesList != null) {
            for (int j = 0; j < seriesList.size(); j++) {
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

    private PatientPane createPatientPane(MediaSeriesGroup patient) {
        PatientPane pat = getPatientPane(patient);
        if (pat == null) {
            pat = new PatientPane(patient);
            patientPaneList.add(pat);
        }
        return pat;
    }

    private PatientPane getPatientPane(MediaSeriesGroup patient) {
        for (PatientPane p : patientPaneList) {
            if (p.isPatient(patient)) {
                return p;
            }
        }
        return null;
    }

    private StudyPane getStudyPane(MediaSeriesGroup study) {
        List<StudyPane> studies = patient2study.get(model.getParent(study, DicomModel.patient));
        if (studies != null) {
            for (int i = 0; i < studies.size(); i++) {
                StudyPane st = studies.get(i);
                if (st.isStudy(study)) {
                    return st;
                }
            }
        }
        return null;
    }

    private StudyPane createStudyPaneInstance(MediaSeriesGroup study, int[] position) {
        StudyPane studyPane = getStudyPane(study);
        if (studyPane == null) {
            studyPane = new StudyPane(study);
            List<StudyPane> studies = patient2study.get(model.getParent(study, DicomModel.patient));
            if (studies != null) {
                int index = Collections.binarySearch(studies, studyPane, DicomSorter.STUDY_COMPARATOR);
                if (index < 0) {
                    index = -(index + 1);
                } else {
                    index = studies.size();
                }
                if (position != null) {
                    position[0] = index;
                }
                studies.add(index, studyPane);
            }
        } else if (position != null) {
            position[0] = -1;
        }
        return studyPane;
    }

    private void updateThumbnailSize() {
        int thumbnailSize = slider.getValue();
        for (PatientPane p : patientContainer.getPatientPaneList()) {
            for (StudyPane studyPane : p.getStudyPaneList()) {
                for (SeriesPane series : studyPane.getSeriesPaneList()) {
                    series.updateSize(thumbnailSize);
                }
                studyPane.doLayout();
            }
        }
        patientContainer.revalidate();
        patientContainer.repaint();

    }

    private SeriesPane getSeriesPane(MediaSeriesGroup series) {
        List<SeriesPane> seriesList = study2series.get(model.getParent(series, DicomModel.study));
        if (seriesList != null) {
            for (int j = 0; j < seriesList.size(); j++) {
                SeriesPane se = seriesList.get(j);
                if (se.isSeries(series)) {
                    return se;
                }
            }
        }
        return null;
    }

    private synchronized SeriesPane createSeriesPaneInstance(MediaSeriesGroup series, int[] position) {
        SeriesPane seriesPane = getSeriesPane(series);
        if (seriesPane == null) {
            seriesPane = new SeriesPane(series);
            List<SeriesPane> seriesList = study2series.get(model.getParent(series, DicomModel.study));
            if (seriesList != null) {
                int index = Collections.binarySearch(seriesList, seriesPane, DicomSorter.SERIES_COMPARATOR);
                if (index < 0) {
                    index = -(index + 1);
                } else {
                    index = seriesList.size();
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

    private boolean isSelectedPatient(MediaSeriesGroup patient) {
        if (selectedPatient != null && selectedPatient.patient == patient) {
            return true;
        }
        return false;
    }

    class TitleBorder extends TitledBorder {

        public TitleBorder(String title) {
            super(title);
            setFont(FontTools.getFont10());
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            // Measure the title length
            FontRenderContext frc = ((Graphics2D) g).getFontRenderContext();
            Rectangle bound = getTitleFont().getStringBounds(title, frc).getBounds();
            int panelLength = width - 15;
            if (bound.width > panelLength) {
                int length = (title.length() * panelLength) / bound.width;
                if (length > 2) {
                    title = title.substring(0, length - 2) + "..."; //$NON-NLS-1$
                }
            }
            super.paintBorder(c, g, x, y, width, height);
        }
    }

    @SuppressWarnings("serial")
    class PatientContainerPane extends JPanel {

        private final GridBagConstraints constraint = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0,
            GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
        private final Component filler = Box.createRigidArea(new Dimension(5, 5));

        public PatientContainerPane() {
            modelPatient.removeAllElements();
            // do not use addElement
            // modelPatient.insertElementAt(ALL_PATIENTS, 0);
            setLayout(new GridBagLayout());
        }

        List<PatientPane> getPatientPaneList() {
            List<PatientPane> patientPanes = new ArrayList<>();
            for (Component c : this.getComponents()) {
                if (c instanceof PatientPane) {
                    patientPanes.add((PatientPane) c);
                }
            }
            return patientPanes;
        }

        private void refreshLayout() {
            List<PatientPane> list = getPatientPaneList();
            super.removeAll();
            for (PatientPane p : list) {
                p.refreshLayout();
                if (p.getComponentCount() > 0) {
                    addPane(p);
                }
            }
        }

        private void showAllPatients() {
            super.removeAll();
            for (PatientPane patientPane : patientPaneList) {
                patientPane.showTitle(true);
                patientPane.showAllstudies();
                if (patientPane.getComponentCount() > 0) {
                    addPane(patientPane);
                }
            }
            this.revalidate();
        }

        public void addPane(PatientPane patientPane, int position) {
            constraint.gridx = verticalLayout ? 0 : position;
            constraint.gridy = verticalLayout ? position : 0;

            remove(filler);
            constraint.weightx = verticalLayout ? 1.0 : 0.0;
            constraint.weighty = verticalLayout ? 0.0 : 1.0;
            add(patientPane, constraint);
            constraint.weightx = verticalLayout ? 0.0 : 1.0;
            constraint.weighty = verticalLayout ? 1.0 : 0.0;
            add(filler, constraint);
        }

        public void addPane(PatientPane patientPane) {
            addPane(patientPane, GridBagConstraints.RELATIVE);
        }

        public boolean isPatientVisible(MediaSeriesGroup patient) {
            for (Component c : this.getComponents()) {
                if (c instanceof PatientPane) {
                    if (((PatientPane) c).isPatient(patient)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean isStudyVisible(MediaSeriesGroup study) {
            MediaSeriesGroup patient = model.getParent(study, DicomModel.patient);
            for (Component c : this.getComponents()) {
                if (c instanceof PatientPane) {
                    PatientPane patientPane = (PatientPane) c;
                    if (patientPane.isPatient(patient) && patientPane.isStudyVisible(study)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean isSeriesVisible(MediaSeriesGroup series) {
            MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
            for (Component c : this.getComponents()) {
                if (c instanceof PatientPane) {
                    PatientPane patientPane = (PatientPane) c;
                    if (patientPane.isPatient(patient) && patientPane.isSeriesVisible(series)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @SuppressWarnings("serial")
    class PatientPane extends JPanel {

        private final MediaSeriesGroup patient;
        private final GridBagConstraints constraint = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0,
            GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);

        public PatientPane(MediaSeriesGroup patient) {
            if (patient == null) {
                throw new IllegalArgumentException("Patient cannot be null"); //$NON-NLS-1$
            }
            this.patient = patient;
            this.setAlignmentX(LEFT_ALIGNMENT);
            this.setAlignmentY(TOP_ALIGNMENT);
            this.setFocusable(false);
            setLayout(new GridBagLayout());
        }

        public void showTitle(boolean show) {
            if (show) {
                TitleBorder title = new TitleBorder(patient.toString());
                title.setTitleFont(FontTools.getFont12Bold());
                title.setTitleJustification(TitledBorder.LEFT);
                Color color = javax.swing.UIManager.getColor("ComboBox.buttonHighlight"); //$NON-NLS-1$
                title.setTitleColor(color);
                title.setBorder(BorderFactory.createLineBorder(color, 2));
                this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 5, 25, 5), title));
            } else {
                this.setBorder(null);
            }
        }

        public boolean isStudyVisible(MediaSeriesGroup study) {
            for (Component c : this.getComponents()) {
                if (c instanceof StudyPane) {
                    StudyPane studyPane = (StudyPane) c;
                    if (studyPane.isStudy(study)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean isSeriesVisible(MediaSeriesGroup series) {
            MediaSeriesGroup study = model.getParent(series, DicomModel.study);
            for (Component c : this.getComponents()) {
                if (c instanceof StudyPane) {
                    StudyPane studyPane = (StudyPane) c;
                    if (studyPane.isStudy(study) && studyPane.isSeriesVisible(series)) {
                        return true;
                    }
                }
            }
            return false;
        }

        List<StudyPane> getStudyPaneList() {
            ArrayList<StudyPane> studyPaneList = new ArrayList<>();
            for (Component c : this.getComponents()) {
                if (c instanceof StudyPane) {
                    studyPaneList.add((StudyPane) c);
                }
            }
            return studyPaneList;
        }

        private void refreshLayout() {
            List<StudyPane> studies = getStudyPaneList();
            super.removeAll();
            for (StudyPane studyPane : studies) {
                studyPane.refreshLayout();
                if (studyPane.getComponentCount() > 0) {
                    addPane(studyPane);
                }
                studyPane.doLayout();
            }
            this.revalidate();
        }

        private void showAllstudies() {
            super.removeAll();
            List<StudyPane> studies = patient2study.get(patient);
            if (studies != null) {
                for (StudyPane studyPane : studies) {
                    studyPane.showAllSeries();
                    studyPane.refreshLayout();
                    if (studyPane.getComponentCount() > 0) {
                        addPane(studyPane);
                    }
                    studyPane.doLayout();
                }
                this.revalidate();
            }
        }

        public void addPane(StudyPane studyPane) {
            addPane(studyPane, GridBagConstraints.RELATIVE);
        }

        public void addPane(StudyPane studyPane, int position) {
            constraint.gridx = verticalLayout ? 0 : position;
            constraint.gridy = verticalLayout ? position : 0;

            constraint.weightx = verticalLayout ? 1.0 : 0.0;
            constraint.weighty = 0.0;

            add(studyPane, constraint);
        }

        public boolean isPatient(MediaSeriesGroup patient) {
            return this.patient.equals(patient);
        }
    }

    @SuppressWarnings("serial")
    class StudyPane extends JPanel {

        final MediaSeriesGroup dicomStudy;
        private final TitleBorder title;

        public StudyPane(MediaSeriesGroup dicomStudy) {
            if (dicomStudy == null) {
                throw new IllegalArgumentException("Study cannot be null"); //$NON-NLS-1$
            }
            this.setAlignmentX(LEFT_ALIGNMENT);
            this.setAlignmentY(TOP_ALIGNMENT);
            this.dicomStudy = dicomStudy;
            title = new TitleBorder(dicomStudy.toString());
            title.setTitleFont(FontTools.getFont12());
            title.setTitleJustification(TitledBorder.LEFT);
            this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5), title));
            this.setFocusable(false);
            refreshLayout();
        }

        public boolean isSeriesVisible(MediaSeriesGroup series) {
            for (Component c : this.getComponents()) {
                if (c instanceof SeriesPane) {
                    if (((SeriesPane) c).isSeries(series)) {
                        return true;
                    }
                }
            }
            return false;
        }

        List<SeriesPane> getSeriesPaneList() {
            ArrayList<SeriesPane> seriesPaneList = new ArrayList<>();
            for (Component c : this.getComponents()) {
                if (c instanceof SeriesPane) {
                    seriesPaneList.add((SeriesPane) c);
                }
            }
            return seriesPaneList;
        }

        private void refreshLayout() {
            this.setLayout(verticalLayout ? new WrapLayout(FlowLayout.LEFT) : new BoxLayout(this, BoxLayout.X_AXIS));
        }

        private void showAllSeries() {
            super.removeAll();
            List<SeriesPane> seriesList = study2series.get(dicomStudy);
            if (seriesList != null) {
                int thumbnailSize = slider.getValue();
                for (int i = 0; i < seriesList.size(); i++) {
                    SeriesPane series = seriesList.get(i);
                    series.updateSize(thumbnailSize);
                    addPane(series, i);
                }
                this.revalidate();
            }
        }

        public void addPane(SeriesPane seriesPane, int index) {
            seriesPane.updateSize(slider.getValue());
            add(seriesPane, index);
            updateText();
        }

        public void updateText() {
            title.setTitle(dicomStudy.toString());
        }

        public boolean isStudy(MediaSeriesGroup dicomStudy) {
            return this.dicomStudy.equals(dicomStudy);
        }
    }

    @SuppressWarnings("serial")
    class SeriesPane extends JPanel {

        final MediaSeriesGroup sequence;
        private final JLabel label;

        public SeriesPane(MediaSeriesGroup sequence) {
            this.sequence = Objects.requireNonNull(sequence);
            // To handle selection color with all L&Fs
            this.setUI(new javax.swing.plaf.PanelUI() {
            });
            this.setOpaque(true);
            this.setBackground(JMVUtils.TREE_BACKROUND);
            this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            int thumbnailSize = slider.getValue();
            if (sequence instanceof Series) {
                Series series = (Series) sequence;
                Thumbnail thumb = (Thumbnail) series.getTagValue(TagW.Thumbnail);
                if (thumb == null) {
                    thumb = createThumbnail(series, model, thumbnailSize);
                    series.setTag(TagW.Thumbnail, thumb);
                }
                Optional.ofNullable(thumb).ifPresent(this::add);
            }
            this.setAlignmentX(LEFT_ALIGNMENT);
            this.setAlignmentY(TOP_ALIGNMENT);
            String desc = TagD.getTagValue(sequence, Tag.SeriesDescription, String.class);
            label = new JLabel(desc == null ? "" : desc, SwingConstants.CENTER); //$NON-NLS-1$
            label.setFont(FontTools.getFont10());
            label.setFocusable(false);
            this.setFocusable(false);
            updateSize(thumbnailSize);
            this.add(label);

        }

        public void updateSize(int thumbnailSize) {
            Dimension max = label.getMaximumSize();
            if (max == null || max.width != thumbnailSize) {
                if (sequence instanceof Series) {
                    Series series = (Series) sequence;
                    SeriesThumbnail thumb = (SeriesThumbnail) series.getTagValue(TagW.Thumbnail);
                    if (thumb != null) {
                        thumb.setThumbnailSize(thumbnailSize);
                    }
                }
                FontRenderContext frc = new FontRenderContext(null, false, false);
                Dimension dim =
                    new Dimension(thumbnailSize, (int) (label.getFont().getStringBounds("0", frc).getHeight() + 1.0f)); //$NON-NLS-1$
                label.setPreferredSize(dim);
                label.setMaximumSize(dim);
            }
        }

        public void updateText() {
            String desc = TagD.getTagValue(sequence, Tag.SeriesDescription, String.class);
            label.setText(desc == null ? "" : desc); //$NON-NLS-1$
        }

        public boolean isSeries(MediaSeriesGroup sequence) {
            return this.sequence.equals(sequence);
        }

        public MediaSeriesGroup getSequence() {
            return sequence;
        }

    }

    /**
     * @return
     */
    protected JPanel getMainPanel() {
        if (panelMain == null) {
            panelMain = new JPanel();
            panel = new JPanel();
            final GridBagLayout gridBagLayout = new GridBagLayout();
            gridBagLayout.rowHeights = new int[] { 0, 0, 7 };
            panel.setLayout(gridBagLayout);
            panel.setBorder(new EmptyBorder(5, 5, 5, 5));

            final JLabel label = new JLabel(PATIENT_ICON);
            final GridBagConstraints gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.insets = new Insets(0, 0, 5, 5);
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 0;
            panel.add(label, gridBagConstraints);

            final GridBagConstraints gridBagConstraints1 = new GridBagConstraints();
            gridBagConstraints1.insets = new Insets(0, 2, 5, 0);
            gridBagConstraints1.anchor = GridBagConstraints.WEST;
            gridBagConstraints1.weightx = 1.0;
            gridBagConstraints1.gridy = 0;
            gridBagConstraints1.gridx = 1;
            panel.add(patientCombobox, gridBagConstraints1);
            patientCombobox.setMaximumRowCount(15);
            patientCombobox.setFont(FontTools.getFont11());
            JMVUtils.setPreferredWidth(patientCombobox, 145, 145);
            // Update UI before adding the Tooltip feature in the combobox list
            patientCombobox.updateUI();
            patientCombobox.addItemListener(patientChangeListener);
            JMVUtils.addTooltipToComboList(patientCombobox);

            final GridBagConstraints gridBagConstraints3 = new GridBagConstraints();
            gridBagConstraints3.anchor = GridBagConstraints.WEST;
            gridBagConstraints3.insets = new Insets(2, 2, 5, 0);
            gridBagConstraints3.gridx = 1;
            gridBagConstraints3.gridy = 1;

            panel.add(studyCombobox, gridBagConstraints3);
            studyCombobox.setMaximumRowCount(15);
            studyCombobox.setFont(FontTools.getFont11());
            // Update UI before adding the Tooltip feature in the combobox list
            studyCombobox.updateUI();
            JMVUtils.setPreferredWidth(studyCombobox, 145, 145);
            // do not use addElement
            modelStudy.insertElementAt(ALL_STUDIES, 0);
            modelStudy.setSelectedItem(ALL_STUDIES);
            studyCombobox.addItemListener(studyItemListener);
            JMVUtils.addTooltipToComboList(studyCombobox);

            final GridBagConstraints gridBagConstraints4 = new GridBagConstraints();
            gridBagConstraints4.anchor = GridBagConstraints.WEST;
            gridBagConstraints4.insets = new Insets(2, 2, 5, 0);
            gridBagConstraints4.gridx = 1;
            gridBagConstraints4.gridy = 2;

            panel.add(koOpen, gridBagConstraints4);
            koOpen.addActionListener(e -> {
                final MediaSeriesGroup patient = selectedPatient == null ? null : selectedPatient.patient;
                if (patient != null && e.getSource() instanceof JButton) {
                    List<KOSpecialElement> list = DicomModel.getSpecialElements(patient, KOSpecialElement.class);
                    if (list != null && !list.isEmpty()) {
                        JButton button = (JButton) e.getSource();

                        if (list.size() == 1) {
                            model.openrelatedSeries(list.get(0), patient);
                        } else {
                            Collections.sort(list, DicomSpecialElement.ORDER_BY_DATE);

                            JPopupMenu popupMenu = new JPopupMenu();
                            popupMenu.add(new TitleMenuItem(ActionW.KO_SELECTION.getTitle(), popupMenu.getInsets()));
                            popupMenu.addSeparator();

                            ButtonGroup group = new ButtonGroup();

                            for (final KOSpecialElement koSpecialElement : list) {
                                final JMenuItem item = new JMenuItem(koSpecialElement.getShortLabel());
                                item.addActionListener(e1 -> model.openrelatedSeries(koSpecialElement, patient));
                                popupMenu.add(item);
                                group.add(item);
                            }
                            popupMenu.show(button, 5, 5);
                        }
                    }
                }
            });
            koOpen.setVisible(false);

            panelMain.setLayout(new BorderLayout());
            panelMain.add(panel, BorderLayout.NORTH);
            JPanel panel2 = new JPanel();

            if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.explorer.moreoptions", true)) { //$NON-NLS-1$
                GridBagConstraints gbcbtnMoreOptions = new GridBagConstraints();
                gbcbtnMoreOptions.anchor = GridBagConstraints.EAST;
                gbcbtnMoreOptions.gridx = 1;
                gbcbtnMoreOptions.gridy = 3;
                btnMoreOptions.setFont(FontTools.getFont10());
                btnMoreOptions.addActionListener(e -> {
                    if (btnMoreOptions.isSelected()) {
                        panelMain.add(panel2);
                    } else {
                        panelMain.remove(panel2);
                    }
                    panelMain.revalidate();
                    panelMain.repaint();
                });
                panel.add(btnMoreOptions, gbcbtnMoreOptions);
            }

            GridBagLayout gblpanel2 = new GridBagLayout();
            panel2.setLayout(gblpanel2);

            JPanel panel3 = new JPanel();
            GridBagConstraints gbcpanel3 = new GridBagConstraints();
            gbcpanel3.weightx = 1.0;
            gbcpanel3.anchor = GridBagConstraints.NORTHWEST;
            gbcpanel3.gridx = 0;
            gbcpanel3.gridy = 0;
            FlowLayout flowLayout = (FlowLayout) panel3.getLayout();
            flowLayout.setHgap(10);
            flowLayout.setAlignment(FlowLayout.LEFT);
            panel2.add(panel3, gbcpanel3);

            final JPanel palenSlider1 = new JPanel();
            palenSlider1.setLayout(new BoxLayout(palenSlider1, BoxLayout.Y_AXIS));
            palenSlider1
                .setBorder(new TitledBorder(Messages.getString("DicomExplorer.thmb_size") + " " + slider.getValue())); //$NON-NLS-1$ //$NON-NLS-2$

            slider.setPaintTicks(true);
            slider.setSnapToTicks(true);
            slider.setMajorTickSpacing(16);
            slider.addChangeListener(e -> {
                JSlider source = (JSlider) e.getSource();
                if (!source.getValueIsAdjusting()) {
                    ((TitledBorder) palenSlider1.getBorder()).setTitle(Messages.getString("DicomExplorer.thmb_size") //$NON-NLS-1$
                        + StringUtil.COLON_AND_SPACE + source.getValue());
                    palenSlider1.repaint();
                    updateThumbnailSize();
                }

            });
            JMVUtils.setPreferredWidth(slider, 145, 145);
            palenSlider1.add(slider);
            GridBagConstraints gbcpalenSlider1 = new GridBagConstraints();
            gbcpalenSlider1.insets = new Insets(0, 5, 5, 5);
            gbcpalenSlider1.anchor = GridBagConstraints.NORTHWEST;
            gbcpalenSlider1.gridx = 0;
            gbcpalenSlider1.gridy = 1;
            panel2.add(palenSlider1, gbcpalenSlider1);
        }
        return panelMain;
    }

    public Set<Series> getSelectedPatientOpenSeries() {
        return getPatientOpenSeries(selectedPatient == null ? null : selectedPatient.patient);
    }

    public Set<Series> getPatientOpenSeries(MediaSeriesGroup patient) {
        Set<Series> openSeriesSet = new LinkedHashSet<>();

        if (patient != null) {
            synchronized (model) {
                for (MediaSeriesGroup study : model.getChildren(patient)) {
                    for (MediaSeriesGroup seq : model.getChildren(study)) {
                        if (seq instanceof Series && Boolean.TRUE.equals(seq.getTagValue(TagW.SeriesOpen))) {
                            openSeriesSet.add((Series) seq);
                        }
                    }
                }
            }
        }
        return openSeriesSet;
    }

    public boolean isPatientHasOpenSeries(MediaSeriesGroup patient) {

        synchronized (model) {
            for (MediaSeriesGroup study : model.getChildren(patient)) {
                for (MediaSeriesGroup seq : model.getChildren(study)) {
                    if (seq instanceof Series) {
                        Boolean open = (Boolean) ((Series) seq).getTagValue(TagW.SeriesOpen);
                        return open == null ? false : open;
                    }
                }
            }
        }
        return false;
    }

    public void selectPatient(MediaSeriesGroup patient) {
        if (selectedPatient == null || patient != selectedPatient.patient) {
            selectionList.clear();
            studyCombobox.removeItemListener(studyItemListener);
            modelStudy.removeAllElements();
            // do not use addElement
            modelStudy.insertElementAt(ALL_STUDIES, 0);
            modelStudy.setSelectedItem(ALL_STUDIES);
            patientContainer.removeAll();
            if (patient == null) {
                selectedPatient = null;
                patientContainer.showAllPatients();
            } else {
                selectedPatient = createPatientPane(patient);
                selectedPatient.showTitle(false);
                List<StudyPane> studies = patient2study.get(patient);
                if (studies != null) {
                    for (StudyPane studyPane : studies) {
                        modelStudy.addElement(studyPane.dicomStudy);
                    }
                }
                studyCombobox.addItemListener(studyItemListener);
                selectStudy();
                patientContainer.addPane(selectedPatient);
                koOpen.setVisible(DicomModel.hasSpecialElements(patient, KOSpecialElement.class));
                // Send message for selecting related plug-ins window
                model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.SELECT, model, null, patient));
            }
        }
    }

    private List<Series> getSplitSeries(Series dcm) {
        MediaSeriesGroup study = model.getParent(dcm, DicomModel.study);
        Object splitNb = dcm.getTagValue(TagW.SplitSeriesNumber);
        List<Series> list = new ArrayList<>();
        if (splitNb == null || study == null) {
            list.add(dcm);
            return list;
        }
        String uid = TagD.getTagValue(dcm, Tag.SeriesInstanceUID, String.class);
        if (uid != null) {
            for (MediaSeriesGroup group : model.getChildren(study)) {
                if (group instanceof Series) {
                    Series s = (Series) group;
                    if (uid.equals(TagD.getTagValue(s, Tag.SeriesInstanceUID))) {
                        list.add(s);
                    }
                }
            }
        }
        return list;
    }

    private void updateSplitSeries(Series dcmSeries) {
        MediaSeriesGroup study = model.getParent(dcmSeries, DicomModel.study);
        // In case the Series has been replaced (split number = -1) and removed
        if (study == null) {
            return;
        }
        StudyPane studyPane = createStudyPaneInstance(study, null);
        List<Series> list = getSplitSeries(dcmSeries);

        List<SeriesPane> seriesList = study2series.get(study);
        if (seriesList == null) {
            seriesList = new ArrayList<>();
            study2series.put(study, seriesList);
        }
        boolean addSeries = patientContainer.isStudyVisible(study);
        boolean repaintStudy = false;
        for (Series dicomSeries : list) {
            int[] positionSeries = new int[1];
            createSeriesPaneInstance(dicomSeries, positionSeries);
            if (addSeries && positionSeries[0] != -1) {
                repaintStudy = true;
            }

            SeriesThumbnail thumb = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
            if (thumb != null) {
                thumb.reBuildThumbnail();
            }
        }

        Integer nb = (Integer) dcmSeries.getTagValue(TagW.SplitSeriesNumber);
        // Convention -> split number inferior to 0 is a Series that has been replaced (ex. when a DicomSeries is
        // converted DicomVideoSeries after downloading files).
        if (nb != null && nb < 0) {
            model.removeSeries(dcmSeries);
            repaintStudy = true;
        }
        if (repaintStudy) {
            studyPane.removeAll();
            for (int i = 0, k = 1; i < seriesList.size(); i++) {
                SeriesPane s = seriesList.get(i);
                studyPane.addPane(s, i);
                if (list.contains(s.getSequence())) {
                    s.getSequence().setTag(TagW.SplitSeriesNumber, k);
                    k++;
                }
            }

            studyPane.revalidate();
            studyPane.repaint();
        } else {
            int k = 1;
            for (SeriesPane s : seriesList) {
                if (list.contains(s.getSequence())) {
                    s.getSequence().setTag(TagW.SplitSeriesNumber, k);
                    k++;
                }
            }
        }

    }

    private void selectStudy() {
        Object item = modelStudy.getSelectedItem();
        if (item instanceof MediaSeriesGroupNode) {
            MediaSeriesGroupNode selectedStudy = (MediaSeriesGroupNode) item;
            selectStudy(selectedStudy);
        } else {
            selectStudy(null);
        }
    }

    public void selectStudy(MediaSeriesGroup selectedStudy) {
        if (selectedPatient != null) {
            selectionList.clear();
            selectedPatient.removeAll();

            if (selectedStudy == null) {
                selectedPatient.showAllstudies();
            } else {
                StudyPane studyPane = getStudyPane(selectedStudy);
                if (studyPane != null) {
                    studyPane.showAllSeries();
                    studyPane.refreshLayout();
                    selectedPatient.addPane(studyPane);
                    studyPane.doLayout();
                }
            }
            selectedPatient.revalidate();
            selectedPatient.repaint();
        }
    }

    @Override
    public void dispose() {
        super.closeDockable();

    }

    private void addDicomSeries(Series series) {
        if (DicomModel.isSpecialModality(series)) {
            // Up to now nothing has to be done in the explorer view about specialModality
            return;
        }
        LOGGER.info("Add series: {}", series); //$NON-NLS-1$
        MediaSeriesGroup study = model.getParent(series, DicomModel.study);
        MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
        final PatientPane patientPane = createPatientPane(patient);

        if (modelPatient.getIndexOf(patient) < 0) {
            modelPatient.addElement(patient);
            if (modelPatient.getSize() == 1) {
                selectedPatient = patientPane;
                patientContainer.addPane(selectedPatient);
                patientCombobox.removeItemListener(patientChangeListener);
                patientCombobox.setSelectedItem(patient);
                patientCombobox.addItemListener(patientChangeListener);
            }
            // Mode all patients
            else if (selectedPatient == null) {
                patientContainer.addPane(patientPane);
            }
        }
        boolean addSeries = selectedPatient == patientPane;
        List<StudyPane> studies = patient2study.computeIfAbsent(patient, k -> new ArrayList<>());
        Object selectedStudy = modelStudy.getSelectedItem();
        int[] positionStudy = new int[1];
        StudyPane studyPane = createStudyPaneInstance(study, positionStudy);

        List<SeriesPane> seriesList = study2series.computeIfAbsent(study, k -> new ArrayList<>());
        int[] positionSeries = new int[1];
        createSeriesPaneInstance(series, positionSeries);
        if (addSeries && positionSeries[0] != -1) {
            // If new study
            if (positionStudy[0] != -1) {
                if (modelStudy.getIndexOf(study) < 0) {
                    modelStudy.addElement(study);
                }
                // if modelStudy has the value "All studies"
                if (ALL_STUDIES.equals(selectedStudy)) {
                    patientPane.removeAll();
                    for (StudyPane s : studies) {
                        patientPane.addPane(s);
                    }
                    patientPane.revalidate();
                }
            }
            if (patientPane.isStudyVisible(study)) {
                studyPane.removeAll();
                for (int i = 0; i < seriesList.size(); i++) {
                    studyPane.addPane(seriesList.get(i), i);
                }
                studyPane.revalidate();
                studyPane.repaint();
            }
        }
    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        EVENT type = event.getEventType();
        if (EVENT.SELECT_VIEW.equals(type) && event.getSeriesViewer() instanceof ImageViewerPlugin) {
            ViewCanvas<?> pane = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane();
            if (pane != null) {
                MediaSeries s = pane.getSeries();
                if (s != null) {
                    if (!getSelectionList().isOpenningSeries() && patientContainer.isSeriesVisible(s)) {
                        SeriesPane p = getSeriesPane(s);
                        if (p != null) {
                            JViewport vp = thumnailView.getViewport();
                            Rectangle bound = vp.getViewRect();
                            Point ptmin = SwingUtilities.convertPoint(p, new Point(0, 0), patientContainer);
                            Point ptmax = SwingUtilities.convertPoint(p, new Point(0, p.getHeight()), patientContainer);
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
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // Get only event from the model of DicomExplorer
        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            ObservableEvent.BasicAction action = event.getActionCommand();
            Object newVal = event.getNewValue();
            if (model.equals(evt.getSource())) {
                if (ObservableEvent.BasicAction.SELECT.equals(action)) {
                    if (newVal instanceof Series) {
                        Series dcm = (Series) newVal;
                        MediaSeriesGroup patient = model.getParent(dcm, DicomModel.patient);
                        if (!isSelectedPatient(patient)) {
                            modelPatient.setSelectedItem(patient);
                        }
                    }
                } else if (ObservableEvent.BasicAction.ADD.equals(action)) {
                    if (newVal instanceof Series) {
                        addDicomSeries((Series) newVal);
                    }
                } else if (ObservableEvent.BasicAction.REMOVE.equals(action)) {
                    if (newVal instanceof MediaSeriesGroup) {
                        MediaSeriesGroup group = (MediaSeriesGroup) newVal;
                        // Patient Group
                        if (TagD.getUID(Level.PATIENT).equals(group.getTagID())) {
                            removePatientPane(group);
                        }
                        // Study Group
                        else if (TagD.getUID(Level.STUDY).equals(group.getTagID())) {
                            removeStudy(group);
                        }
                        // Series Group
                        else if (TagD.getUID(Level.SERIES).equals(group.getTagID())) {
                            removeSeries(group);
                        }
                    }
                }
                // Update patient and study infos from the series (when receiving the first downloaded image)
                else if (ObservableEvent.BasicAction.UDPATE_PARENT.equals(action)) {
                    if (newVal instanceof Series) {
                        Series dcm = (Series) newVal;
                        MediaSeriesGroup patient = model.getParent(dcm, DicomModel.patient);
                        if (isSelectedPatient(patient)) {
                            MediaSeriesGroup study = model.getParent(dcm, DicomModel.study);
                            StudyPane studyPane = getStudyPane(study);
                            if (studyPane != null) {
                                if (!DicomModel.isSpecialModality(dcm)) {
                                    SeriesPane pane = getSeriesPane(dcm);
                                    if (pane != null) {
                                        pane.updateText();
                                    }
                                }
                                studyPane.updateText();
                            }
                        }
                    }
                }
                // update
                else if (ObservableEvent.BasicAction.UPDATE.equals(action)) {
                    if (newVal instanceof Series) {
                        Series series = (Series) newVal;
                        Integer splitNb = (Integer) series.getTagValue(TagW.SplitSeriesNumber);
                        if (splitNb != null) {
                            updateSplitSeries(series);
                        }
                    } else if (newVal instanceof KOSpecialElement) {
                        Object item = modelPatient.getSelectedItem();
                        if (item instanceof MediaSeriesGroupNode) {
                            koOpen.setVisible(
                                DicomModel.hasSpecialElements((MediaSeriesGroup) item, KOSpecialElement.class));
                        }
                    }
                } else if (ObservableEvent.BasicAction.LOADING_START.equals(action)) {
                    if (newVal instanceof ExplorerTask) {
                        addTaskToGlobalProgression((ExplorerTask<?, ?>) newVal);
                    }
                } else if (ObservableEvent.BasicAction.LOADING_STOP.equals(action)
                    || ObservableEvent.BasicAction.LOADING_CANCEL.equals(action)) {
                    if (newVal instanceof ExplorerTask) {
                        removeTaskToGlobalProgression((ExplorerTask<?, ?>) newVal);
                    }
                    Object item = modelPatient.getSelectedItem();
                    if (item instanceof MediaSeriesGroupNode) {
                        koOpen
                            .setVisible(DicomModel.hasSpecialElements((MediaSeriesGroup) item, KOSpecialElement.class));
                    }
                }
            } else if (evt.getSource() instanceof SeriesViewer) {
                if (ObservableEvent.BasicAction.SELECT.equals(action)) {
                    if (newVal instanceof MediaSeriesGroup) {
                        MediaSeriesGroup patient = (MediaSeriesGroup) newVal;
                        if (!isSelectedPatient(patient)) {
                            modelPatient.setSelectedItem(patient);
                            // focus get back to viewer
                            if (evt.getSource() instanceof ViewerPlugin) {
                                ((ViewerPlugin) evt.getSource()).requestFocusInWindow();
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Icon getIcon() {
        // TODO Auto-generated method stub
        return null;
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
    public DataExplorerModel getDataExplorerModel() {
        return model;
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        removeAll();
        add(getMainPanel(), verticalLayout ? BorderLayout.NORTH : BorderLayout.WEST);
        patientContainer.refreshLayout();
        add(thumnailView, BorderLayout.CENTER);
        add(loadingPanel, verticalLayout ? BorderLayout.SOUTH : BorderLayout.EAST);
    }

    public synchronized void addTaskToGlobalProgression(final ExplorerTask task) {
        GuiExecutor.instance().invokeAndWait(() -> {
            loadingPanel.addTask(task);
            revalidate();
            repaint();
        });

    }

    public synchronized void removeTaskToGlobalProgression(final ExplorerTask task) {
        GuiExecutor.instance().invokeAndWait(() -> {
            if (loadingPanel.removeTask(task)) {
                revalidate();
                repaint();
            }
        });
    }

    public static SeriesThumbnail createThumbnail(final Series series, final DicomModel dicomModel,
        final int thumbnailSize) {

        Callable<SeriesThumbnail> callable = () -> {
            final SeriesThumbnail thumb = new SeriesThumbnail(series, thumbnailSize);
            if (series.getSeriesLoader() instanceof LoadSeries) {
                // In case series is downloaded or canceled
                LoadSeries loader = (LoadSeries) series.getSeriesLoader();
                thumb.setProgressBar(loader.isDone() ? null : loader.getProgressBar());
            }
            thumb.registerListeners();
            ThumbnailMouseAndKeyAdapter thumbAdapter = new ThumbnailMouseAndKeyAdapter(series, dicomModel, null);
            thumb.addMouseListener(thumbAdapter);
            thumb.addKeyListener(thumbAdapter);
            return thumb;
        };
        FutureTask<SeriesThumbnail> future = new FutureTask<>(callable);
        GuiExecutor.instance().invokeAndWait(future);
        SeriesThumbnail result = null;
        try {
            result = future.get();
        } catch (InterruptedException e) {
            LOGGER.warn("Building Series thumbnail task Interruption"); //$NON-NLS-1$
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            LOGGER.error("Building Series thumbnail task", e); //$NON-NLS-1$
        }
        return result;
    }

    @Override
    public void importFiles(File[] files, boolean recursive) {
        if (files != null) {
            DicomModel.LOADING_EXECUTOR.execute(new LoadLocalDicom(files, recursive, model));
        }
    }

    @Override
    public List<Action> getOpenExportDialogAction() {
        return Arrays.asList(ExportToolBar.buildExportAction(this, model, BUTTON_NAME));
    }

    @Override
    public List<Action> getOpenImportDialogAction() {
        ArrayList<Action> actions = new ArrayList<>(2);
        actions.add(ImportToolBar.buildImportAction(this, model, BUTTON_NAME));
        DefaultAction importCDAction = new DefaultAction(Messages.getString("DicomExplorer.dcmCD"), //$NON-NLS-1$
            new ImageIcon(DicomExplorer.class.getResource("/icon/16x16/cd.png")), //$NON-NLS-1$
            event -> ImportToolBar.openImportDicomCdAction(this, model, Messages.getString("DicomExplorer.dcmCD"))); //$NON-NLS-1$
        actions.add(importCDAction);
        return actions;
    }

    @Override
    public boolean canImportFiles() {
        return true;
    }
}
