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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.dcm4che3.data.Attributes;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.GuiExecutor;
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
import org.weasis.dicom.codec.DicomMediaIO;

/**
 * @note This class is a pure copy of LoadLocalDicom taking care only of the DicomObject and not the file
 * 
 * @version $Rev$ $Date$
 */

public class LoadDicomObjects extends ExplorerTask {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LoadDicomObjects.class);
    private final Attributes[] dicomObjectsToLoad;
    private final DicomModel dicomModel;

    private boolean openPlugin = true;

    public LoadDicomObjects(DataExplorerModel explorerModel, Attributes... dcmObjects) {
        super(Messages.getString("DicomExplorer.loading"), false); //$NON-NLS-1$

        if (dcmObjects == null || dcmObjects.length < 1 || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }

        this.dicomModel = (DicomModel) explorerModel;
        this.dicomObjectsToLoad = dcmObjects;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStart, dicomModel, null,
            this));
        addSelectionAndnotify();
        return true;
    }

    @Override
    protected void done() {
        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStop, dicomModel, null,
            this));
        writeInfo(Messages.getString("LoadDicomObjects.end")); //$NON-NLS-1$
    }

    private void writeInfo(String text) {
        log.info(text);
    }

    public void addSelectionAndnotify() {

        openPlugin = true;

        final ArrayList<SeriesThumbnail> thumbs = new ArrayList<SeriesThumbnail>(dicomObjectsToLoad.length);

        for (Attributes dicom : dicomObjectsToLoad) {
            DicomMediaIO loader = new DicomMediaIO(dicom);

            if (loader.isReadableDicom()) {
                // Issue: must handle adding image to viewer and building thumbnail (middle image)
                SeriesThumbnail t = buildDicomStructure(loader);
                if (t != null) {
                    thumbs.add(t);
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
    }

    private SeriesThumbnail buildDicomStructure(DicomMediaIO dicomReader) {

        SeriesThumbnail thumb = null;
        String patientPseudoUID = (String) dicomReader.getTagValue(TagW.PatientPseudoUID);
        MediaSeriesGroup patient = dicomModel.getHierarchyNode(TreeModel.rootNode, patientPseudoUID);
        if (patient == null) {
            patient = new MediaSeriesGroupNode(TagW.PatientPseudoUID, patientPseudoUID, TagW.PatientName);
            dicomReader.writeMetaData(patient);
            dicomModel.addHierarchyNode(TreeModel.rootNode, patient);
            writeInfo(Messages.getString("LoadDicomObjects.add_pat") + " " + patient); //$NON-NLS-1$ //$NON-NLS-2$
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
                    dicomModel.updateSpecialModality(dicomSeries);
                } else {
                    dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, dicomModel,
                        null, dicomSeries));
                }

                // After the thumbnail is sent to interface, it will be return to be rebuilt later
                thumb = t;

                Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
                Object dicomObject = dicomSeries.getTagValue(TagW.DicomSpecialElementList);
                if (splitNb != null || dicomObject != null) {
                    dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Update, dicomModel,
                        null, dicomSeries));
                }

                if (openPlugin) {
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
                        dicomModel.updateSpecialModality(dicomSeries);
                    }

                    // If Split series or special DICOM element update the explorer view and View2DContainer
                    Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
                    Object dicomObject = dicomSeries.getTagValue(TagW.DicomSpecialElementList);
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
