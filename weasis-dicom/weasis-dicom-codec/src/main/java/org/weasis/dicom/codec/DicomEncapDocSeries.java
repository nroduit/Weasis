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
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;

public class DicomEncapDocSeries extends Series<DicomEncapDocElement> implements FilesExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomEncapDocSeries.class);

    public DicomEncapDocSeries(String subseriesInstanceUID) {
        super(TagW.SubseriesInstanceUID, subseriesInstanceUID, DicomSeries.defaultTagView);
    }

    public DicomEncapDocSeries(DicomSeries dicomSeries) {
        super(TagW.SubseriesInstanceUID, dicomSeries.getTagValue(TagW.SubseriesInstanceUID),
            DicomSeries.defaultTagView);

        Iterator<Entry<TagW, Object>> iter = dicomSeries.getTagEntrySetIterator();
        while (iter.hasNext()) {
            Entry<TagW, Object> e = iter.next();
            setTag(e.getKey(), e.getValue());
        }
    }

    @Override
    public void addMedia(DicomEncapDocElement media) {
        if (media instanceof DicomEncapDocElement) {
            DicomEncapDocElement dcmEnc = (DicomEncapDocElement) media;
            if (media.getMediaReader() instanceof DicomMediaIO) {
                DicomMediaIO dicomImageLoader = (DicomMediaIO) media.getMediaReader();
                String extension = "tmp"; //$NON-NLS-1$
                Attributes ds = dicomImageLoader.getDicomObject();
                String mime = ds.getString(Tag.MIMETypeOfEncapsulatedDocument);
                List<String> extensions = MimeInspector.getExtensions(mime);
                if (!extensions.isEmpty()) {
                    extension = extensions.get(0);
                }
                // see http://dicom.nema.org/MEDICAL/Dicom/current/output/chtml/part03/sect_C.24.2.html
                Object data = dicomImageLoader.getDicomObject().getValue(Tag.EncapsulatedDocument);
                if (data instanceof BulkData) {
                    BulkData bulkData = (BulkData) data;
                    FileInputStream in = null;
                    FileOutputStream out = null;
                    try {
                        File file = File.createTempFile("encap_", "." + extension, AppProperties.FILE_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                        in = new FileInputStream(dcmEnc.getFile());
                        out = new FileOutputStream(file);
                        StreamUtils.skipFully(in, bulkData.offset());
                        StreamUtils.copy(in, out, bulkData.length());
                        dcmEnc.setDocument(file);
                        this.add(dcmEnc);
                    } catch (Exception e) {
                        LOGGER.error("Cannot extract encapsulated document", e);
                    } finally {
                        FileUtil.safeClose(out);
                        FileUtil.safeClose(in);
                    }
                }
            }
        }
    }

    @Override
    public String getToolTips() {
        StringBuilder toolTips = new StringBuilder("<html>"); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.pat"), TagD.get(Tag.PatientName)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.mod"), TagD.get(Tag.Modality)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series_nb"), TagD.get(Tag.SeriesNumber)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.study"), TagD.get(Tag.StudyDescription)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series"), TagD.get(Tag.SeriesDescription)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.date"), TagD.get(Tag.SeriesDate)); //$NON-NLS-1$
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    @Override
    public String toString() {
        return (String) getTagValue(TagW.SubseriesInstanceUID);
    }

    @Override
    public String getMimeType() {
        return DicomMediaIO.SERIES_ENCAP_DOC_MIMETYPE;
    }

    @Override
    public List<File> getExtractFiles() {
        // Should have only one file as all the DicomEncapDocElement items are split in sub-series
        List<File> files = new ArrayList<>();
        Iterable<DicomEncapDocElement> mediaList = getMedias(null, null);
        synchronized (this) {
            for (Iterator<DicomEncapDocElement> iter = mediaList.iterator(); iter.hasNext();) {
                DicomEncapDocElement dcm = iter.next();
                files.add(dcm.getExtractFile());
            }
        }
        return files;
    }

}
