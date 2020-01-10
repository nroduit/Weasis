/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TagD.Level;

public class DicomVideoSeries extends Series<DicomVideoElement> implements FilesExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomVideoSeries.class);

    private int width = 256;
    private int height = 256;

    public DicomVideoSeries(String subseriesInstanceUID) {
        super(TagW.SubseriesInstanceUID, subseriesInstanceUID, DicomSeries.defaultTagView);
    }

    public DicomVideoSeries(DicomSeries dicomSeries) {
        super(TagD.getUID(Level.SERIES), dicomSeries.getTagValue(TagW.SubseriesInstanceUID),
            DicomSeries.defaultTagView);

        Iterator<Entry<TagW, Object>> iter = dicomSeries.getTagEntrySetIterator();
        while (iter.hasNext()) {
            Entry<TagW, Object> e = iter.next();
            setTag(e.getKey(), e.getValue());
        }
    }

    @Override
    public void addMedia(DicomVideoElement media) {
        if (media != null && media.getMediaReader() instanceof DicomMediaIO) {
            DicomMediaIO dicomImageLoader = (DicomMediaIO) media.getMediaReader();
            width = TagD.getTagValue(dicomImageLoader, Tag.Columns, Integer.class);
            height = TagD.getTagValue(dicomImageLoader, Tag.Rows, Integer.class);
            VR.Holder holder = new VR.Holder();
            Object pixdata = dicomImageLoader.getDicomObject().getValue(Tag.PixelData, holder);
            if (pixdata instanceof Fragments) {
                Fragments fragments = (Fragments) pixdata;
                // Should have only 2 fragments: 1) compression marker 2) video stream
                // One fragment shall contain the whole video stream.
                // see http://dicom.nema.org/medical/dicom/current/output/chtml/part05/sect_8.2.5.html
                for (Object data : fragments) {
                    if (data instanceof BulkData) {
                        BulkData bulkData = (BulkData) data;
                        FileInputStream in = null;
                        FileOutputStream out = null;
                        try {
                            File videoFile = File.createTempFile("video_", ".mpg", AppProperties.FILE_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                            in = new FileInputStream(media.getFile());
                            out = new FileOutputStream(videoFile);
                            StreamUtils.skipFully(in, bulkData.offset());
                            StreamUtils.copy(in, out, bulkData.length());
                            media.setVideoFile(videoFile);
                            this.add(media);
                            return;
                        } catch (Exception e) {
                            LOGGER.error("Cannot extract video stream", e); //$NON-NLS-1$
                        } finally {
                            FileUtil.safeClose(out);
                            FileUtil.safeClose(in);
                        }
                    }
                }
            }
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public String getToolTips() {
        StringBuilder toolTips = new StringBuilder("<html>"); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.pat"), TagD.get(Tag.PatientName)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.mod"), TagD.get(Tag.Modality)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series_nb"), TagD.get(Tag.SeriesNumber)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.study"), TagD.get(Tag.StudyDescription)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series"), TagD.get(Tag.SeriesDescription)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.date"), TagD.get(Tag.SeriesDate), TagD.get(Tag.SeriesTime)); //$NON-NLS-1$

        toolTips.append(Messages.getString("DicomVideo.video_l")); //$NON-NLS-1$
        toolTips.append(StringUtil.COLON_AND_SPACE);
        Integer speed = TagD.getTagValue(this, Tag.CineRate, Integer.class);
        if (speed == null) {
            speed = TagD.getTagValue(this, Tag.RecommendedDisplayFrameRate, Integer.class);
            if (speed == null) {
                speed = 25;
            }
        }
        Integer frames = TagD.getTagValue(this, Tag.NumberOfFrames, Integer.class);
        if (frames != null) {
            toolTips.append(convertSecondsInTime(frames / speed));
        }
        toolTips.append("<br>"); //$NON-NLS-1$

        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    private static String convertSecondsInTime(int totalSecs) {
        int hours = totalSecs / 3600;
        int minutes = (totalSecs % 3600) / 60;
        int seconds = totalSecs % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds); //$NON-NLS-1$
    }

    @Override
    public String toString() {
        return (String) getTagValue(TagW.SubseriesInstanceUID);
    }

    @Override
    public String getMimeType() {
        return DicomMediaIO.SERIES_VIDEO_MIMETYPE;
    }

    @Override
    public List<File> getExtractFiles() {
        // Should have only one file as all the DicomVideoElement items are split in sub-series
        List<File> files = new ArrayList<>();
        getMedias(null, null).forEach(dcm -> files.add(dcm.getExtractFile())); // Synchronized iteration with forEach
        return files;
    }
}
