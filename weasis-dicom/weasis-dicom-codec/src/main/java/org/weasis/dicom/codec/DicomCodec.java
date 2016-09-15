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
package org.weasis.dicom.codec;

import java.net.URI;
import java.util.Hashtable;
import java.util.List;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.dcm4che3.data.ItemPointer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReaderSpi;
import org.dcm4che3.imageio.plugins.rle.RLEImageReaderSpi;
import org.dcm4che3.io.BulkDataDescriptor;
import org.dcm4che3.util.TagUtils;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaReader;

import com.sun.media.imageioimpl.plugins.raw.RawImageReaderSpi;

@Component(immediate = false)
@Service
@Property(name = "service.name", value = "DICOM (dcm4chee toolkit)")
public class DicomCodec implements Codec {
    public static final DicomImageReaderSpi DicomImageReaderSpi = new DicomImageReaderSpi();
    public static final RLEImageReaderSpi RLEImageReaderSpi = new RLEImageReaderSpi();
    // public static final DicomImageWriterSpi DicomImageWriterSpi = new DicomImageWriterSpi();
    public static final RawImageReaderSpi RawImageReaderSpi = new RawImageReaderSpi();

    public static final String NAME = "dcm4che"; //$NON-NLS-1$
    public static final String[] FILE_EXTENSIONS = { "dcm", "dicm" }; //$NON-NLS-1$ //$NON-NLS-2$

    public static final BulkDataDescriptor BULKDATA_DESCRIPTOR = new BulkDataDescriptor() {

        @Override
        public boolean isBulkData(List<ItemPointer> itemPointer, String privateCreator, int tag, VR vr, int length) {
            switch (TagUtils.normalizeRepeatingGroup(tag)) {
                case Tag.PixelDataProviderURL:
                case Tag.AudioSampleData:
                case Tag.CurveData:
                case Tag.SpectroscopyData:
                case Tag.OverlayData:
                case Tag.EncapsulatedDocument:
                case Tag.FloatPixelData:
                case Tag.DoubleFloatPixelData:
                case Tag.PixelData:
                    return itemPointer.isEmpty();
                case Tag.WaveformData:
                    return itemPointer.size() == 1 && itemPointer.get(0).sequenceTag == Tag.WaveformSequence;
            }
            if (TagUtils.isPrivateTag(tag)) {
                return length > 5000; // Do no read in memory private value more than 5 KB
            }
            return false;
        }
    };

    @Override
    public String[] getReaderMIMETypes() {
        return new String[] { DicomMediaIO.MIMETYPE, DicomMediaIO.SERIES_XDSI, DicomMediaIO.IMAGE_MIMETYPE,
            DicomMediaIO.SERIES_VIDEO_MIMETYPE, DicomMediaIO.SERIES_ENCAP_DOC_MIMETYPE };
    }

    @Override
    public String[] getReaderExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public boolean isMimeTypeSupported(String mimeType) {
        if (mimeType != null) {
            for (String mime : getReaderMIMETypes()) {
                if (mimeType.equals(mime)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public MediaReader getMediaIO(URI media, String mimeType, Hashtable<String, Object> properties) {
        if (isMimeTypeSupported(mimeType)) {
            return new DicomMediaIO(media);
        }
        return null;
    }

    @Override
    public String getCodecName() {
        return NAME;
    }

    @Override
    public String[] getWriterExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String[] getWriterMIMETypes() {
        return new String[] { DicomMediaIO.MIMETYPE };
    }

}
