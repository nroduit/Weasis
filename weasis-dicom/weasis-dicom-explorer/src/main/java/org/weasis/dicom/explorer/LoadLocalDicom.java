/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

import org.dcm4che3.data.Tag;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
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
import org.weasis.core.util.FileUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.dicom.codec.DicomCodec;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;

public class LoadLocalDicom extends ExplorerTask<Boolean, String> {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(LoadLocalDicom.class);
    private final File[] files;
    private final DicomModel dicomModel;
    private final boolean recursive;
    private boolean openPlugin;

    public LoadLocalDicom(File[] files, boolean recursive, DataExplorerModel explorerModel) {
        super(Messages.getString("DicomExplorer.loading"), false); //$NON-NLS-1$
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
        dicomModel
            .firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, dicomModel, null, this));
        addSelectionAndnotify(files, true);
        return true;
    }

    @Override
    protected void done() {
        dicomModel
            .firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
        LOGGER.info("End of loading DICOM locally"); //$NON-NLS-1$
    }

    public void addSelectionAndnotify(File[] file, boolean firstLevel) {
        if (file == null || file.length < 1) {
            return;
        }
        final ArrayList<SeriesThumbnail> thumbs = new ArrayList<>();
        final ArrayList<File> folders = new ArrayList<>();

        for (int i = 0; i < file.length; i++) {
            if (isCancelled()) {
                return;
            }

            if (file[i] == null) {
                continue;
            } else if (file[i].isDirectory()) {
                if (firstLevel || recursive) {
                    folders.add(file[i]);
                }
            } else {
                if (file[i].canRead()) {
                    if (FileUtil.isFileExtensionMatching(file[i], DicomCodec.FILE_EXTENSIONS)
                        || MimeInspector.isMatchingMimeTypeFromMagicNumber(file[i], DicomMediaIO.DICOM_MIMETYPE)) {
                        DicomMediaIO loader = new DicomMediaIO(file[i]);
                        if (loader.isReadableDicom()) {
                            // Issue: must handle adding image to viewer and building thumbnail (middle image)
                            SeriesThumbnail t = buildDicomStructure(loader, openPlugin);
                            if (t != null) {
                                thumbs.add(t);
                            }

                            File gpxFile = new File(file[i].getPath() + ".xml"); //$NON-NLS-1$
                            GraphicModel graphicModel = XmlSerializer.readPresentationModel(gpxFile);
                            if (graphicModel != null) {
                                loader.setTag(TagW.PresentationModel, graphicModel);
                            }
                        }
                    }
                }
            }
        }
        for (final SeriesThumbnail t : thumbs) {
            MediaSeries<MediaElement> series = t.getSeries();
            // Avoid to rebuild most of CR series thumbnail
            if (series != null && series.size(null) > 2) {
                GuiExecutor.instance().execute(t::reBuildThumbnail);
            }
        }
        for (int i = 0; i < folders.size(); i++) {
            addSelectionAndnotify(folders.get(i).listFiles(), false);
        }
    }

    private SeriesThumbnail buildDicomStructure(DicomMediaIO dicomReader, boolean open) {
        SeriesThumbnail thumb = null;
        String studyUID = (String) dicomReader.getTagValue(TagD.getUID(Level.STUDY));
        String patientPseudoUID = (String) dicomReader.getTagValue(TagD.getUID(Level.PATIENT));
        MediaSeriesGroup patient = dicomModel.getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
        if (patient == null) {
            MediaSeriesGroup study = dicomModel.getStudyNode(studyUID);
            if (study == null) {
                patient =
                    new MediaSeriesGroupNode(TagW.PatientPseudoUID, patientPseudoUID, DicomModel.patient.getTagView());
                dicomReader.writeMetaData(patient);
                dicomModel.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
                LOGGER.info("Adding patient: {}", patient); //$NON-NLS-1$
            } else {
                patient = dicomModel.getParent(study, DicomModel.patient);
                LOGGER.warn("DICOM patient attributes are inconsistent! Name or ID is different within an exam."); //$NON-NLS-1$
            }
        }

        MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, studyUID);
        if (study == null) {
            study = new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.getTagView());
            dicomReader.writeMetaData(study);
            dicomModel.addHierarchyNode(patient, study);
        }

        String seriesUID = (String) dicomReader.getTagValue(TagD.get(Tag.SeriesInstanceUID));
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

                if (open) {
                    SeriesViewerFactory plugin = UIManager.getViewerFactory(dicomSeries.getMimeType());
                    if (plugin != null && !(plugin instanceof MimeSystemAppFactory)) {
                        openPlugin = false;
                        ViewerPluginBuilder.openSequenceInPlugin(plugin, dicomSeries, dicomModel, true, true);
                    } else if (plugin != null) {
                        // Send event to select the related patient in Dicom Explorer.
                        dicomModel.firePropertyChange(
                            new ObservableEvent(ObservableEvent.BasicAction.SELECT, dicomModel, null, dicomSeries));
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
            LOGGER.error("Build DicomModel", e); //$NON-NLS-1$
        } finally {
            // dicomReader.reset();
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
                Collection<MediaSeriesGroup> seriesList = dicomModel.getChildren(study);
                for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                    MediaSeriesGroup group = it.next();
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
