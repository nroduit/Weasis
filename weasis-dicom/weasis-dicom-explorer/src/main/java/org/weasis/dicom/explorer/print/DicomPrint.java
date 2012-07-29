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
import java.io.File;
import java.util.concurrent.Executor;

import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.imageioimpl.plugins.dcm.DicomImageReader;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.Device;
import org.dcm4che2.net.DimseRSP;
import org.dcm4che2.net.NetworkApplicationEntity;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.NewThreadExecutor;
import org.dcm4che2.net.TransferCapability;
import org.dcm4che2.util.UIDUtils;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.util.ImagePrint;
import org.weasis.core.ui.util.PrintOptions;

public class DicomPrint {

    private static final String[] NATIVE_LE_TS = { UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian };

    private DicomPrintOptions dicomPrintOptions;

    public DicomPrint(DicomPrintOptions dicomPrintOptions) {
        this.dicomPrintOptions = dicomPrintOptions;
    }

    public static BufferedImage printImage(ExportImage image, PrintOptions printOptions) {
        if ((image == null)) {
            return null;
        }
        if (printOptions.getHasAnnotations()) {
            image.getInfoLayer().setVisible(true);
        } else {
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
            bufferedImage = DicomImageReader.createRGBBufferedImage(image.getWidth(), image.getHeight());
        } else {
            bufferedImage = createGrayBufferedImage(image.getWidth(), image.getHeight());
        }
        image.draw((Graphics2D) bufferedImage.getGraphics());
        ImagePrint.restoreDoubleBuffering(image, wasBuffered);
        return bufferedImage;
    }

    public static BufferedImage createGrayBufferedImage(int destWidth, int destHeight) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        ColorModel cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        WritableRaster r = cm.createCompatibleWritableRaster(destWidth, destHeight);
        BufferedImage dest = new BufferedImage(cm, r, false, null);

