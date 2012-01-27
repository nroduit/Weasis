/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Gunter Zeilinger, Huetteldorferstr. 24/10, 1150 Vienna/Austria/Europe.
 * Portions created by the Initial Developer are Copyright (C) 2002-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See listed authors below.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */
package org.dcm4che2.imageioimpl.plugins.dcm;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.TransferSyntax;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.imageio.ImageWriterFactory;
import org.dcm4che2.imageio.plugins.dcm.DicomStreamMetaData;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.util.UIDUtils;

/**
 * Write DICOM files containing images - handles transcoding to a new representation, as well as transcoding the images.
 * Can also use raw data if the original type is the same type as the new one.
 * 
 * @author bwallace
 * 
 */
public class DicomImageWriter extends ImageWriter {
    protected DicomOutputStream dos;

    protected ImageWriter writer;

    /** The write param for the child element */
    protected ImageWriteParam writeParam;

    /*
     * Encapsulated means that the iamge data is held in ITEMs instead of directly as part of the root level object -
     * true for all compressed streams.
     */
    protected boolean encapsulated = true;

    /** Number of bytes per sample */
    private int bytes;

    /** Create a new DICOM Image Writer. */
    public DicomImageWriter(ImageWriterSpi spi) {
        super(spi);
    }

    /**
     * The image metadata provided MUST be of the correct type for the child image writer, as the meta-data will only be
     * written to the child image.
     */
    @Override
    public IIOMetadata convertImageMetadata(IIOMetadata metadata, ImageTypeSpecifier type, ImageWriteParam param) {
        setupWriter(metadata);
        return writer.convertImageMetadata(metadata, type, param);
    }

    /** Can't convert anything except existing DicomStreamMetaData */
    @Override
    public IIOMetadata convertStreamMetadata(IIOMetadata metadata, ImageWriteParam param) {
        if (metadata instanceof DicomStreamMetaData) {
            return metadata;
        }
        return null;
    }

    /**
     * No easy way to figure out what htis one as as the stream meta-data isn't yet available. Suggest only using this
     * after starting to write the stream, as at that point, the child writer will be available.
     */
    @Override
    public IIOMetadata getDefaultImageMetadata(ImageTypeSpecifier image, ImageWriteParam writeParam) {
        if (writer != null) {
            return writer.getDefaultImageMetadata(image, writeParam);
        }
        return null;
    }

    /**
     * Get a default set of DICOM data to use in the stream meta-data.
     */
    @Override
    public IIOMetadata getDefaultStreamMetadata(ImageWriteParam arg0) {
        DicomStreamMetaData ret = new DicomStreamMetaData();
        DicomObject dobj = new BasicDicomObject();
        ret.setDicomObject(dobj);
        Date now = new Date();

        dobj.putString(Tag.TransferSyntaxUID, VR.UI, UID.JPEGBaseline1);
        dobj.putString(Tag.ConversionType, VR.CS, "WSD");
        dobj.putString(Tag.Modality, VR.CS, "OT");
        dobj.putInt(Tag.InstanceNumber, VR.IS, 1);
        dobj.putDate(Tag.DateOfSecondaryCapture, VR.DA, now);
        dobj.putDate(Tag.TimeOfSecondaryCapture, VR.TM, now);
        dobj.putString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        dobj.putString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        return ret;
    }

    /**
     * This updates the metadata with relevant information from the image, such as the size etc.
     * 
     * @param metadata
     * @param image
     */
    public void updateDicomHeader(DicomStreamMetaData metadata, BufferedImage image) {
        DicomObject dobj = metadata.getDicomObject();
        // Assume that if it has bits stored, then everything else is
        // correct....
        if (dobj.containsValue(Tag.BitsStored)) {
            return;
        }
        int numSamples = image.getColorModel().getNumComponents();
        if (numSamples == 4) {
            numSamples = 3;
        }
        dobj.putInt(Tag.SamplesPerPixel, VR.US, numSamples);
        int bits = image.getColorModel().getComponentSize(0);
        int allocated = 8;
        if (bits > 8) {
            allocated = 16;
        }
        dobj.putInt(Tag.BitsStored, VR.US, bits);
        dobj.putInt(Tag.BitsAllocated, VR.US, allocated);
        dobj.putInt(Tag.Columns, VR.US, image.getWidth());
        dobj.putInt(Tag.Rows, VR.US, image.getHeight());
    }

    /** Writes the image, including the DICOM metadata. */
    @Override
    public void write(IIOMetadata metadata, IIOImage iioimage, ImageWriteParam param) throws IOException {
        DicomStreamMetaData dmeta = (DicomStreamMetaData) metadata;
        if (dmeta == null) {
            dmeta = (DicomStreamMetaData) getDefaultStreamMetadata(param);
            metadata = dmeta;
            updateDicomHeader(dmeta, (BufferedImage) iioimage.getRenderedImage());
        }
        prepareWriteSequence(metadata);
        writeToSequence(iioimage, param);
        endWriteSequence();
    }

