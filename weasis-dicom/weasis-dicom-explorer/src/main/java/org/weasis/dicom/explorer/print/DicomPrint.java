/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.weasis.dicom.explorer.print;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
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
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.util.ExportLayout;
import org.weasis.core.ui.util.ImagePrint;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.dicom.explorer.pref.node.DicomPrintNode;
import org.weasis.dicom.explorer.print.DicomPrintDialog.FilmSize;
import org.weasis.opencv.data.PlanarImage;

public class DicomPrint {

    private final DicomPrintNode dcmNode;
    private final DicomPrintOptions printOptions;
    private int interpolation;
    private double placeholderX;
    private double placeholderY;

    private int lastx;
    private double lastwx;
    private double[] lastwy;
    private double wx;

    public DicomPrint(DicomPrintNode dicomPrintNode, DicomPrintOptions printOptions) {
        if (dicomPrintNode == null) {
            throw new IllegalArgumentException();
        }
        this.dcmNode = dicomPrintNode;
        this.printOptions = printOptions == null ? dicomPrintNode.getPrintOptions() : printOptions;
    }

    public BufferedImage printImage(ExportLayout<? extends ImageElement> layout) {
        if (layout == null) {
            return null;
        }

        BufferedImage bufferedImage = initialize(layout);
        Graphics2D g2d = (Graphics2D) bufferedImage.getGraphics();

        if (g2d != null) {
            Color borderColor = "WHITE".equals(printOptions.getBorderDensity()) ? Color.WHITE : Color.BLACK; //$NON-NLS-1$
            Color background = "WHITE".equals(printOptions.getEmptyDensity()) ? Color.WHITE : Color.BLACK; //$NON-NLS-1$
            g2d.setBackground(background);
            if (!Color.BLACK.equals(background)) {
                // Change background color
                g2d.clearRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());
            }
            final Map<LayoutConstraints, Component> elements = layout.getLayoutModel().getConstraints();
            Iterator<Entry<LayoutConstraints, Component>> enumVal = elements.entrySet().iterator();
            while (enumVal.hasNext()) {
                Entry<LayoutConstraints, Component> e = enumVal.next();
                LayoutConstraints key = e.getKey();
                Component value = e.getValue();

                ExportImage<? extends ImageElement> image = null;
                Point2D.Double pad = new Point2D.Double(0.0, 0.0);

                if (value instanceof ExportImage) {
                    image = (ExportImage) value;
                    formatImage(image, key, pad);
                }

                if (key.gridx == 0) {
                    wx = 0.0;
                } else if (lastx < key.gridx) {
                    wx += lastwx;
                }
                double wy = lastwy[key.gridx];

                double x = 5 + (placeholderX * wx) + (MathUtil.isEqualToZero(wx) ? 0 : key.gridx * 5) + pad.x;
                double y = 5 + (placeholderY * wy) + (MathUtil.isEqualToZero(wy) ? 0 : key.gridy * 5) + pad.y;
                lastx = key.gridx;
                lastwx = key.weightx;
                for (int i = key.gridx; i < key.gridx + key.gridwidth; i++) {
                    lastwy[i] += key.weighty;
                }

                if (image != null) {
                    boolean wasBuffered = ImagePrint.disableDoubleBuffering(image);

                    // Set us to the upper left corner
                    g2d.translate(x, y);
                    g2d.setClip(image.getBounds());
                    image.draw(g2d);
                    ImagePrint.restoreDoubleBuffering(image, wasBuffered);
                    g2d.translate(-x, -y);

                    if (!borderColor.equals(background)) {
                        // Change background color
                        g2d.setClip(null);
                        g2d.setColor(borderColor);
                        g2d.setStroke(new BasicStroke(2));
                        Dimension viewSize = image.getSize();
                        g2d.drawRect((int) x - 1, (int) y - 1, viewSize.width + 1, viewSize.height + 1);
                    }
                }
            }
        }

