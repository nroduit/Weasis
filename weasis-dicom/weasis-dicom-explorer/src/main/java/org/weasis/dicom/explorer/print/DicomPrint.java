/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Marcelo Porto - initial API and implementation, Animati Sistemas de Informática Ltda. (http://www.animati.com.br)
 *     Nicolas Roduit
 *     
 ******************************************************************************/

package org.weasis.dicom.explorer.print;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.util.concurrent.Executors;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.util.ImagePrint;
import org.weasis.core.ui.util.PrintOptions;

public class DicomPrint {

    private DicomPrintOptions dicomPrintOptions;

    public DicomPrint(DicomPrintOptions dicomPrintOptions) {
        this.dicomPrintOptions = dicomPrintOptions;
    }

    public static BufferedImage printImage(ExportImage image, PrintOptions printOptions) {
        if ((image == null)) {
            return null;
        }
        if (!printOptions.getHasAnnotations() && image.getInfoLayer().isVisible()) {
            image.getInfoLayer().setVisible(false);
        }
        RenderedImage img = image.getSourceImage();
        double w = img == null ? image.getWidth() : img.getWidth() * image.getImage().getRescaleX();
        double h = img == null ? image.getHeight() : img.getHeight() * image.getImage().getRescaleY();
        double scaleFactor = printOptions.getImageScale();
        // Set the print area in pixel
        int cw = (int) (w * scaleFactor + 0.5);
        int ch = (int) (h * scaleFactor + 0.5);
        image.setSize(cw, ch);

        image.zoom(scaleFactor);
        image.center();

        boolean wasBuffered = ImagePrint.disableDoubleBuffering(image);
        BufferedImage bufferedImage;
        if (printOptions.isColor()) {
            bufferedImage = createRGBBufferedImage(image.getWidth(), image.getHeight());
        } else {
            bufferedImage = createGrayBufferedImage(image.getWidth(), image.getHeight());
        }
        Graphics2D g2d = (Graphics2D) bufferedImage.getGraphics();
        g2d.setClip(image.getBounds());
        image.draw(g2d);
        ImagePrint.restoreDoubleBuffering(image, wasBuffered);
        return bufferedImage;
    }

    /**
     * Creates a BufferedImage with a custom color model that can be used to store 3 channel RGB data in a byte array
     * data buffer
     */
    public static BufferedImage createRGBBufferedImage(int destWidth, int destHeight) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        ColorModel cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        WritableRaster r = cm.createCompatibleWritableRaster(destWidth, destHeight);
        BufferedImage dest = new BufferedImage(cm, r, false, null);

        return dest;
    }

    public static BufferedImage createGrayBufferedImage(int destWidth, int destHeight) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        ColorModel cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        WritableRaster r = cm.createCompatibleWritableRaster(destWidth, destHeight);
        BufferedImage dest = new BufferedImage(cm, r, false, null);

        return dest;
    }

    public void printImage(BufferedImage image) throws Exception {
        Attributes filmSessionAttrs = new Attributes();
        Attributes filmBoxAttrs = new Attributes();
        Attributes imageBoxAttrs = new Attributes();
        Attributes dicomImage = new Attributes();
        final String printManagementSOPClass =
            dicomPrintOptions.isPrintInColor() ? UID.BasicColorPrintManagementMetaSOPClass
                : UID.BasicGrayscalePrintManagementMetaSOPClass;
        final String imageBoxSOPClass =
            dicomPrintOptions.isPrintInColor() ? UID.BasicColorImageBoxSOPClass : UID.BasicGrayscaleImageBoxSOPClass;

        storeRasterInDicom(image, dicomImage, dicomPrintOptions.isPrintInColor());

        // writeDICOM(new File("/tmp/print.dcm"), dicomImage);

        Device device = new Device("WEASIS_AE"); //$NON-NLS-1$
        ApplicationEntity ae = new ApplicationEntity("WEASIS_AE"); //$NON-NLS-1$
        Connection conn = new Connection();
        //    Executor executor = new NewThreadExecutor("WEASIS_AE"); //$NON-NLS-1$
        ApplicationEntity remoteAE = new ApplicationEntity(dicomPrintOptions.getDicomPrinter().getAeTitle());
        Connection remoteConn = new Connection();

        ae.addConnection(conn);
        ae.setAssociationInitiator(true);
        ae.setAETitle("WEASIS_AE"); //$NON-NLS-1$

        remoteConn.setPort(dicomPrintOptions.getDicomPrinter().getPort());
        remoteConn.setHostname(dicomPrintOptions.getDicomPrinter().getHostname());
        remoteConn.setSocketCloseDelay(90);

        remoteAE.setAssociationAcceptor(true);
        remoteAE.addConnection(remoteConn);

        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.addConnection(conn);
        device.setExecutor(Executors.newSingleThreadExecutor());
        device.setScheduledExecutor(Executors.newSingleThreadScheduledExecutor());

        filmSessionAttrs.setInt(Tag.NumberOfCopies, VR.IS, dicomPrintOptions.getNumOfCopies());
        filmSessionAttrs.setString(Tag.PrintPriority, VR.CS, dicomPrintOptions.getPriority());
        filmSessionAttrs.setString(Tag.MediumType, VR.CS, dicomPrintOptions.getMediumType());
        filmSessionAttrs.setString(Tag.FilmDestination, VR.CS, dicomPrintOptions.getFilmDestination());
        filmBoxAttrs.setString(Tag.FilmSizeID, VR.CS, dicomPrintOptions.getFilmSizeId());
        filmBoxAttrs.setString(Tag.FilmOrientation, VR.CS, dicomPrintOptions.getFilmOrientation());
        filmBoxAttrs.setString(Tag.MagnificationType, VR.CS, dicomPrintOptions.getMagnificationType());
        filmBoxAttrs.setString(Tag.SmoothingType, VR.CS, dicomPrintOptions.getSmoothingType());
        filmBoxAttrs.setString(Tag.Trim, VR.CS, dicomPrintOptions.getTrim());
        filmBoxAttrs.setString(Tag.BorderDensity, VR.CS, dicomPrintOptions.getBorderDensity());
        filmBoxAttrs.setInt(Tag.MinDensity, VR.US, dicomPrintOptions.getMinDensity());
        filmBoxAttrs.setInt(Tag.MaxDensity, VR.US, dicomPrintOptions.getMaxDensity());
        filmBoxAttrs.setString(Tag.ImageDisplayFormat, VR.ST, dicomPrintOptions.getImageDisplayFormat());
        imageBoxAttrs.setInt(Tag.ImageBoxPosition, VR.US, 1);

        Sequence seq =
            imageBoxAttrs.ensureSequence(dicomPrintOptions.isPrintInColor() ? Tag.BasicColorImageSequence
                : Tag.BasicGrayscaleImageSequence, 1);
        seq.add(dicomImage);
        final String filmSessionUID = UIDUtils.createUID();
        final String filmBoxUID = UIDUtils.createUID();
        Attributes filmSessionSequenceObject = new Attributes();
        filmSessionSequenceObject.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.BasicFilmSessionSOPClass);
        filmSessionSequenceObject.setString(Tag.ReferencedSOPInstanceUID, VR.UI, filmSessionUID);
        seq = filmBoxAttrs.ensureSequence(Tag.ReferencedFilmSessionSequence, 1);
        seq.add(filmSessionSequenceObject);

        AAssociateRQ rq = new AAssociateRQ();
        rq.addPresentationContext(new PresentationContext(1, printManagementSOPClass, UID.ImplicitVRLittleEndian));
        rq.setCallingAET(ae.getAETitle());
        rq.setCalledAET(remoteAE.getAETitle());
        Association association = ae.connect(remoteConn, rq);
        // Create a Basic Film Session
        dimseRSPHandler(association.ncreate(printManagementSOPClass, UID.BasicFilmSessionSOPClass, filmSessionUID,
            filmSessionAttrs, UID.ImplicitVRLittleEndian));
        // Create a Basic Film Box. We need to get the Image Box UID from the response
        DimseRSP ncreateFilmBoxRSP =
            association.ncreate(printManagementSOPClass, UID.BasicFilmBoxSOPClass, filmBoxUID, filmBoxAttrs,
                UID.ImplicitVRLittleEndian);
        dimseRSPHandler(ncreateFilmBoxRSP);
        ncreateFilmBoxRSP.next();
        Attributes imageBoxSequence = ncreateFilmBoxRSP.getDataset().getNestedDataset(Tag.ReferencedImageBoxSequence);
        // Send N-SET message with the Image Box
        dimseRSPHandler(association.nset(printManagementSOPClass, imageBoxSOPClass,
            imageBoxSequence.getString(Tag.ReferencedSOPInstanceUID), imageBoxAttrs, UID.ImplicitVRLittleEndian));
        // Send N-ACTION message with the print action
        dimseRSPHandler(association.naction(printManagementSOPClass, UID.BasicFilmBoxSOPClass, filmBoxUID, 1, null,
            UID.ImplicitVRLittleEndian));
        // The print action ends here. This will only delete the Film Box and Film Session
        association.ndelete(printManagementSOPClass, UID.BasicFilmBoxSOPClass, filmBoxUID);
        association.ndelete(printManagementSOPClass, UID.BasicFilmSessionSOPClass, filmSessionUID);

    }

    private void dimseRSPHandler(DimseRSP response) throws Exception {
        response.next();
        Attributes command = response.getCommand();
        if (command.getInt(Tag.Status, 0) != 0) {
            throw new Exception("Unable to print the image. DICOM response status: " + command.getInt(Tag.Status, 0)); //$NON-NLS-1$
        }
    }

    public static void storeRasterInDicom(BufferedImage image, Attributes dcmObj, Boolean printInColor) {
        byte[] bytesOut = null;
        if (dcmObj != null && image != null) {
            dcmObj.setInt(Tag.Columns, VR.US, image.getWidth());
            dcmObj.setInt(Tag.Rows, VR.US, image.getHeight());
            dcmObj.setInt(Tag.PixelRepresentation, VR.US, 0);
            dcmObj.setString(Tag.PhotometricInterpretation, VR.CS, printInColor ? "RGB" : "MONOCHROME2"); //$NON-NLS-1$ //$NON-NLS-2$
            dcmObj.setInt(Tag.SamplesPerPixel, VR.US, printInColor ? 3 : 1);
            dcmObj.setInt(Tag.BitsAllocated, VR.US, 8);
            dcmObj.setInt(Tag.BitsStored, VR.US, 8);
            dcmObj.setInt(Tag.HighBit, VR.US, 7);
            // Issue with some PrintSCP servers
            // dcmObj.putString(Tag.TransferSyntaxUID, VR.UI, UID.ImplicitVRLittleEndian);
            if (printInColor) {
                // Must be PixelInterleavedSampleModel
                dcmObj.setInt(Tag.PlanarConfiguration, VR.US, 0);
            } else {
                image = convertRGBImageToMonochrome(image);
            }

            DataBuffer dataBuffer = image.getRaster().getDataBuffer();

            if (dataBuffer instanceof DataBufferByte) {
                bytesOut = ((DataBufferByte) dataBuffer).getData();
            } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
                short[] data =
                    dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                        : ((DataBufferUShort) dataBuffer).getData();
                bytesOut = new byte[data.length * 2];
                for (int i = 0; i < data.length; i++) {
                    bytesOut[i * 2] = (byte) (data[i] & 0xFF);
                    bytesOut[i * 2 + 1] = (byte) ((data[i] >>> 8) & 0xFF);
                }
            }
            dcmObj.setBytes(Tag.PixelData, VR.OW, bytesOut);
        }
    }

    private static BufferedImage convertRGBImageToMonochrome(BufferedImage colorImage) {
        if (colorImage.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return colorImage;
        }
        BufferedImage image =
            new BufferedImage(colorImage.getWidth(), colorImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = image.getGraphics();
        g.drawImage(colorImage, 0, 0, null);
        g.dispose();
        return image;
    }
}