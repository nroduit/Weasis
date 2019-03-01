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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
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
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;

/**
 * @note This class is a pure copy of LoadLocalDicom taking care only of the DicomObject and not the file
 *
 * @version $Rev$ $Date$
 */

public class LoadDicomObjects extends ExplorerTask<Boolean, String> {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(LoadDicomObjects.class);
    
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
        dicomModel
            .firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, dicomModel, null, this));
        addSelectionAndnotify();
        return true;
    }

    @Override
    protected void done() {
        dicomModel
            .firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
        LOGGER.info("End of loading DICOM locally"); //$NON-NLS-1$
    }

    public void addSelectionAndnotify() {

        openPlugin = true;

        final ArrayList<SeriesThumbnail> thumbs = new ArrayList<>(dicomObjectsToLoad.length);

        for (Attributes dicom : dicomObjectsToLoad) {
            if (isCancelled()) {
                return;
            }

            try {
                DicomMediaIO loader = new DicomMediaIO(dicom);
                if (loader.isReadableDicom()) {
                    // Issue: must handle adding image to viewer and building thumbnail (middle image)
                    SeriesThumbnail t = buildDicomStructure(loader);
                    if (t != null) {
                        thumbs.add(t);
                    }
                }
            } catch (URISyntaxException e) {
                LOGGER.debug("", e); //$NON-NLS-1$
            }

        }

        for (final SeriesThumbnail t : thumbs) {
            MediaSeries<MediaElement> series = t.getSeries();
            // Avoid to rebuild most of CR series thumbnail
            if (series != null && series.size(null) > 2) {
                GuiExecutor.instance().execute(t::reBuildThumbnail);
            }
        }
    }

    private SeriesThumbnail buildDicomStructure(DicomMediaIO dicomReader) {

        SeriesThumbnail thumb = null;
        String patientPseudoUID = (String) dicomReader.getTagValue(TagD.getUID(Level.PATIENT));
        MediaSeriesGroup patient = dicomModel.getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
        if (patient == null) {
            patient =
                new MediaSeriesGroupNode(TagW.PatientPseudoUID, patientPseudoUID, DicomModel.patient.getTagView());
            dicomReader.writeMetaData(patient);
            dicomModel.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
            LOGGER.info("Adding patient: {}", patient); //$NON-NLS-1$
        }

        String studyUID = (String) dicomReader.getTagValue(TagD.getUID(Level.STUDY));
        MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, studyUID);
        if (study == null) {
            study = new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.getTagView());
            dicomReader.writeMetaData(study);
            dicomModel.addHierarchyNode(patient, study);
        }

        String seriesUID = (String) dicomReader.getTagValue(TagD.get(Tag.SeriesInstanceUID));
        Series<?> dicomSeries = (Series<?>) dicomModel.getHierarchyNode(study, seriesUID);
        try {
            if (dicomSeries == null) {
                dicomSeries = dicomReader.buildSeries(seriesUID);
                dicomSeries.setTag(TagW.ExplorerModel, dicomModel);
                dicomSeries.setTag(TagW.ObjectToSave, Boolean.TRUE);
                dicomReader.writeMetaData(dicomSeries);
                dicomModel.addHierarchyNode(study, dicomSeries);
                MediaElement[] medias = dicomReader.getMediaElement();
                if (medias != null) {
                    for (MediaElement media : medias) {
                        dicomModel.applySplittingRules(dicomSeries, media);
                        media.setTag(TagW.ObjectToSave, Boolean.TRUE);
                    }
                    if (medias.length > 0) {
                        dicomSeries.setFileSize(dicomSeries.getFileSize() + medias[0].getLength());
                    }
                }

                // Load image and create thumbnail in this Thread
                SeriesThumbnail t = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                if (t == null) {
                    t = DicomExplorer.createThumbnail(dicomSeries, dicomModel, Thumbnail.DEFAULT_SIZE);
                    dicomSeries.setTag(TagW.Thumbnail, t);
                    Optional.ofNullable(t).ifPresent(SeriesThumbnail::repaint);
                }

                if (DicomModel.isSpecialModality(dicomSeries)) {
                    dicomModel.addSpecialModality(dicomSeries);
                    Arrays.stream(medias).filter(DicomSpecialElement.class::isInstance)
                        .map(DicomSpecialElement.class::cast).findFirst().ifPresent(d -> dicomModel.firePropertyChange(
                            new ObservableEvent(ObservableEvent.BasicAction.UPDATE, dicomModel, null, d)));
                } else {
                    dicomModel.firePropertyChange(
                        new ObservableEvent(ObservableEvent.BasicAction.ADD, dicomModel, null, dicomSeries));
                }

                // After the thumbnail is sent to interface, it will be return to be rebuilt later
                thumb = t;

                Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
                if (splitNb != null) {
                    dicomModel.firePropertyChange(
                        new ObservableEvent(ObservableEvent.BasicAction.UPDATE, dicomModel, null, dicomSeries));
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
                if (isSOPInstanceUIDExist(study, dicomSeries, seriesUID,
                    TagD.getTagValue(dicomReader, Tag.SOPInstanceUID, String.class))) {
                    return null;
                }
                MediaElement[] medias = dicomReader.getMediaElement();
                if (medias != null) {
                    for (MediaElement media : medias) {
                        dicomModel.applySplittingRules(dicomSeries, media);
                        media.setTag(TagW.ObjectToSave, Boolean.TRUE);
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
                        Arrays.stream(medias).filter(DicomSpecialElement.class::isInstance)
                            .map(DicomSpecialElement.class::cast).findFirst()
                            .ifPresent(d -> dicomModel.firePropertyChange(
                                new ObservableEvent(ObservableEvent.BasicAction.UPDATE, dicomModel, null, d)));
                    }

                    // If Split series or special DICOM element update the explorer view and View2DContainer
                    Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
                    if (splitNb != null) {
                        dicomModel.firePropertyChange(
                            new ObservableEvent(ObservableEvent.BasicAction.UPDATE, dicomModel, null, dicomSeries));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Build DICOM hierarchy", e);
        }
        return thumb;
    }

    private boolean isSOPInstanceUIDExist(MediaSeriesGroup study, Series dicomSeries, String seriesUID, Object sopUID) {
        TagW sopTag = TagD.getUID(Level.INSTANCE);
        if (dicomSeries.hasMediaContains(sopTag, sopUID)) {
            return true;
        }
        Object splitNb = dicomSeries.getTagValue(TagW.SplitSeriesNumber);
        if (splitNb != null && study != null) {
            String uid = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
            if (uid != null) {
                for (MediaSeriesGroup group : dicomModel.getChildren(study)) {
                    if (dicomSeries != group && group instanceof Series) {
                        Series s = (Series) group;
                        if (uid.equals(TagD.getTagValue(group, Tag.SeriesInstanceUID))) {
                            if (s.hasMediaContains(sopTag, sopUID)) {
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