    /**
     * Writing as a sequence is actually the preferred approach. All the regular write method does is writes to a
     * sequence.
     */
    @Override
    public boolean canWriteSequence() {
        return true;
    }

    /** Sets up the child writer if it hasn't already been setup */
    protected void setupWriter(IIOMetadata metadata) {
        if (writer != null) {
            return;
        }
        if (metadata == null) {
            metadata = getDefaultStreamMetadata(null);
        }
        DicomStreamMetaData dmeta = (DicomStreamMetaData) metadata;
        DicomObject dobj = dmeta.getDicomObject();
        String tsuid = dobj.getString(Tag.TransferSyntaxUID);
        TransferSyntax ts = TransferSyntax.valueOf(tsuid);
        encapsulated = ts.encapsulated();

        if (encapsulated) {
            writer = ImageWriterFactory.getInstance().getWriterForTransferSyntax(tsuid);
            writeParam = ImageWriterFactory.getInstance().createWriteParam(tsuid, writer);
        } else {
            writer = ImageIO.getImageWritersByFormatName("RAW").next();
        }
    }

    /**
     * Start writing the intiial sequence.
     */
    @Override
    public void prepareWriteSequence(IIOMetadata metadata) throws IOException {
        if (dos != null) {
            throw new IOException("Already written the DICOM object header - can't write it again.");
        }
        DicomStreamMetaData dmeta = (DicomStreamMetaData) metadata;
        DicomObject dobj = dmeta.getDicomObject();
        Object output = getOutput();
        dos = new DicomOutputStream((ImageOutputStream) output);
        dos.setAutoFinish(false);
        if (dmeta.isFileMetaInfoIncluded()) {
        dos.writeDicomFile(dobj);
        } else {
            dos.writeDataset(dobj, dobj.getString(Tag.TransferSyntaxUID));
        }

        setupWriter(metadata);

        if (encapsulated) {
            dos.writeHeader(Tag.PixelData, VR.OB, -1);
            dos.writeHeader(Tag.Item, null, 0);
        } else {
            int frames = dobj.getInt(Tag.NumberOfFrames, 1);
            int width = dobj.getInt(Tag.Columns);
            int height = dobj.getInt(Tag.Rows);
            bytes = (dobj.getInt(Tag.BitsAllocated, 8) + 7) / 8;
            int samples = dobj.getInt(Tag.SamplesPerPixel);
            int size = frames * width * height * bytes * samples;
            VR vr = VR.OB;
            if (bytes > 1) {
                vr = VR.OW;
            }
            dos.writeHeader(Tag.PixelData, vr, size);
        }
        dos.flush();
        ((ImageOutputStream) output).flush();
    }

    /**
     * Write the given image to the sequence.
     */
    @Override
    public void writeToSequence(IIOImage iioimage, ImageWriteParam param) throws IOException {
        byte[] data = extractImageEncoding(iioimage, param);
        writeBytesToSequence(data, param);
    }

    /**
     * Write the given image as a byte array to the sequence.
     */
    public void writeBytesToSequence(byte[] data, ImageWriteParam param) throws IOException {

        if (encapsulated) {
            dos.writeHeader(Tag.Item, null, data.length);
        }

        dos.write(data);
        // The flush allows any memory buffer to be cleared.
        dos.flush();
        ((ImageOutputStream) output).flush();
    }

    /**
     * This method gets the encoded image from the given object as a byte array of data.
     */
    protected byte[] extractImageEncoding(IIOImage iioimage, ImageWriteParam param) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream imageOutput = new MemoryCacheImageOutputStream(baos);
        writer.setOutput(imageOutput);
        writer.write(null, iioimage, writeParam);
        imageOutput.close();
        baos.close();
        byte[] data = baos.toByteArray();
        // Write little endian raw data
        if ((!encapsulated) && bytes == 2 && !dos.getTransferSyntax().bigEndian()) {
            for (int i = 0; i < data.length; i += 2) {
                byte swap = data[i];
                data[i] = data[i + 1];
                data[i + 1] = swap;
            }
        }
        return data;
    }

    /**
     * Finish writing the header data to the stream.
     */
    @Override
    public void endWriteSequence() throws IOException {
        if (encapsulated) {
            dos.writeHeader(Tag.SequenceDelimitationItem, null, 0);
        }
        dos.finish();
        ((ImageOutputStream) output).flush();
        dos = null;
        output = null;
        writer = null;
    }

}
