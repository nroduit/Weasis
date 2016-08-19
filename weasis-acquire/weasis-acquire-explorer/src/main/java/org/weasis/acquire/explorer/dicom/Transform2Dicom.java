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
package org.weasis.acquire.explorer.dicom;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.media.jai.PlanarImage;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.op.CStore;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ProgressListener;
import org.weasis.dicom.tool.Dicomizer;

public final class Transform2Dicom {

    private static final Logger LOGGER = LoggerFactory.getLogger(Transform2Dicom.class);

    private Transform2Dicom() {
    }

    public static File dicomize(Collection<AcquireImageInfo> collection) {
        File exportDirDicom =
            FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "dicomize", "dcm"));
        if (collection != null) {
            File exportDirImage =
                FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "dicomize", "img"));
            try {
                buildStudySeriesDate(collection);

                for (AcquireImageInfo imageInfo : collection) {
                    ImageElement img = imageInfo.getImage();
                    TagW tagUid = TagD.getUID(Level.INSTANCE);
                    String uid = (String) img.getTagValue(tagUid);
                    if (uid == null) {
                        uid = UUID.randomUUID().toString();
                        img.setTag(tagUid, uid);
                    }

                    // Transform to jpeg
                    File imgFile = img.getFileCache().getOriginalFile().get();
                    if (imgFile == null || !img.getMimeType().contains("jpg")
                        || !imageInfo.getCurrentValues().equals(imageInfo.getDefaultValues())) {
                        imgFile = new File(exportDirImage, uid + ".jpg");
                        PlanarImage transformedImage = img.getImage(imageInfo.getPostProcessOpManager(), false);

                        if (!ImageFiler.writeJPG(imgFile, transformedImage, 0.8f)) {
                            // out of memory
                            imgFile.delete();
                        }
                    }

                    // Dicomize
                    if (imgFile.canRead()) {
                        Attributes attrs = imageInfo.getAttributes();
                        DicomMediaUtils.fillAttributes(AcquireManager.GLOBAL.getTagEntrySetIterator(), attrs);
                        DicomMediaUtils.fillAttributes(imageInfo.getSerie().getTagEntrySetIterator(), attrs);
                        DicomMediaUtils.fillAttributes(img.getTagEntrySetIterator(), attrs);

                        try {
                            Dicomizer.jpeg(attrs, imgFile, new File(exportDirDicom, uid), false);
                        } catch (IOException e) {
                            LOGGER.error("Cannot dicomize {}", img.getName(), e);
                        }
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("Dicomize image", ex);
            } finally {
                FileUtil.recursiveDelete(exportDirImage);
            }

        }
        return exportDirDicom;
    }

    public static void sendDicomFiles(File exportDir, DicomNode destination, final JProgressBar progressBar)
        throws IOException {
        // dicomModel
        // .firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStart, dicomModel, null, t));

        try {

            // if (t.isCancelled()) {
            // return false;
            // }

            String weasisAet = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.aet", "WEASIS_AE"); //$NON-NLS-1$ //$NON-NLS-2$

            List<String> files = new ArrayList<>();
            files.add(exportDir.getPath());

            DicomProgress dicomProgress = new DicomProgress();
            dicomProgress.addProgressListener(new ProgressListener() {

                @Override
                public void handleProgression(final DicomProgress p) {
                    // if (t.isCancelled()) {
                    // p.cancel();
                    // }

                    GuiExecutor.instance().execute(new Runnable() {

                        @Override
                        public void run() {
                            int c = p.getNumberOfCompletedSuboperations() + p.getNumberOfFailedSuboperations();
                            int r = p.getNumberOfRemainingSuboperations();
                            progressBar.setValue((c * 100) / (c + r));
                        }
                    });
                }
            });

            final DicomState state = CStore.process(new DicomNode(weasisAet), destination, files, dicomProgress);
            if (state.getStatus() != Status.Success) {
                LOGGER.error("Dicom send error: {}", state.getMessage());
                GuiExecutor.instance()
                    .execute(() -> JOptionPane.showOptionDialog(null,
                        String.format("Dicom send error: %s", state.getMessage()), null, JOptionPane.DEFAULT_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, null, null));
            }
        } finally {
            FileUtil.recursiveDelete(exportDir);
        }
    }

    private static void buildStudySeriesDate(Collection<AcquireImageInfo> collection) {
        TagW seriesDate = TagD.get(Tag.SeriesDate);
        TagW seriesTime = TagD.get(Tag.SeriesTime);
        TagW studyDate = TagD.get(Tag.StudyDate);
        TagW studyTime = TagD.get(Tag.StudyTime);

        for (AcquireImageInfo imageInfo : collection) {
            ImageElement img = imageInfo.getImage();
            LocalDateTime date = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, img);
            if (date == null) {
                continue;
            }
            
            LocalDateTime minSeries = TagD.dateTime(Tag.SeriesDate, Tag.SeriesTime, imageInfo.getSerie());
            if (minSeries == null || date.isBefore(minSeries)) {
                imageInfo.getSerie().setTag(seriesDate, date.toLocalDate());
                imageInfo.getSerie().setTag(seriesTime, date.toLocalTime());
            }


            LocalDateTime minStudy = TagD.dateTime(Tag.StudyDate, Tag.StudyTime, AcquireManager.GLOBAL);
            if (minStudy == null || date.isBefore(minStudy)) {
                AcquireManager.GLOBAL.setTag(studyDate, date.toLocalDate());
                AcquireManager.GLOBAL.setTag(studyTime, date.toLocalTime());
            }
        }
    }
}
