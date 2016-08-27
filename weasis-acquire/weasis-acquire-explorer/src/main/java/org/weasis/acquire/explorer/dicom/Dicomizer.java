package org.weasis.acquire.explorer.dicom;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.jpeg.JPEG;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;

public class Dicomizer {

    private Dicomizer() {
    }

    public static void pdf(final Attributes attrs, File pdfFile, File dcmFile) throws IOException {
        attrs.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // UTF-8
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.EncapsulatedPDFStorage);
        ensureUID(attrs, Tag.StudyInstanceUID);
        ensureUID(attrs, Tag.SeriesInstanceUID);
        ensureUID(attrs, Tag.SOPInstanceUID);
        Date now = new Date();
        attrs.setDate(Tag.InstanceCreationDate, VR.DA, now);
        attrs.setDate(Tag.InstanceCreationTime, VR.TM, now);

        BulkData bulk = new BulkData(pdfFile.toURI().toString(), 0, (int) pdfFile.length(), false);
        attrs.setValue(Tag.EncapsulatedDocument, VR.OB, bulk);
        attrs.setString(Tag.MIMETypeOfEncapsulatedDocument, VR.LO, "application/pdf");
        Attributes fmi = attrs.createFileMetaInformation(UID.ExplicitVRLittleEndian);
        DicomOutputStream dos = new DicomOutputStream(dcmFile);
        try {
            dos.writeDataset(fmi, attrs);
        } finally {
            dos.close();
        }
    }

    public static void jpeg(final Attributes attrs, File jpgFile, File dcmFile, boolean noAPPn) throws IOException {
        String transferSyntax = UID.JPEGBaseline1;
        Header h = new Header();
        h.noAPPn = noAPPn;
        h.fileLength = (int) jpgFile.length();
        DataInputStream jpgInput = new DataInputStream(new BufferedInputStream(new FileInputStream(jpgFile)));
        try {
            attrs.setString(Tag.SOPClassUID, VR.UI, UID.VLPhotographicImageStorage);
            attrs.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // UTF-8
            if (noAPPn || missingRowsColumnsSamplesPMI(attrs)) {
                readHeader(attrs, jpgInput, h);
            }
            ensureUS(attrs, Tag.BitsAllocated, 8);
            ensureUS(attrs, Tag.BitsStored,
                attrs.getInt(Tag.BitsAllocated, (h.buffer[h.headerLength] & 0xff) > 8 ? 16 : 8));
            ensureUS(attrs, Tag.HighBit, attrs.getInt(Tag.BitsStored, (h.buffer[h.headerLength] & 0xff)) - 1);
            ensureUS(attrs, Tag.PixelRepresentation, 0);
            ensureUID(attrs, Tag.StudyInstanceUID);
            ensureUID(attrs, Tag.SeriesInstanceUID);
            ensureUID(attrs, Tag.SOPInstanceUID);
            Date now = new Date();
            attrs.setDate(Tag.InstanceCreationDate, VR.DA, now);
            attrs.setDate(Tag.InstanceCreationTime, VR.TM, now);
            Attributes fmi = attrs.createFileMetaInformation(transferSyntax);
            DicomOutputStream dos = new DicomOutputStream(dcmFile);
            try {
                dos.writeDataset(fmi, attrs);
                dos.writeHeader(Tag.PixelData, VR.OB, -1);
                dos.writeHeader(Tag.Item, null, 0);
                dos.writeHeader(Tag.Item, null, (h.fileLength + 1) & ~1);
                dos.write(h.buffer, 0, h.headerLength);

                int r;
                while ((r = jpgInput.read(h.buffer)) > 0) {
                    dos.write(h.buffer, 0, r);
                }

                if ((h.fileLength & 1) != 0) {
                    dos.write(0);
                }

                dos.writeHeader(Tag.SequenceDelimitationItem, null, 0);
            } finally {
                dos.close();
            }
        } finally {
            jpgInput.close();
        }
    }

    public static void mpeg(final Attributes attrs, File jpgFile, File dcmFile, boolean noAPPn) throws IOException {
        String transferSyntax = UID.MPEG2;
        Header h = new Header();
        h.noAPPn = noAPPn;
        h.fileLength = (int) jpgFile.length();
        DataInputStream jpgInput = new DataInputStream(new BufferedInputStream(new FileInputStream(jpgFile)));
        try {
            attrs.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // UTF-8
            if (noAPPn || missingRowsColumnsSamplesPMI(attrs)) {
                readHeader(attrs, jpgInput, h);
            }
            ensureUS(attrs, Tag.BitsAllocated, 8);
            ensureUS(attrs, Tag.BitsStored,
                attrs.getInt(Tag.BitsAllocated, (h.buffer[h.headerLength] & 0xff) > 8 ? 16 : 8));
            ensureUS(attrs, Tag.HighBit, attrs.getInt(Tag.BitsStored, h.buffer[h.headerLength] & 0xff) - 1);
            ensureUS(attrs, Tag.PixelRepresentation, 0);
            ensureUID(attrs, Tag.StudyInstanceUID);
            ensureUID(attrs, Tag.SeriesInstanceUID);
            ensureUID(attrs, Tag.SOPInstanceUID);
            Date now = new Date();
            attrs.setDate(Tag.InstanceCreationDate, VR.DA, now);
            attrs.setDate(Tag.InstanceCreationTime, VR.TM, now);
            Attributes fmi = attrs.createFileMetaInformation(transferSyntax);
            DicomOutputStream dos = new DicomOutputStream(dcmFile);
            try {
                dos.writeDataset(fmi, attrs);
                dos.writeHeader(Tag.PixelData, VR.OB, -1);
                int r;
                while ((r = jpgInput.read(h.buffer)) > 0) {
                    dos.write(h.buffer, 0, r);
                }
                dos.writeHeader(Tag.SequenceDelimitationItem, null, 0);
            } finally {
                dos.close();
            }
        } finally {
            jpgInput.close();
        }
    }

    private static boolean missingRowsColumnsSamplesPMI(Attributes attrs) {
        return !(attrs.containsValue(Tag.Rows) && attrs.containsValue(Tag.Columns)
            && attrs.containsValue(Tag.SamplesPerPixel) && attrs.containsValue(Tag.PhotometricInterpretation));
    }

    private static void readHeader(Attributes attrs, DataInputStream jpgInput, Header h) throws IOException {
        if (jpgInput.read() != 0xff || jpgInput.read() != JPEG.SOI || jpgInput.read() != 0xff) {
            throw new IOException("JPEG stream does not start with FF D8 FF");
        }
        int marker = jpgInput.read();
        int segmLen;
        boolean seenSOF = false;
        h.buffer[0] = (byte) 0xff;
        h.buffer[1] = (byte) JPEG.SOI;
        h.buffer[2] = (byte) 0xff;
        h.buffer[3] = (byte) marker;
        h.headerLength = 4;
        while (marker != JPEG.SOS) {
            segmLen = jpgInput.readUnsignedShort();
            if (h.buffer.length < h.headerLength + segmLen + 2) {
                h.growBuffer(h.headerLength + segmLen + 2);
            }
            h.buffer[h.headerLength++] = (byte) (segmLen >>> 8);
            h.buffer[h.headerLength++] = (byte) segmLen;
            jpgInput.readFully(h.buffer, h.headerLength, segmLen - 2);
            if ((marker & 0xf0) == JPEG.SOF0 && marker != JPEG.DHT && marker != JPEG.DAC) {
                seenSOF = true;
                int p = h.buffer[h.headerLength] & 0xff;
                int y = ((h.buffer[h.headerLength + 1] & 0xff) << 8) | (h.buffer[h.headerLength + 2] & 0xff);
                int x = ((h.buffer[h.headerLength + 3] & 0xff) << 8) | (h.buffer[h.headerLength + 4] & 0xff);
                int nf = h.buffer[h.headerLength + 5] & 0xff;
                attrs.setInt(Tag.SamplesPerPixel, VR.US, nf);
                if (nf == 3) {
                    attrs.setString(Tag.PhotometricInterpretation, VR.CS, "YBR_FULL_422");
                    attrs.setInt(Tag.PlanarConfiguration, VR.US, 0);
                } else {
                    attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
                }
                attrs.setInt(Tag.Rows, VR.US, y);
                attrs.setInt(Tag.Columns, VR.US, x);
                attrs.setInt(Tag.BitsAllocated, VR.US, p > 8 ? 16 : 8);
                attrs.setInt(Tag.BitsStored, VR.US, p);
                attrs.setInt(Tag.HighBit, VR.US, p - 1);
                attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
            }
            if (h.noAPPn && (marker & 0xf0) == JPEG.APP0) {
                h.fileLength -= segmLen + 2;
                h.headerLength -= 4;
            } else {
                h.headerLength += segmLen - 2;
            }
            if (jpgInput.read() != 0xff) {
                throw new IOException("Missing SOS segment in JPEG stream");
            }
            marker = jpgInput.read();
            h.buffer[h.headerLength++] = (byte) 0xff;
            h.buffer[h.headerLength++] = (byte) marker;
        }
        if (!seenSOF) {
            throw new IOException("Missing SOF segment in JPEG stream");
        }
    }

    private static void ensureUID(Attributes attrs, int tag) {
        if (!attrs.containsValue(tag)) {
            attrs.setString(tag, VR.UI, UIDUtils.createUID());
        }
    }

    private static void ensureUS(Attributes attrs, int tag, int val) {
        if (!attrs.containsValue(tag)) {
            attrs.setInt(tag, VR.US, val);
        }
    }

    private static class Header {
        byte[] buffer = new byte[8192];
        int headerLength = 0;
        int fileLength = 0;
        boolean noAPPn = false;

        private void growBuffer(int minSize) {
            int newSize = buffer.length << 1;
            while (newSize < minSize) {
                newSize <<= 1;
            }
            byte[] tmp = new byte[newSize];
            System.arraycopy(buffer, 0, tmp, 0, headerLength);
            buffer = tmp;
        }
    }
}