        return dest;
    }

    public void printImage(BufferedImage image) throws Exception {
        DicomObject filmSessionAttrs = new BasicDicomObject();
        DicomObject filmBoxAttrs = new BasicDicomObject();
        DicomObject imageBoxAttrs = new BasicDicomObject();
        DicomObject dicomImage = new BasicDicomObject();
        final String printManagementSOPClass =
            dicomPrintOptions.isPrintInColor() ? UID.BasicColorPrintManagementMetaSOPClass
                : UID.BasicGrayscalePrintManagementMetaSOPClass;
        final String imageBoxSOPClass =
            dicomPrintOptions.isPrintInColor() ? UID.BasicColorImageBoxSOPClass : UID.BasicGrayscaleImageBoxSOPClass;

        storeRasterInDicom(image, dicomImage, dicomPrintOptions.isPrintInColor());
        TransferCapability[] transferCapability =
            { new TransferCapability(printManagementSOPClass, NATIVE_LE_TS, TransferCapability.SCU) };

        writeDICOM(new File("/tmp/print.dcm"), dicomImage);

        Device device = new Device("");
        NetworkApplicationEntity ae = new NetworkApplicationEntity();
        NetworkConnection conn = new NetworkConnection();
        Executor executor = new NewThreadExecutor("WEASIS_AE");
        NetworkApplicationEntity remoteAE = new NetworkApplicationEntity();
        NetworkConnection remoteConn = new NetworkConnection();

        conn.setPort(106);
        conn.setHostname("localhost");

        ae.setNetworkConnection(conn);
        ae.setAssociationInitiator(true);
        ae.setAETitle("WEASIS_AE");
        ae.setTransferCapability(transferCapability);

        remoteConn.setPort(Integer.parseInt(dicomPrintOptions.getDicomPrinter().getPort()));
        remoteConn.setHostname(dicomPrintOptions.getDicomPrinter().getHostname());
        remoteConn.setSocketCloseDelay(90);

        remoteAE.setNetworkConnection(remoteConn);
        remoteAE.setAssociationAcceptor(true);
        remoteAE.setAETitle(dicomPrintOptions.getDicomPrinter().getAeTitle());

        device.setNetworkApplicationEntity(ae);
        device.setNetworkConnection(conn);

        filmSessionAttrs.putInt(Tag.NumberOfCopies, VR.IS, dicomPrintOptions.getNumOfCopies());
        filmSessionAttrs.putString(Tag.PrintPriority, VR.CS, dicomPrintOptions.getPriority());
        filmSessionAttrs.putString(Tag.MediumType, VR.CS, dicomPrintOptions.getMediumType());
        filmSessionAttrs.putString(Tag.FilmDestination, VR.CS, dicomPrintOptions.getFilmDestination());
        filmBoxAttrs.putString(Tag.FilmSizeID, VR.CS, dicomPrintOptions.getFilmSizeId());
        filmBoxAttrs.putString(Tag.FilmOrientation, VR.CS, dicomPrintOptions.getFilmOrientation());
        filmBoxAttrs.putString(Tag.MagnificationType, VR.CS, dicomPrintOptions.getMagnificationType());
        filmBoxAttrs.putString(Tag.SmoothingType, VR.CS, dicomPrintOptions.getSmoothingType());
        filmBoxAttrs.putString(Tag.Trim, VR.CS, dicomPrintOptions.getTrim());
        filmBoxAttrs.putString(Tag.BorderDensity, VR.CS, dicomPrintOptions.getBorderDensity());
        filmBoxAttrs.putInt(Tag.MinDensity, VR.US, dicomPrintOptions.getMinDensity());
        filmBoxAttrs.putInt(Tag.MaxDensity, VR.US, dicomPrintOptions.getMaxDensity());
        filmBoxAttrs.putString(Tag.ImageDisplayFormat, VR.ST, dicomPrintOptions.getImageDisplayFormat());
        imageBoxAttrs.putInt(Tag.ImageBoxPosition, VR.US, 1);
        if (dicomPrintOptions.isPrintInColor()) {
            imageBoxAttrs.putNestedDicomObject(Tag.BasicColorImageSequence, dicomImage);
        } else {
            imageBoxAttrs.putNestedDicomObject(Tag.BasicGrayscaleImageSequence, dicomImage);
        }
        final String filmSessionUID = UIDUtils.createUID();
        final String filmBoxUID = UIDUtils.createUID();
        DicomObject filmSessionSequenceObject = new BasicDicomObject();
        filmSessionSequenceObject.putString(Tag.ReferencedSOPClassUID, VR.UI, UID.BasicFilmSessionSOPClass);
        filmSessionSequenceObject.putString(Tag.ReferencedSOPInstanceUID, VR.UI, filmSessionUID);
        filmBoxAttrs.putNestedDicomObject(Tag.ReferencedFilmSessionSequence, filmSessionSequenceObject);

        Association association;
        association = ae.connect(remoteAE, executor);
        // Create a Basic Film Session
        dimseRSPHandler(association.ncreate(printManagementSOPClass, UID.BasicFilmSessionSOPClass, filmSessionUID,
            filmSessionAttrs, transferCapability[0].getTransferSyntax()[0]));
        // Create a Basic Film Box. We need to get the Image Box UID from the response
        DimseRSP ncreateFilmBoxRSP =
            association.ncreate(printManagementSOPClass, UID.BasicFilmBoxSOPClass, filmBoxUID, filmBoxAttrs,
                transferCapability[0].getTransferSyntax()[0]);
        dimseRSPHandler(ncreateFilmBoxRSP);
        ncreateFilmBoxRSP.next();
        DicomObject imageBoxSequence =
            ncreateFilmBoxRSP.getDataset().getNestedDicomObject(Tag.ReferencedImageBoxSequence);
        // Send N-SET message with the Image Box
        dimseRSPHandler(association.nset(printManagementSOPClass, imageBoxSOPClass,
            imageBoxSequence.getString(Tag.ReferencedSOPInstanceUID), imageBoxAttrs,
            transferCapability[0].getTransferSyntax()[0]));
        // Send N-ACTION message with the print action
        dimseRSPHandler(association.naction(printManagementSOPClass, UID.BasicFilmBoxSOPClass, filmBoxUID, 1, null,
            transferCapability[0].getTransferSyntax()[0]));
        // The print action ends here. This will only delete the Film Box and Film Session
        association.ndelete(printManagementSOPClass, UID.BasicFilmBoxSOPClass, filmBoxUID);
        association.ndelete(printManagementSOPClass, UID.BasicFilmSessionSOPClass, filmSessionUID);

    }

    private void dimseRSPHandler(DimseRSP response) throws Exception {
        response.next();
        DicomObject command = response.getCommand();
        if (command.getInt(Tag.Status) != 0) {
            throw new Exception("Unable to print the image.");
        }
    }

    public static void storeRasterInDicom(BufferedImage image, DicomObject dcmObj, Boolean printInColor) {
        byte[] bytesOut = null;
        if (dcmObj != null && image != null) {
            dcmObj.putInt(Tag.Columns, VR.US, image.getWidth());
            dcmObj.putInt(Tag.Rows, VR.US, image.getHeight());
            dcmObj.putInt(Tag.PixelRepresentation, VR.US, 0);
            dcmObj.putString(Tag.PhotometricInterpretation, VR.CS, printInColor ? "RGB" : "MONOCHROME2");
            dcmObj.putInt(Tag.SamplesPerPixel, VR.US, printInColor ? 3 : 1);
            dcmObj.putInt(Tag.BitsAllocated, VR.US, 8);
            dcmObj.putInt(Tag.BitsStored, VR.US, 8);
            dcmObj.putInt(Tag.HighBit, VR.US, 7);
            dcmObj.putString(Tag.TransferSyntaxUID, VR.UI, UID.ImplicitVRLittleEndian);
            if (printInColor) {
                // Must be PixelInterleavedSampleModel
                dcmObj.putInt(Tag.PlanarConfiguration, VR.US, 0);
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
            dcmObj.putBytes(Tag.PixelData, VR.OW, bytesOut);
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