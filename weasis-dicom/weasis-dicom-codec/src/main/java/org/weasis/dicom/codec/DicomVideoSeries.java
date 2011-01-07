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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map.Entry;

import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagElement;

public class DicomVideoSeries extends Series<DicomVideoElement> implements FileExtractor {

    private int width = 256;
    private int height = 256;
    private int frames = 0;

    public DicomVideoSeries(String subseriesInstanceUID) {
        super(TagElement.SubseriesInstanceUID, subseriesInstanceUID, TagElement.SubseriesInstanceUID);
    }

    public DicomVideoSeries(DicomSeries dicomSeries) {
        super(TagElement.SubseriesInstanceUID, dicomSeries.getTagValue(TagElement.SubseriesInstanceUID),
            TagElement.SubseriesInstanceUID);

        Iterator<Entry<TagElement, Object>> iter = dicomSeries.getTagEntrySetIterator();
        while (iter.hasNext()) {
            Entry<TagElement, Object> e = iter.next();
            setTag(e.getKey(), e.getValue());
        }
    }

    @Override
    public void addMedia(MediaReader mediaLoader) {
        if (mediaLoader instanceof DicomMediaIO) {
            DicomMediaIO dicomImageLoader = (DicomMediaIO) mediaLoader;
            frames = dicomImageLoader.getMediaElementNumber();
            byte[] mpeg = null;
            try {
                width = dicomImageLoader.getWidth(0);
                height = dicomImageLoader.getHeight(0);
                dicomImageLoader.readPixelData();
                mpeg = dicomImageLoader.getDicomObject().get(TagElement.PixelData.getId()).getFragment(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (mpeg != null) {
                OutputStream tempFileStream = null;
                try {
                    File videoFile = File.createTempFile("video_", ".mpg", AbstractProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                    tempFileStream = new BufferedOutputStream(new FileOutputStream(videoFile));
                    tempFileStream.write(mpeg);
                    DicomVideoElement dicom = (DicomVideoElement) dicomImageLoader.getMediaElement();
                    dicom.setVideoFile(videoFile);
                    medias.add(dicom);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // Close file.
                    if (tempFileStream != null) {
                        try {
                            tempFileStream.close();
                        } catch (Exception e) {
                        }
                    }
                }
            }
            // DataExplorerModel model = (DataExplorerModel) getTagValue(TagElement.ExplorerModel);
            // if (model != null) {
            // model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, model, null,
            // new SeriesEvent(SeriesEvent.Action.AddImage, this, insertIndex + frames)));
            // }
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
        StringBuffer toolTips = new StringBuffer();
        toolTips.append("<html>"); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.pat"), TagElement.PatientName); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.mod"), TagElement.Modality); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series_nb"), TagElement.SeriesNumber); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.study"), TagElement.StudyDescription); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series"), TagElement.SeriesDescription); //$NON-NLS-1$
        toolTips.append(Messages.getString("DicomSeries.date") + getDate() + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
        toolTips.append(Messages.getString("DicomVideo.video_l")); //$NON-NLS-1$
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    @Override
    public String toString() {
        return (String) getTagValue(TagElement.SubseriesInstanceUID);
    }

    public String getDate() {
        Date seriesDate = (Date) getTagValue(TagElement.SeriesDate);
        if (seriesDate != null) {
            return new SimpleDateFormat("dd/MM/yyyy").format(seriesDate); //$NON-NLS-1$
        }
        return ""; //$NON-NLS-1$
    }

    @Override
    public String getMimeType() {
        return DicomMediaIO.SERIES_VIDEO_MIMETYPE;
    }

    @Override
    public File getExtractFile() {
        DicomVideoElement media = getMedia(MEDIA_POSITION.FIRST);
        if (media != null) {
            return media.getVideoFile();
        }
        return null;
    }

}
