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
package org.weasis.dicom.codec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.Map.Entry;

import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.StreamUtils;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;

public class DicomVideoSeries extends Series<DicomVideoElement> implements FileExtractor {

    private int width = 256;
    private int height = 256;

    public DicomVideoSeries(String subseriesInstanceUID) {
        super(TagW.SubseriesInstanceUID, subseriesInstanceUID, TagW.SubseriesInstanceUID);
    }

    public DicomVideoSeries(DicomSeries dicomSeries) {
        super(TagW.SubseriesInstanceUID, dicomSeries.getTagValue(TagW.SubseriesInstanceUID), TagW.SubseriesInstanceUID);

        Iterator<Entry<TagW, Object>> iter = dicomSeries.getTagEntrySetIterator();
        while (iter.hasNext()) {
            Entry<TagW, Object> e = iter.next();
            setTag(e.getKey(), e.getValue());
        }
    }

    @Override
    public void addMedia(MediaElement media) {
        if (media instanceof DicomVideoElement) {
            DicomVideoElement dcmVideo = (DicomVideoElement) media;
            if (media.getMediaReader() instanceof DicomMediaIO) {
                DicomMediaIO dicomImageLoader = (DicomMediaIO) media.getMediaReader();
                width = (Integer) dicomImageLoader.getTagValue(TagW.Columns);
                height = (Integer) dicomImageLoader.getTagValue(TagW.Rows);
                VR.Holder holder = new VR.Holder();
                Object pixdata = dicomImageLoader.getDicomObject().getValue(Tag.PixelData, holder);
                if (pixdata instanceof Fragments) {
                    Fragments fragments = (Fragments) pixdata;
                    for (Object data : fragments) {
                        if (data instanceof BulkData) {
                            BulkData bulkData = (BulkData) data;
                            FileInputStream in = null;
                            FileOutputStream out = null;
                            try {
                                File videoFile =
                                    File.createTempFile("video_", ".mpg", AbstractProperties.FILE_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                                in = new FileInputStream(dcmVideo.getFile());
                                out = new FileOutputStream(videoFile);
                                StreamUtils.skipFully(in, bulkData.offset);
                                StreamUtils.copy(in, out, bulkData.length);
                                dcmVideo.setVideoFile(videoFile);
                                this.add(dcmVideo);
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                FileUtil.safeClose(out);
                                FileUtil.safeClose(in);
                            }
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
        StringBuilder toolTips = new StringBuilder();
        toolTips.append("<html>"); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.pat"), TagW.PatientName); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.mod"), TagW.Modality); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series_nb"), TagW.SeriesNumber); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.study"), TagW.StudyDescription); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series"), TagW.SeriesDescription); //$NON-NLS-1$
        toolTips.append(Messages.getString("DicomSeries.date")); //$NON-NLS-1$ //$NON-NLS-2$
        toolTips.append(' ');
        toolTips.append(TagW.formatDateTime((Date) getTagValue(TagW.SeriesDate)));
        toolTips.append("<br>"); //$NON-NLS-1$ 
        toolTips.append(Messages.getString("DicomVideo.video_l")); //$NON-NLS-1$
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
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
    public File getExtractFile() {
        DicomVideoElement media = getMedia(MEDIA_POSITION.FIRST, null, null);
        if (media != null) {
            return media.getVideoFile();
        }
        return null;
    }

}
