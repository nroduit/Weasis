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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

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
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.graphic.model.GraphicList;
import org.weasis.core.ui.serialize.DefaultSerializer;
import org.weasis.dicom.codec.DicomMediaIO;

public class LoadLocalDicom extends ExplorerTask {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LoadLocalDicom.class);
    private final File[] files;
    private final DicomModel dicomModel;
    private final boolean recursive;
    private boolean openPlugin;

    public LoadLocalDicom(File[] files, boolean recursive, DataExplorerModel explorerModel) {
        super(Messages.getString("DicomExplorer.loading")); //$NON-NLS-1$
        if (files == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        this.dicomModel = (DicomModel) explorerModel;
        this.files = files;
        this.recursive = recursive;
        this.openPlugin = true;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStart, dicomModel, null,
            this));
        addSelectionAndnotify(files, true);
        return true;
    }

    @Override
    protected void done() {
        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStop, dicomModel, null,
            this));
        writeInfo(Messages.getString("LoadLocalDicom.end")); //$NON-NLS-1$
    }

    private void writeInfo(String text) {
        log.info(text);
    }

    public void addSelectionAndnotify(File[] file, boolean firstLevel) {
        if (file == null || file.length < 1) {
            return;
        }
        final ArrayList<SeriesThumbnail> thumbs = new ArrayList<SeriesThumbnail>();
        final ArrayList<File> folders = new ArrayList<File>();
        for (int i = 0; i < file.length; i++) {
            if (file[i] == null) {
                continue;
            } else if (file[i].isDirectory()) {
                if (firstLevel || recursive) {
                    folders.add(file[i]);
                }
            } else {
                if (file[i].canRead()) {
                    if (MimeInspector.isMatchingMimeTypeFromMagicNumber(file[i], DicomMediaIO.MIMETYPE)) {
                        DicomMediaIO loader = new DicomMediaIO(file[i]);
                        if (loader.isReadableDicom()) {
                            // Issue: must handle adding image to viewer and building thumbnail (middle image)
                            SeriesThumbnail t = buildDicomStructure(loader, openPlugin);
                            if (t != null) {
                                thumbs.add(t);
                            }

                            File gpxFile = new File(file[i].getPath() + ".xml");

                            if (gpxFile.canRead()) {
                                try {
                                    GraphicList list =
                                        DefaultSerializer.getInstance().getSerializer()
                                            .read(GraphicList.class, gpxFile);
                                    loader.setTag(TagW.MeasurementGraphics, list);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }
        for (final SeriesThumbnail t : thumbs) {
            MediaSeries series = t.getSeries();
            // Avoid to rebuild most of CR series thumbnail
            if (series != null && series.size(null) > 2) {
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

    private SeriesThumbnail buildDicomStructure(DicomMediaIO dicomReader, boolean open) {
        SeriesThumbnail thumb = null;
        String patientPseudoUID = (String) dicomReader.getTagValue(TagW.PatientPseudoUID);
        MediaSeriesGroup patient = dicomModel.getHierarchyNode(TreeModel.rootNode, patientPseudoUID);
        if (patient == null) {
            patient = new MediaSeriesGroupNode(TagW.PatientPseudoUID, patientPseudoUID, TagW.PatientName);
            dicomReader.writeMetaData(patient);
            dicomModel.addHierarchyNode(TreeModel.rootNode, patient);
            writeInfo(Messages.getString("LoadLocalDicom.add_pat") + " " + patient); //$NON-NLS-1$ //$NON-NLS-2$
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
                        dicomModel.applySplittingRules(dicomSeries, media);
                    }
                }
                if (medias.length > 0) {
                    dicomSeries.setFileSize(dicomSeries.getFileSize() + medias[0].getLength());
                }

                // Load image and create thumbnail in this Thread
                SeriesThumbnail t = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                if (t == null) {
                    t = DicomExplorer.createThumbnail(dicomSeries, dicomModel, Thumbnail.DEFAULT_SIZE);
                    dicomSeries.setTag(TagW.Thumbnail, t);
                }

                if (DicomModel.isSpecialModality(dicomSeries)) {
                    dicomModel.addSpecialModality(dicomSeries);
                } else {
                    dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, dicomModel,
                        null, dicomSeries));
                }

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
                        ViewerPluginBuilder.openSequenceInPlugin(plugin, dicomSeries, dicomModel, true, true);
                    }
                }
            } else {
                // Test if SOPInstanceUID already exists
                if (isSOPInstanceUIDExist(study, dicomSeries, seriesUID, dicomReader.getTagValue(TagW.SOPInstanceUID))) {
                    return null;
                }
                MediaElement[] medias = dicomReader.getMediaElement();
                if (medias != null) {
                    for (MediaElement media : medias) {
                        dicomModel.applySplittingRules(dicomSeries, media);
                    }
                    if (medias.length > 0) {
                        dicomSeries.setFileSize(dicomSeries.getFileSize() + medias[0].getLength());
                        // Refresh the number of images on the thumbnail
                        Thumbnail t = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                        if (t != null) {
                            t.repaint();
                        }
                    }

                    if (DicomModel.isSpecialModality(dicomSeries)) {
                        dicomModel.addSpecialModality(dicomSeries);
                    }

                    // If Split series or special DICOM element update the explorer view and View2DContainer
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
            // dicomReader.reset();
        }
        return thumb;
    }

    private boolean isSOPInstanceUIDExist(MediaSeriesGroup study, Series dicomSeries, String seriesUID, Object sopUID) {
        if (dicomSeries.hasMediaContains(TagW.SOPInstanceUID, sopUID)) {
            return true;
        }
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
                            if (s.hasMediaContains(TagW.SOPInstanceUID, sopUID)) {
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