        return bufferedImage;
    }

    private BufferedImage initialize(ExportLayout<? extends ImageElement> layout) {
        Dimension dimGrid = layout.getLayoutModel().getGridSize();
        FilmSize filmSize = printOptions.getFilmSizeId();
        PrintOptions.DotPerInches dpi = printOptions.getDpi();

        int width = filmSize.getWidth(dpi);
        int height = filmSize.getHeight(dpi);

        if ("LANDSCAPE".equals(printOptions.getFilmOrientation())) { //$NON-NLS-1$
            int tmp = width;
            width = height;
            height = tmp;
        }

        String mType = printOptions.getMagnificationType();
        interpolation = 1;

        if ("REPLICATE".equals(mType)) { //$NON-NLS-1$
            interpolation = 0;
        } else if ("CUBIC".equals(mType)) { //$NON-NLS-1$
            interpolation = 2;
        }

        // Printable size
        placeholderX = width - (dimGrid.width + 1) * 5.0;
        placeholderY = height - (dimGrid.height + 1) * 5.0;

        lastx = 0;
        lastwx = 0.0;
        lastwy = new double[dimGrid.width];
        wx = 0.0;

        if (printOptions.isColorPrint()) {
            return createRGBBufferedImage(width, height);
        } else {
            return createGrayBufferedImage(width, height);
        }
    }

    private void formatImage(ExportImage<? extends ImageElement> image, LayoutConstraints key, Point2D.Double pad) {
        if (!printOptions.isShowingAnnotations() && image.getInfoLayer().getVisible()) {
            image.getInfoLayer().setVisible(false);
        }

        Rectangle2D originSize = (Rectangle2D) image.getActionValue("origin.image.bound"); //$NON-NLS-1$
        Point2D originCenterOffset = (Point2D) image.getActionValue("origin.center.offset"); //$NON-NLS-1$
        Double originZoom = (Double) image.getActionValue("origin.zoom"); //$NON-NLS-1$
        PlanarImage img = image.getSourceImage();
        if (img != null && originCenterOffset != null && originZoom != null) {
            boolean bestfit = originZoom <= 0.0;
            double canvasWidth;
            double canvasHeight;
            if (bestfit || originSize == null) {
                canvasWidth = img.width() * image.getImage().getRescaleX();
                canvasHeight = img.height() * image.getImage().getRescaleY();
            } else {
                canvasWidth = originSize.getWidth() / originZoom;
                canvasHeight = originSize.getHeight() / originZoom;
            }
            double scaleCanvas =
                Math.min(placeholderX * key.weightx / canvasWidth, placeholderY * key.weighty / canvasHeight);

            // Set the print area in pixel
            double cw = canvasWidth * scaleCanvas;
            double ch = canvasHeight * scaleCanvas;
            image.setSize((int) (cw + 0.5), (int) (ch + 0.5));

            if (printOptions.isCenter()) {
                pad.x = (placeholderX * key.weightx - cw) * 0.5;
                pad.y = (placeholderY * key.weighty - ch) * 0.5;
            } else {
                pad.x = 0.0;
                pad.y = 0.0;
            }

            image.getDisplayOpManager().setParamValue(AffineTransformOp.OP_NAME, AffineTransformOp.P_INTERPOLATION, interpolation);
            double scaleFactor = Math.min(cw / canvasWidth, ch / canvasHeight);
            // Resize in best fit window
            image.zoom(scaleFactor);
            if (bestfit) {
                image.center();
            } else {
                image.setCenter(originCenterOffset.getX(), originCenterOffset.getY());
            }
        }
    }

    /**
     * Creates a BufferedImage with a custom color model that can be used to store 3 channel RGB data in a byte array
     * data buffer
     */
    public static BufferedImage createRGBBufferedImage(int destWidth, int destHeight) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        ColorModel cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        WritableRaster r = cm.createCompatibleWritableRaster(destWidth, destHeight);
        return new BufferedImage(cm, r, false, null);
    }

    public static BufferedImage createGrayBufferedImage(int destWidth, int destHeight) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        ColorModel cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        WritableRaster r = cm.createCompatibleWritableRaster(destWidth, destHeight);
        return new BufferedImage(cm, r, false, null);
    }

    public void printImage(BufferedImage image) throws Exception {
        Attributes filmSessionAttrs = new Attributes();
        Attributes filmBoxAttrs = new Attributes();
        Attributes imageBoxAttrs = new Attributes();
        Attributes dicomImage = new Attributes();
        final String printManagementSOPClass = printOptions.isColorPrint() ? UID.BasicColorPrintManagementMetaSOPClass
            : UID.BasicGrayscalePrintManagementMetaSOPClass;
        final String imageBoxSOPClass =
            printOptions.isColorPrint() ? UID.BasicColorImageBoxSOPClass : UID.BasicGrayscaleImageBoxSOPClass;

        storeRasterInDicom(image, dicomImage, printOptions.isColorPrint());

        // writeDICOM(new File("/tmp/print.dcm"), dicomImage);

        String weasisAet = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.aet", "WEASIS_AE"); //$NON-NLS-1$ //$NON-NLS-2$

        Device device = new Device(weasisAet);
        ApplicationEntity ae = new ApplicationEntity(weasisAet);
        Connection conn = new Connection();

        ApplicationEntity remoteAE = new ApplicationEntity(dcmNode.getAeTitle());
        Connection remoteConn = new Connection();

        ae.addConnection(conn);
        ae.setAssociationInitiator(true);
        ae.setAETitle(weasisAet);

        remoteConn.setPort(dcmNode.getPort());
        remoteConn.setHostname(dcmNode.getHostname());
        remoteConn.setSocketCloseDelay(90);

        remoteAE.setAssociationAcceptor(true);
        remoteAE.addConnection(remoteConn);

        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.addConnection(conn);
        device.setExecutor(Executors.newSingleThreadExecutor());
        device.setScheduledExecutor(Executors.newSingleThreadScheduledExecutor());

        filmSessionAttrs.setInt(Tag.NumberOfCopies, VR.IS, printOptions.getNumOfCopies());
        filmSessionAttrs.setString(Tag.PrintPriority, VR.CS, printOptions.getPriority());
        filmSessionAttrs.setString(Tag.MediumType, VR.CS, printOptions.getMediumType());
        filmSessionAttrs.setString(Tag.FilmDestination, VR.CS, printOptions.getFilmDestination());
        filmBoxAttrs.setString(Tag.FilmSizeID, VR.CS, printOptions.getFilmSizeId().toString());
        filmBoxAttrs.setString(Tag.FilmOrientation, VR.CS, printOptions.getFilmOrientation());
        filmBoxAttrs.setString(Tag.MagnificationType, VR.CS, printOptions.getMagnificationType());
        filmBoxAttrs.setString(Tag.SmoothingType, VR.CS, printOptions.getSmoothingType());
        filmBoxAttrs.setString(Tag.Trim, VR.CS, printOptions.getTrim());
        filmBoxAttrs.setString(Tag.BorderDensity, VR.CS, printOptions.getBorderDensity());
        filmBoxAttrs.setInt(Tag.MinDensity, VR.US, printOptions.getMinDensity());
        filmBoxAttrs.setInt(Tag.MaxDensity, VR.US, printOptions.getMaxDensity());
        filmBoxAttrs.setString(Tag.ImageDisplayFormat, VR.ST, printOptions.getImageDisplayFormat());
        imageBoxAttrs.setInt(Tag.ImageBoxPosition, VR.US, 1);

        Sequence seq = imageBoxAttrs.ensureSequence(
            printOptions.isColorPrint() ? Tag.BasicColorImageSequence : Tag.BasicGrayscaleImageSequence, 1);
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
        Association as = ae.connect(remoteConn, rq);
        try {
            // Create a Basic Film Session
            dimseRSPHandler(as.ncreate(printManagementSOPClass, UID.BasicFilmSessionSOPClass, filmSessionUID,
                filmSessionAttrs, UID.ImplicitVRLittleEndian));
            // Create a Basic Film Box. We need to get the Image Box UID from the response
            DimseRSP ncreateFilmBoxRSP = as.ncreate(printManagementSOPClass, UID.BasicFilmBoxSOPClass, filmBoxUID,
                filmBoxAttrs, UID.ImplicitVRLittleEndian);
            dimseRSPHandler(ncreateFilmBoxRSP);
            ncreateFilmBoxRSP.next();
            Attributes imageBoxSequence =
                ncreateFilmBoxRSP.getDataset().getNestedDataset(Tag.ReferencedImageBoxSequence);
            // Send N-SET message with the Image Box
            dimseRSPHandler(as.nset(printManagementSOPClass, imageBoxSOPClass,
                imageBoxSequence.getString(Tag.ReferencedSOPInstanceUID), imageBoxAttrs, UID.ImplicitVRLittleEndian));
            // Send N-ACTION message with the print action
            dimseRSPHandler(as.naction(printManagementSOPClass, UID.BasicFilmBoxSOPClass, filmBoxUID, 1, null,
                UID.ImplicitVRLittleEndian));
            // The print action ends here. This will only delete the Film Box and Film Session
            as.ndelete(printManagementSOPClass, UID.BasicFilmBoxSOPClass, filmBoxUID);
            as.ndelete(printManagementSOPClass, UID.BasicFilmSessionSOPClass, filmSessionUID);
        } finally {
            if (as != null && as.isReadyForDataTransfer()) {
                as.waitForOutstandingRSP();
                as.release();
            }
        }

    }

    private void dimseRSPHandler(DimseRSP response) throws IOException, InterruptedException {
        response.next();
        Attributes command = response.getCommand();
        if (command.getInt(Tag.Status, 0) != 0) {
            throw new IOException("Unable to print the image. DICOM response status: " + command.getInt(Tag.Status, 0)); //$NON-NLS-1$
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
            // Assumed that the displayed image has always an 1/1 aspect ratio.
            dcmObj.setInt(Tag.PixelAspectRatio, VR.IS, 1, 1);
            // Issue with some PrintSCP servers
            // dcmObj.putString(Tag.TransferSyntaxUID, VR.UI, UID.ImplicitVRLittleEndian);

            DataBuffer dataBuffer;
            if (printInColor) {
                // Must be PixelInterleavedSampleModel
                dcmObj.setInt(Tag.PlanarConfiguration, VR.US, 0);
                dataBuffer = image.getRaster().getDataBuffer();
            } else {
                dataBuffer = convertRGBImageToMonochrome(image).getRaster().getDataBuffer();
            }

            if (dataBuffer instanceof DataBufferByte) {
                bytesOut = ((DataBufferByte) dataBuffer).getData();
            } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
                short[] data = dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
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