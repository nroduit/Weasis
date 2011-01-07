/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.explorer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagElement;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class LoadLocalDicom extends SwingWorker<Boolean, String> {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LoadLocalDicom.class);
    private final File[] files;
    private final DicomModel dicomModel;
    private final boolean recursive;
    private boolean openPlugin;
    private JProgressBar progressBar = null;

    public LoadLocalDicom(File[] files, boolean recursive, DataExplorerModel explorerModel) {
        if (files == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        this.dicomModel = (DicomModel) explorerModel;
        this.files = files;
        this.recursive = recursive;
        this.openPlugin = true;
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    public void setProgressBar(JProgressBar progressBar) {
        this.progressBar = progressBar;
        if (progressBar != null) {
            progressBar.setIndeterminate(true);
            progressBar.setStringPainted(true);
        }
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        addSelection(files, true);
        return true;
    }

    @Override
    protected void done() {
        if (progressBar != null) {
            progressBar.setIndeterminate(false);
        }
        writeInfo(Messages.getString("LoadLocalDicom.end")); //$NON-NLS-1$
    }

    private void writeInfo(String text) {
        if (progressBar != null) {
            progressBar.setString(text);
        }
        log.info(text);
    }

    public void addSelection(File[] file, boolean firstLevel) {
        if (file == null || file.length < 1) {
            return;
        }
        final ArrayList<DicomMediaIO> files = new ArrayList<DicomMediaIO>();
        final ArrayList<File> folders = new ArrayList<File>();
        for (int i = 0; i < file.length; i++) {
            if (file[i].isDirectory()) {
                if (firstLevel || recursive) {
                    folders.add(file[i]);
                }
            } else {
                if (file[i].canRead()) {
                    String mime = null;
                    try {
                        mime = MimeInspector.getMimeType(file[i]);
                    } catch (IOException e) {
                    }
                    if (DicomMediaIO.MIMETYPE.equals(mime)) {
                        DicomMediaIO loader = new DicomMediaIO(file[i]);
                        if (loader.readMediaTags()) {
                            // Issue: must handle adding image to viewer and building thumbnail (middle image)
                            // buildDicomStructure(loader, openPlugin);
                            files.add(loader);
                        }
                    }
                }
            }
        }
        boolean hasImages = files.size() > 0;
        if (hasImages) {
            buildDicomStructure(files, openPlugin & hasImages);
        }
        for (int i = 0; i < folders.size(); i++) {
            addSelection(folders.get(i).listFiles(), false);
        }
    }

    private void buildDicomStructure(DicomMediaIO dicomReader, boolean open) {

        String patientPseudoUID = (String) dicomReader.getTagValue(TagElement.PatientPseudoUID);
        MediaSeriesGroup patient = dicomModel.getHierarchyNode(TreeModel.rootNode, patientPseudoUID);
        if (patient == null) {
            patient = new MediaSeriesGroupNode(TagElement.PatientPseudoUID, patientPseudoUID, TagElement.PatientName);
            dicomReader.writeMetaData(patient);
            dicomModel.addHierarchyNode(TreeModel.rootNode, patient);
            writeInfo(Messages.getString("LoadLocalDicom.add_pat") + patient); //$NON-NLS-1$
        }

        String studyUID = (String) dicomReader.getTagValue(TagElement.StudyInstanceUID);
        MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, studyUID);
        if (study == null) {
            study = new MediaSeriesGroupNode(TagElement.StudyInstanceUID, studyUID, TagElement.StudyDate);
            dicomReader.writeMetaData(study);
            dicomModel.addHierarchyNode(patient, study);
        }

        String seriesUID = (String) dicomReader.getTagValue(TagElement.SeriesInstanceUID);
        Series dicomSeries = (Series) dicomModel.getHierarchyNode(study, seriesUID);
        try {
            if (dicomSeries == null) {
                dicomSeries = dicomReader.buildSeries(seriesUID);
                dicomSeries.setTag(TagElement.ExplorerModel, dicomModel);
                dicomReader.writeMetaData(dicomSeries);
                dicomModel.addHierarchyNode(study, dicomSeries);
                dicomSeries.addMedia(dicomReader);

                // Load image and create thumbnail in this Thread
                Thumbnail thumb = (Thumbnail) dicomSeries.getTagValue(TagElement.Thumbnail);
                if (thumb == null) {
                    thumb = DicomExplorer.createThumbnail(dicomSeries, dicomModel, Thumbnail.DEFAULT_SIZE);
                    dicomSeries.setTag(TagElement.Thumbnail, thumb);
                }

                dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, dicomModel, null,
                    dicomSeries));
                Integer splitNb = (Integer) dicomSeries.getTagValue(TagElement.SplitSeriesNumber);
                Object dicomObject = dicomSeries.getTagValue(TagElement.DicomSpecialElement);
                if (splitNb != null || dicomObject != null) {
                    dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Update, dicomModel,
                        null, dicomSeries));
                }
                if (open) {
                    SeriesViewerFactory plugin = UIManager.getViewerFactory(dicomSeries.getMimeType());
                    if (plugin != null) {
                        openPlugin = false;
                        LoadSeries.openSequenceInPlugin(plugin, new Series[] { dicomSeries }, dicomModel);
                    }
                }
            } else {
                dicomModel.applySplittingRules(dicomSeries, dicomReader);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        } finally {
            dicomReader.reset();
        }
    }

    private void buildDicomStructure(ArrayList<DicomMediaIO> seriesList, boolean open) {
        ArrayList<Series> dicomseriesList = new ArrayList<Series>();
        seriesList: for (DicomMediaIO dicomReader : seriesList) {
            String patientPseudoUID = (String) dicomReader.getTagValue(TagElement.PatientPseudoUID);
            MediaSeriesGroup patient = dicomModel.getHierarchyNode(TreeModel.rootNode, patientPseudoUID);
            if (patient == null) {
                patient =
                    new MediaSeriesGroupNode(TagElement.PatientPseudoUID, patientPseudoUID, TagElement.PatientName);
                dicomReader.writeMetaData(patient);
                dicomModel.addHierarchyNode(TreeModel.rootNode, patient);
                writeInfo(Messages.getString("LoadLocalDicom.add_pat") + patient); //$NON-NLS-1$
            }

            String studyUID = (String) dicomReader.getTagValue(TagElement.StudyInstanceUID);
            MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, studyUID);
            if (study == null) {
                study = new MediaSeriesGroupNode(TagElement.StudyInstanceUID, studyUID, TagElement.StudyDate);
                dicomReader.writeMetaData(study);
                dicomModel.addHierarchyNode(patient, study);
            }

            String seriesUID = (String) dicomReader.getTagValue(TagElement.SeriesInstanceUID);
            Series dicomSeries = (Series) dicomModel.getHierarchyNode(study, seriesUID);
            try {
                if (dicomSeries == null) {
                    dicomSeries = dicomReader.buildSeries(seriesUID);
                    dicomSeries.setTag(TagElement.ExplorerModel, dicomModel);
                    dicomReader.writeMetaData(dicomSeries);
                    dicomModel.addHierarchyNode(study, dicomSeries);
                    dicomseriesList.add(dicomSeries);
                    dicomSeries.addMedia(dicomReader);
                } else {
                    // Test if SOPInstanceUID already exists
                    if (isSOPInstanceUIDExist(study, dicomSeries, seriesUID,
                        dicomReader.getTagValue(TagElement.SOPInstanceUID))) {
                        continue seriesList;
                    }
                    dicomModel.applySplittingRules(dicomSeries, dicomReader);
                }
            } catch (Exception e) {
                dicomReader.reset();
                e.printStackTrace();
            }
        }

        for (int i = 0; i < dicomseriesList.size(); i++) {
            final Series series = dicomseriesList.get(i);
            // Load image and create thumbnail in this Thread
            Thumbnail thumb = (Thumbnail) series.getTagValue(TagElement.Thumbnail);
            if (thumb == null) {
                thumb = DicomExplorer.createThumbnail(series, dicomModel, Thumbnail.DEFAULT_SIZE);
                series.setTag(TagElement.Thumbnail, thumb);
            }
            series.resetLoaders();
            dicomModel
                .firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, dicomModel, null, series));
            Integer splitNb = (Integer) series.getTagValue(TagElement.SplitSeriesNumber);
            Object dicomObject = series.getTagValue(TagElement.DicomSpecialElement);
            if (splitNb != null || dicomObject != null) {
                dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Update, dicomModel, null,
                    series));
            }
        }

        if (open && dicomseriesList.size() > 0) {
            final Series series = dicomseriesList.get(0);
            SeriesViewerFactory plugin = UIManager.getViewerFactory(series.getMimeType());
            if (plugin != null) {
                openPlugin = false;
                LoadSeries.openSequenceInPlugin(plugin, new Series[] { series }, dicomModel);
            }
        }
    }

    private boolean isSOPInstanceUIDExist(MediaSeriesGroup study, Series dicomSeries, String seriesUID, Object sopUID) {
        if (dicomSeries.hasMediaContains(TagElement.SOPInstanceUID, sopUID)) {
            return true;
        }
        Object splitNb = dicomSeries.getTagValue(TagElement.SplitSeriesNumber);
        if (splitNb != null && study != null) {
            String uid = (String) dicomSeries.getTagValue(TagElement.SeriesInstanceUID);
            if (uid != null) {
                Collection<MediaSeriesGroup> seriesList = dicomModel.getChildren(study);
                for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                    MediaSeriesGroup group = it.next();
                    if (group instanceof DicomSeries) {
                        DicomSeries s = (DicomSeries) group;
                        if (uid.equals(s.getTagValue(TagElement.SeriesInstanceUID))) {
                            if (s.hasMediaContains(TagElement.SOPInstanceUID, sopUID)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
