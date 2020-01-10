/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.viewer2d.mpr;

import java.awt.Dimension;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BandedSampleModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.SampleModel;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.opencv.data.FileRawImage;
import org.weasis.opencv.data.PlanarImage;

import com.sun.media.imageio.stream.RawImageInputStream;

public class RawImageIO implements DcmMediaReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(RawImageIO.class);

    private static final String MIME_TYPE = "image/raw"; //$NON-NLS-1$
    private static final int[] OFFSETS_0 = { 0 };
    private static final int[] OFFSETS_0_0_0 = { 0, 0, 0 };
    private static final int[] OFFSETS_0_1_2 = { 0, 1, 2 };

    protected FileRawImage imageCV;
    private final FileCache fileCache;

    private final HashMap<TagW, Object> tags;
    private final Codec codec;
    private Attributes attributes;

    public RawImageIO(FileRawImage imageCV, Codec codec) {
        this.imageCV = Objects.requireNonNull(imageCV);
        this.fileCache = new FileCache(this);
        this.tags = new HashMap<>();
        this.codec = codec;
    }

    public void setBaseAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    public File getDicomFile() {
        Attributes dcm = getDicomObject();

        DicomOutputStream out = null;
        try {
            File file = imageCV.getFile();
            BulkData bdl = new BulkData(file.toURI().toString(), FileRawImage.HEADER_LENGTH,
                (int) file.length() - FileRawImage.HEADER_LENGTH, false);
            dcm.setValue(Tag.PixelData, VR.OW, bdl);
            File tmpFile = new File(DicomMediaIO.DICOM_EXPORT_DIR, dcm.getString(Tag.SOPInstanceUID));
            out = new DicomOutputStream(tmpFile);
            out.writeDataset(dcm.createFileMetaInformation(UID.ImplicitVRLittleEndian), dcm);
            return tmpFile;
        } catch (IOException e) {
            LOGGER.error("Cannot write dicom file", e); //$NON-NLS-1$
        } finally {
            FileUtil.safeClose(out);
        }
        return null;

    }

    @Override
    public void writeMetaData(MediaSeriesGroup group) {
        if (group == null) {
            return;
        }
        // Get the dicom header
        Attributes header = getDicomObject();
        DicomMediaUtils.writeMetaData(group, header);

        // Series Group
        if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
            // Information for series ToolTips
            group.setTagNoNull(TagD.get(Tag.PatientName), getTagValue(TagD.get(Tag.PatientName)));
            group.setTagNoNull(TagD.get(Tag.StudyDescription), header.getString(Tag.StudyDescription));
        }
    }

    @Override
    public PlanarImage getImageFragment(MediaElement media) throws Exception {
        if (media != null && media.getFile() != null) {
            return imageCV.read();
        }
        return null;
    }

    @Override
    public URI getUri() {
        return imageCV.getFile().toURI();
    }

    @Override
    public void reset() {
        // unlock file to be deleted on exit
    }

    @Override
    public MediaElement getPreview() {
        return null;
    }

    @Override
    public boolean delegate(DataExplorerModel explorerModel) {
        return false;
    }

    @Override
    public MediaElement[] getMediaElement() {
        return null;
    }

    @Override
    public MediaSeries<MediaElement> getMediaSeries() {
        return null;
    }

    @Override
    public int getMediaElementNumber() {
        return 1;
    }

    @Override
    public String getMediaFragmentMimeType() {
        return MIME_TYPE;
    }

    @Override
    public Map<TagW, Object> getMediaFragmentTags(Object key) {
        return tags;
    }

    @Override
    public void close() {
        reset();
    }

    @Override
    public Codec getCodec() {
        return codec;
    }

    @Override
    public String[] getReaderDescription() {
        return new String[] { "File Raw Image Decoder from OpenCV" }; //$NON-NLS-1$
    }

    @Override
    public Object getTagValue(TagW tag) {
        return tag == null ? null : tags.get(tag);
    }

    @Override
    public void setTag(TagW tag, Object value) {
        DicomMediaUtils.setTag(tags, tag, value);
    }

    @Override
    public void setTagNoNull(TagW tag, Object value) {
        if (value != null) {
            setTag(tag, value);
        }
    }

    @Override
    public boolean containTagKey(TagW tag) {
        return tags.containsKey(tag);
    }

    @Override
    public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
        return tags.entrySet().iterator();
    }

    public void copyTags(TagW[] tagList, MediaElement media, boolean allowNullValue) {
        if (tagList != null && media != null) {
            for (TagW tag : tagList) {
                Object value = media.getTagValue(tag);
                if (allowNullValue || value != null) {
                    tags.put(tag, value);
                }
            }
        }
    }

    @Override
    public void replaceURI(URI uri) {
        throw new UnsupportedOperationException();
    }

    public static ImageReader initRawImageReader(ImageInputStream imageStream, ImageParameters h, int frames,
        int pixelDataPos, boolean bigEndian, int pixelRepresentation) throws IOException {
        if (imageStream != null) {

            long[] frameOffsets = new long[frames];
            int frameLen = h.getWidth() * h.getHeight() * h.getSamplesPerPixel() * (h.getBitsPerSample() >> 3);

            frameOffsets[0] = pixelDataPos;
            for (int i = 1; i < frameOffsets.length; i++) {
                frameOffsets[i] = frameOffsets[i - 1] + frameLen;
            }
            Dimension[] imageDimensions = new Dimension[frames];
            Arrays.fill(imageDimensions, new Dimension(h.getWidth(), h.getHeight()));

            RawImageInputStream riis = new RawImageInputStream(imageStream,
                createImageTypeSpecifier(h, false, pixelRepresentation), frameOffsets, imageDimensions);
            riis.setByteOrder(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
            ImageReader reader = ImageIO.getImageReadersByFormatName("RAW").next(); //$NON-NLS-1$
            reader.setInput(riis);
            return reader;
        }
        return null;
    }

    public static ImageTypeSpecifier createImageTypeSpecifier(ImageParameters h, boolean banded,
        int pixelRepresentation) {
        int width = h.getWidth();
        int height = h.getHeight();
        int bps = h.getBitsPerSample();
        int spp = h.getSamplesPerPixel();

        int dataType =
            bps <= 8 ? DataBuffer.TYPE_BYTE : pixelRepresentation != 0 ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT;
        if (bps > 16 && spp == 1) {
            dataType = DataBuffer.TYPE_INT;
        }
        ColorSpace cs;
        if (spp == 1) {
            cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);

        } else if (spp == 3) {
            cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        } else {
            throw new IllegalArgumentException("Unsupported Samples per Pixel: " + spp); //$NON-NLS-1$
        }
        if (cs == null) {
            throw new IllegalArgumentException("Unsupported Photometric Interpretation: " //$NON-NLS-1$
                + " with Samples per Pixel: " + spp); //$NON-NLS-1$

        }
        int[] bits = new int[spp];
        Arrays.fill(bits, bps);
        ComponentColorModel cm = new ComponentColorModel(cs, bits, false, false, Transparency.OPAQUE, dataType);

        SampleModel sm;
        if (spp == 1) {
            sm = new PixelInterleavedSampleModel(dataType, width, height, 1, width, OFFSETS_0);
        }

        // samples == 3
        else if (banded) {
            sm = new BandedSampleModel(dataType, width, height, width, OFFSETS_0_1_2, OFFSETS_0_0_0);
        } else {
            sm = new PixelInterleavedSampleModel(dataType, width, height, 3, width * 3, OFFSETS_0_1_2);
        }
        return new ImageTypeSpecifier(cm, sm);
    }

    @Override
    public Attributes getDicomObject() {
        Attributes dcm = new Attributes();
        DicomMediaUtils.fillAttributes(tags, dcm);
        dcm.addAll(attributes);
        return dcm;
    }

    @Override
    public FileCache getFileCache() {
        return fileCache;
    }

    @Override
    public boolean buildFile(File ouptut) {
        return false;
    }
}
