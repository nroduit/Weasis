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

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;

public class DicomEncapDocSeries extends Series<DicomEncapDocElement> implements FileExtractor {

    public DicomEncapDocSeries(String subseriesInstanceUID) {
        super(TagW.SubseriesInstanceUID, subseriesInstanceUID, TagW.SubseriesInstanceUID);
    }

    public DicomEncapDocSeries(DicomSeries dicomSeries) {
        super(TagW.SubseriesInstanceUID, dicomSeries.getTagValue(TagW.SubseriesInstanceUID), TagW.SubseriesInstanceUID);

        Iterator<Entry<TagW, Object>> iter = dicomSeries.getTagEntrySetIterator();
        while (iter.hasNext()) {
            Entry<TagW, Object> e = iter.next();
            setTag(e.getKey(), e.getValue());
        }
    }

    @Override
    public void addMedia(MediaElement media) {
        if (media instanceof DicomEncapDocElement) {
            if (media.getMediaReader() instanceof DicomMediaIO) {
                DicomMediaIO dicomImageLoader = (DicomMediaIO) media.getMediaReader();
                byte[] doc = null;
                String extension = "tmp"; //$NON-NLS-1$
                try {
                    DicomObject dicom = dicomImageLoader.getDicomObject();
                    String mime = dicom.getString(Tag.MIMETypeOfEncapsulatedDocument);
                    String[] extensions = MimeInspector.getExtensions(mime);
                    if (extensions.length > 0) {
                        extension = extensions[0];
                    }
                    doc = dicom.getBytes(Tag.EncapsulatedDocument);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (doc != null) {
                    OutputStream tempFileStream = null;
                    try {
                        File file = File.createTempFile("encap_", "." + extension, AbstractProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                        tempFileStream = new BufferedOutputStream(new FileOutputStream(file));
                        tempFileStream.write(doc);
                        DicomEncapDocElement dicom = (DicomEncapDocElement) media;
                        dicom.setDocument(file);
                        medias.add(dicom);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        FileUtil.safeClose(tempFileStream);
                    }
                }
            }
        }
    }

    @Override
    public String getToolTips() {
        StringBuffer toolTips = new StringBuffer();
        toolTips.append("<html>"); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.pat"), TagW.PatientName); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.mod"), TagW.Modality); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series_nb"), TagW.SeriesNumber); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.study"), TagW.StudyDescription); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series"), TagW.SeriesDescription); //$NON-NLS-1$
        toolTips.append(Messages.getString("DicomSeries.date") + getDate() + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    @Override
    public String toString() {
        return (String) getTagValue(TagW.SubseriesInstanceUID);
    }

    public String getDate() {
        Date seriesDate = (Date) getTagValue(TagW.SeriesDate);
        if (seriesDate != null) {
            return new SimpleDateFormat("dd/MM/yyyy").format(seriesDate); //$NON-NLS-1$
        }
        return ""; //$NON-NLS-1$
    }

    @Override
    public String getMimeType() {
        return DicomMediaIO.SERIES_ENCAP_DOC_MIMETYPE;
    }

    @Override
    public File getExtractFile() {
        DicomEncapDocElement media = getMedia(MEDIA_POSITION.FIRST);
        if (media != null) {
            return media.getDocument();
        }
        return null;
    }
}
