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
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class LoadLocalDicom extends SwingWorker<Boolean, String> {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LoadLocalDicom.class);
    private final File[] files;
    private final DicomModel dicomModel;
    private final boolean recursive;
    private boolean openPlugin;
    private final boolean flatSearch;
    private JProgressBar progressBar = null;

    public LoadLocalDicom(File[] files, boolean recursive, DataExplorerModel explorerModel, boolean flatSearch) {
        if (files == null || !(explorerModel instanceof DicomModel))
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        this.dicomModel = (DicomModel) explorerModel;
        this.files = files;
        this.recursive = recursive;
        this.openPlugin = true;
        this.flatSearch = flatSearch;
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
        // if (flatSearch) {
        addSelectionAndnotify(files, true);
        // Issue on linux to many files opened
        // } else {
        // addSelection(files, true);
        // }
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

    public void addSelectionAndnotify(File[] file, boolean firstLevel) {
        if (file == null || file.length < 1)
            return;
        final ArrayList<Thumbnail> thumbs = new ArrayList<Thumbnail>();
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
                            Thumbnail t = buildDicomStructure(loader, openPlugin);
                            if (t != null) {
                                thumbs.add(t);
                            }
                        }
                    }
                }
            }
        }
        for (final Thumbnail t : thumbs) {
            MediaSeries series = t.getSeries();
            // Avoid to rebuild most of CR series thumbnail
            if (series != null && series.getMedias().size() > 2) {
                GuiExecutor.instance().execute(new Runnable() {

                    @Override
                    public void run() {
                        t.reBuildThumbnail();
                    }

                });
            }
        }
        for (int i = 0; i < folders.size(); i++) {
            addSelectionAndnotify(folders.get(i).listFiles(), false);
        }
    }

    public void addSelection(File[] file, boolean firstLevel) {
        if (file == null || file.length < 1)
            return;
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

    private Thumbnail buildDicomStructure(DicomMediaIO dicomReader, boolean open) {
        Thumbnail thumb = null;
        String patientPseudoUID = (String) dicomReader.getTagValue(TagW.PatientPseudoUID);
        MediaSeriesGroup patient = dicomModel.getHierarchyNode(TreeModel.rootNode, patientPseudoUID);
        if (patient == null) {
            patient = new MediaSeriesGroupNode(TagW.PatientPseudoUID, patientPseudoUID, TagW.PatientName);
            dicomReader.writeMetaData(patient);
            dicomModel.addHierarchyNode(TreeModel.rootNode, patient);
            writeInfo(Messages.getString("LoadLocalDicom.add_pat") + patient); //$NON-NLS-1$
        }

        String studyUID = (String) dicomReader.getTagValue(TagW.StudyInstanceUID);
        MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, studyUID);
        if (study == null) {
            study = new MediaSeriesGroupNode(TagW.StudyInstanceUID, studyUID, TagW.StudyDate);
            dicomReader.writeMetaData(study);
            dicomModel.addHierarchyNode(patient, study);
        }

        String seriesUID = (String) dicomReader.getTagValue(TagW.SeriesInstanceUID);
        Series dicomSeries = (Series) dicomModel.getHierarchyNode(study, seriesUID);
        try {
            if (dicomSeries == null) {
                dicomSeries = dicomReader.buildSeries(seriesUID);
                dicomSeries.setTag(TagW.ExplorerModel, dicomModel);
                dicomReader.writeMetaData(dicomSeries);
                dicomModel.addHierarchyNode(study, dicomSeries);
                MediaElement[] medias = dicomReader.getMediaElement();
                if (medias != null) {
                    for (MediaElement media : medias) {
                        dicomSeries.setFileSize(dicomSeries.getFileSize() + media.getLength());
                        dicomModel.applySplittingRules(dicomSeries, media);
                    }
                }

                // Load image and create thumbnail in this Thread
                Thumbnail t = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                if (t == null) {
                    t = DicomExplorer.createThumbnail(dicomSeries, dicomModel, Thumbnail.DEFAULT_SIZE);
                    dicomSeries.setTag(TagW.Thumbnail, t);
                }

                dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, dicomModel, null,
                    dicomSeries));
                // After the thumbnail is sent to interface, it will be return to be rebuilt later
                thumb = t;
                Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
                Object dicomObject = dicomSeries.getTagValue(TagW.DicomSpecialElement);
                if (splitNb != null || dicomObject != null) {
                    dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Update, dicomModel,
                        null, dicomSeries));
                }
                if (open) {
                    SeriesViewerFactory plugin = UIManager.getViewerFactory(dicomSeries.getMimeType());
                    if (plugin != null && !(plugin instanceof MimeSystemAppFactory)) {
                        openPlugin = false;
                        ArrayList<MediaSeries> list = new ArrayList<MediaSeries>(1);
                        list.add(dicomSeries);
                        LoadSeries.openSequenceInPlugin(plugin, list, dicomModel, true);
                    }
                }
            } else {
                MediaElement[] medias = dicomReader.getMediaElement();
                if (medias != null) {
                    for (MediaElement media : medias) {
                        dicomSeries.setFileSize(dicomSeries.getFileSize() + media.getLength());
                        dicomModel.applySplittingRules(dicomSeries, media);
                    }
                    if (medias.length > 0) {
                        // Refresh the number of images on the thumbnail
                        Thumbnail t = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                        if (t != null) {
                            t.repaint();
                        }
                    }
                    // If Split series or special DICOM element update the explorer view
                    Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
                    Object dicomObject = dicomSeries.getTagValue(TagW.DicomSpecialElement);
                    if (splitNb != null || dicomObject != null) {
                        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Update,
                            dicomModel, null, dicomSeries));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dicomReader.reset();
        }
        return thumb;
    }

    private void buildDicomStructure(ArrayList<DicomMediaIO> seriesList, boolean open) {
        ArrayList<Series> dicomseriesList = new ArrayList<Series>();
        seriesList: for (DicomMediaIO dicomReader : seriesList) {
            String patientPseudoUID = (String) dicomReader.getTagValue(TagW.PatientPseudoUID);
            MediaSeriesGroup patient = dicomModel.getHierarchyNode(TreeModel.rootNode, patientPseudoUID);
            if (patient == null) {
                patient = new MediaSeriesGroupNode(TagW.PatientPseudoUID, patientPseudoUID, TagW.PatientName);
                dicomReader.writeMetaData(patient);
                dicomModel.addHierarchyNode(TreeModel.rootNode, patient);
                writeInfo(Messages.getString("LoadLocalDicom.add_pat") + patient); //$NON-NLS-1$
            }

            String studyUID = (String) dicomReader.getTagValue(TagW.StudyInstanceUID);
            MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, studyUID);
            if (study == null) {
                study = new MediaSeriesGroupNode(TagW.StudyInstanceUID, studyUID, TagW.StudyDate);
                dicomReader.writeMetaData(study);
                dicomModel.addHierarchyNode(patient, study);
            }

            String seriesUID = (String) dicomReader.getTagValue(TagW.SeriesInstanceUID);
            Series dicomSeries = (Series) dicomModel.getHierarchyNode(study, seriesUID);
            try {
                if (dicomSeries == null) {
                    dicomSeries = dicomReader.buildSeries(seriesUID);
                    dicomSeries.setTag(TagW.ExplorerModel, dicomModel);
                    dicomReader.writeMetaData(dicomSeries);
                    dicomModel.addHierarchyNode(study, dicomSeries);
                    dicomseriesList.add(dicomSeries);
                    MediaElement[] medias = dicomReader.getMediaElement();
                    if (medias != null) {
                        for (MediaElement media : medias) {
                            dicomSeries.setFileSize(dicomSeries.getFileSize() + media.getLength());
                            dicomModel.applySplittingRules(dicomSeries, media);
                        }
                    }
                } else {
                    // Test if SOPInstanceUID already exists
                    if (isSOPInstanceUIDExist(study, dicomSeries, seriesUID,
                        dicomReader.getTagValue(TagW.SOPInstanceUID))) {
                        continue seriesList;
                    }
                    MediaElement[] medias = dicomReader.getMediaElement();
                    if (medias != null) {
                        for (MediaElement media : medias) {
                            dicomSeries.setFileSize(dicomSeries.getFileSize() + media.getLength());
                            if (dicomModel.applySplittingRules(dicomSeries, media)) {
                                // When the Series is split, build a thumbnail and add it to the dicom explorer
                                dicomseriesList.add(dicomSeries);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                dicomReader.reset();
                e.printStackTrace();
            }
        }

        for (int i = 0; i < dicomseriesList.size(); i++) {
            final Series series = dicomseriesList.get(i);
            // Load image and create thumbnail in this Thread
            Thumbnail thumb = (Thumbnail) series.getTagValue(TagW.Thumbnail);
            if (thumb == null) {
                thumb = DicomExplorer.createThumbnail(series, dicomModel, Thumbnail.DEFAULT_SIZE);
                series.setTag(TagW.Thumbnail, thumb);
            }
            series.resetLoaders();
            dicomModel
                .firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, dicomModel, null, series));
            Integer splitNb = (Integer) series.getTagValue(TagW.SplitSeriesNumber);
            Object dicomObject = series.getTagValue(TagW.DicomSpecialElement);
            if (splitNb != null || dicomObject != null) {
                dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Update, dicomModel, null,
                    series));
            }
        }

        if (open && dicomseriesList.size() > 0) {
            final Series series = dicomseriesList.get(0);
            SeriesViewerFactory plugin = UIManager.getViewerFactory(series.getMimeType());
            if (plugin != null && !(plugin instanceof MimeSystemAppFactory)) {
                openPlugin = false;
                ArrayList<MediaSeries> list = new ArrayList<MediaSeries>(1);
                list.add(series);
                LoadSeries.openSequenceInPlugin(plugin, list, dicomModel, true);
            }
        }
    }

    private boolean isSOPInstanceUIDExist(MediaSeriesGroup study, Series dicomSeries, String seriesUID, Object sopUID) {
        if (dicomSeries.hasMediaContains(TagW.SOPInstanceUID, sopUID))
            return true;
        Object splitNb = dicomSeries.getTagValue(TagW.SplitSeriesNumber);
        if (splitNb != null && study != null) {
            String uid = (String) dicomSeries.getTagValue(TagW.SeriesInstanceUID);
            if (uid != null) {
                Collection<MediaSeriesGroup> seriesList = dicomModel.getChildren(study);
                for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                    MediaSeriesGroup group = it.next();
                    if (dicomSeries != group && group instanceof Series) {
                        Series s = (Series) group;
                        if (uid.equals(s.getTagValue(TagW.SeriesInstanceUID))) {
                            if (s.hasMediaContains(TagW.SOPInstanceUID, sopUID))
                                return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
