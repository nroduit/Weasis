/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.print;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.Comparator;
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
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.print.PageRenderer;
import org.weasis.core.ui.print.PageSource;
import org.weasis.core.ui.print.PageSpec;
import org.weasis.core.ui.print.Paper;
import org.weasis.core.ui.print.PrintResolution;
import org.weasis.core.ui.util.ExportLayout;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomPrintNode;
import org.weasis.dicom.param.DeviceOpService;

/**
 * The DICOM printer sink: the page is rendered as one film raster at the film resolution of the
 * node and sent as the single image box of a film box (Basic Grayscale or Color Print Management,
 * PS3.4 H), the printer scaling it to its own matrix.
 */
public class DicomPrint {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomPrint.class);

  private static final String DEFAULT_CALLING_AET = "WEASIS_AE"; // NON-NLS

  private final DicomPrintNode node;
  private final DicomPrintOptions options;

  public DicomPrint(DicomPrintNode node, DicomPrintOptions options) {
    if (node == null) {
      throw new IllegalArgumentException("No print node");
    }
    this.node = node;
    this.options = options == null ? node.getPrintOptions() : options;
  }

  /** Renders and sends every page as a film, {@code copies} times, from {@code callingNode}. */
  public void print(
      PageSource pages, PageSpec spec, PrintOptions page, int copies, DefaultDicomNode callingNode)
      throws Exception {
    int count = pages.pageCount();
    for (int i = 0; i < count; i++) {
      send(render(pages.page(i), spec, page, i, count), spec, copies, callingNode);
    }
  }

  /** The film raster: the page at the film resolution of the node, in gray or in color. */
  public BufferedImage render(
      ExportLayout<? extends ImageElement> layout, PageSpec spec, PrintOptions page) {
    return render(layout, spec, page, 0, 1);
  }

  /** The film raster of page {@code pageIndex} of {@code pageCount}. */
  public BufferedImage render(
      ExportLayout<? extends ImageElement> layout,
      PageSpec spec,
      PrintOptions page,
      int pageIndex,
      int pageCount) {
    double pixelsPerMm = PrintResolution.pixelsPerMm(options.getFilmResolution());
    Dimension size = PageRenderer.pageSize(spec, pixelsPerMm);
    BufferedImage film =
        options.isColorPrint()
            ? createRGBBufferedImage(size.width, size.height)
            : createGrayBufferedImage(size.width, size.height);
    return PageRenderer.renderPage(layout, spec, pixelsPerMm, page, film, pageIndex, pageCount);
  }

  /** The attributes of the film session (PS3.3 C.13.1). */
  public static Attributes filmSessionAttributes(DicomPrintOptions options, int copies) {
    Attributes attrs = new Attributes();
    attrs.setInt(Tag.NumberOfCopies, VR.IS, Math.max(1, copies));
    attrs.setString(Tag.PrintPriority, VR.CS, options.getPriority());
    attrs.setString(Tag.MediumType, VR.CS, options.getMediumType());
    attrs.setString(Tag.FilmDestination, VR.CS, options.getFilmDestination());
    return attrs;
  }

  /**
   * The attributes of the film box (PS3.3 C.13.3): the film size and orientation of the page, the
   * density of the empty area from its background, the rest from the options.
   */
  public static Attributes filmBoxAttributes(DicomPrintOptions options, PageSpec spec) {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.FilmSizeID, VR.CS, filmSizeIdOf(spec.paper()));
    attrs.setString(Tag.FilmOrientation, VR.CS, spec.orientation().name());
    attrs.setString(Tag.ImageDisplayFormat, VR.ST, options.getImageDisplayFormat());
    attrs.setString(Tag.MagnificationType, VR.CS, options.getMagnificationType());
    attrs.setString(Tag.SmoothingType, VR.CS, options.getSmoothingType());
    attrs.setString(Tag.Trim, VR.CS, options.getTrim());
    attrs.setString(Tag.BorderDensity, VR.CS, options.getBorderDensity());
    attrs.setString(
        Tag.EmptyImageDensity,
        VR.CS,
        Color.WHITE.equals(spec.background()) ? "WHITE" : "BLACK"); // NON-NLS
    attrs.setInt(Tag.MinDensity, VR.US, options.getMinDensity());
    attrs.setInt(Tag.MaxDensity, VR.US, options.getMaxDensity());
    return attrs;
  }

  /** The DICOM film size of a paper, or of the closest film when the paper has none. */
  public static String filmSizeIdOf(Paper paper) {
    if (paper.hasFilmSizeId()) {
      return paper.filmSizeId();
    }
    return DicomPrintOptions.filmSizes().stream()
        .min(
            Comparator.comparingDouble(
                p ->
                    Math.abs(p.widthMm() - paper.widthMm())
                        + Math.abs(p.heightMm() - paper.heightMm())))
        .orElse(DicomPrintOptions.DEF_FILM_SIZE)
        .filmSizeId();
  }

  /** Sends the film raster to the printer: film session, film box, image box, print action. */
  public void send(BufferedImage film, PageSpec spec, int copies, DefaultDicomNode callingNode)
      throws Exception {
    boolean color = options.isColorPrint();
    String printManagementSOPClass =
        color ? UID.BasicColorPrintManagementMeta : UID.BasicGrayscalePrintManagementMeta;
    String imageBoxSOPClass = color ? UID.BasicColorImageBox : UID.BasicGrayscaleImageBox;

    Attributes dicomImage = new Attributes();
    storeRasterInDicom(film, dicomImage, color);
    Attributes imageBoxAttrs = new Attributes();
    imageBoxAttrs.setInt(Tag.ImageBoxPosition, VR.US, 1);
    imageBoxAttrs
        .ensureSequence(color ? Tag.BasicColorImageSequence : Tag.BasicGrayscaleImageSequence, 1)
        .add(dicomImage);

    Attributes filmSessionAttrs = filmSessionAttributes(options, copies);
    Attributes filmBoxAttrs = filmBoxAttributes(options, spec);
    String filmSessionUID = UIDUtils.createUID();
    String filmBoxUID = UIDUtils.createUID();
    Attributes filmSessionRef = new Attributes();
    filmSessionRef.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.BasicFilmSession);
    filmSessionRef.setString(Tag.ReferencedSOPInstanceUID, VR.UI, filmSessionUID);
    Sequence seq = filmBoxAttrs.ensureSequence(Tag.ReferencedFilmSessionSequence, 1);
    seq.add(filmSessionRef);

    String callingAet = callingNode == null ? DEFAULT_CALLING_AET : callingNode.getAeTitle();
    Device device = new Device(callingAet);
    ApplicationEntity ae = new ApplicationEntity(callingAet);
    Connection conn = new Connection();
    ae.addConnection(conn);
    ae.setAssociationInitiator(true);
    device.addConnection(conn);
    device.addApplicationEntity(ae);

    Connection remoteConn = new Connection();
    remoteConn.setPort(node.getPort());
    remoteConn.setHostname(node.getHostname());
    remoteConn.setSocketCloseDelay(90);

    AAssociateRQ rq = new AAssociateRQ();
    rq.addPresentationContext(
        new PresentationContext(1, printManagementSOPClass, UID.ImplicitVRLittleEndian));
    rq.setCallingAET(callingAet);
    rq.setCalledAET(node.getAeTitle());

    var service = new DeviceOpService(device);
    service.start();
    try {
      Association as = ae.connect(remoteConn, rq);
      try {
        // See http://dicom.nema.org/medical/dicom/current/output/chtml/part02/sect_E.4.2.html
        dimseRSPHandler(
            as.ncreate(
                printManagementSOPClass,
                UID.BasicFilmSession,
                filmSessionUID,
                filmSessionAttrs,
                UID.ImplicitVRLittleEndian));
        // The film box response carries the UID of the image box to fill
        DimseRSP ncreateFilmBoxRSP =
            as.ncreate(
                printManagementSOPClass,
                UID.BasicFilmBox,
                filmBoxUID,
                filmBoxAttrs,
                UID.ImplicitVRLittleEndian);
        dimseRSPHandler(ncreateFilmBoxRSP);
        ncreateFilmBoxRSP.next();
        Attributes imageBoxSequence =
            ncreateFilmBoxRSP.getDataset().getNestedDataset(Tag.ReferencedImageBoxSequence);
        dimseRSPHandler(
            as.nset(
                printManagementSOPClass,
                imageBoxSOPClass,
                imageBoxSequence.getString(Tag.ReferencedSOPInstanceUID),
                imageBoxAttrs,
                UID.ImplicitVRLittleEndian));
        dimseRSPHandler(
            as.naction(
                printManagementSOPClass,
                UID.BasicFilmBox,
                filmBoxUID,
                1,
                null,
                UID.ImplicitVRLittleEndian));
        // The print action ends here. This only deletes the film box and the film session
        as.ndelete(printManagementSOPClass, UID.BasicFilmBox, filmBoxUID);
        as.ndelete(printManagementSOPClass, UID.BasicFilmSession, filmSessionUID);
      } finally {
        if (as.isReadyForDataTransfer()) {
          as.waitForOutstandingRSP();
          as.release();
        }
        // The association runs on the device executors: let it end before they are stopped
        as.waitForSocketClose();
      }
    } finally {
      service.stop();
    }
  }

  private void dimseRSPHandler(DimseRSP response) throws IOException, InterruptedException {
    response.next();
    Attributes command = response.getCommand();
    int status = command.getInt(Tag.Status, 0);
    if (status == Status.AttributeValueOutOfRange
        || status == Status.AttributeListError
        || status == 0xB600
        || status == 0xB602
        || status == 0xB604
        || status == 0xB609
        || status == 0xB60A) {
      LOGGER.warn("DICOM Print warning status: {}", Integer.toHexString(status));
    } else if (status != Status.Success) {
      throw new IOException(
          "Unable to print the image. DICOM response status: " + Integer.toHexString(status));
    }
  }

  /** A raster of 3 bytes per pixel in RGB order, the layout of a color image box. */
  public static BufferedImage createRGBBufferedImage(int destWidth, int destHeight) {
    ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
    ColorModel cm =
        new ComponentColorModel(cs, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
    WritableRaster r = cm.createCompatibleWritableRaster(destWidth, destHeight);
    return new BufferedImage(cm, r, false, null);
  }

  public static BufferedImage createGrayBufferedImage(int destWidth, int destHeight) {
    ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
    ColorModel cm =
        new ComponentColorModel(cs, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
    WritableRaster r = cm.createCompatibleWritableRaster(destWidth, destHeight);
    return new BufferedImage(cm, r, false, null);
  }

  /** Fills the pixel module of an image box with the raster: 8-bit RGB or MONOCHROME2. */
  public static void storeRasterInDicom(
      BufferedImage image, Attributes dcmObj, Boolean printInColor) {
    byte[] bytesOut = null;
    if (dcmObj != null && image != null) {
      dcmObj.setInt(Tag.Columns, VR.US, image.getWidth());
      dcmObj.setInt(Tag.Rows, VR.US, image.getHeight());
      dcmObj.setInt(Tag.PixelRepresentation, VR.US, 0);
      dcmObj.setString(
          Tag.PhotometricInterpretation, VR.CS, printInColor ? "RGB" : "MONOCHROME2"); // NON-NLS
      dcmObj.setInt(Tag.SamplesPerPixel, VR.US, printInColor ? 3 : 1);
      dcmObj.setInt(Tag.BitsAllocated, VR.US, 8);
      dcmObj.setInt(Tag.BitsStored, VR.US, 8);
      dcmObj.setInt(Tag.HighBit, VR.US, 7);
      // The page raster has square pixels
      dcmObj.setInt(Tag.PixelAspectRatio, VR.IS, 1, 1);

      DataBuffer dataBuffer;
      if (printInColor) {
        // Must be PixelInterleavedSampleModel
        dcmObj.setInt(Tag.PlanarConfiguration, VR.US, 0);
        dataBuffer = toInterleavedRGB(image).getRaster().getDataBuffer();
      } else {
        dataBuffer = toMonochrome(image).getRaster().getDataBuffer();
      }

      if (dataBuffer instanceof DataBufferByte dataBufferByte) {
        bytesOut = dataBufferByte.getData();
      } else if (dataBuffer instanceof DataBufferShort dataBufferShort) {
        bytesOut = fillShortArray(dataBufferShort.getData());
      } else if (dataBuffer instanceof DataBufferUShort dataBufferUShort) {
        bytesOut = fillShortArray(dataBufferUShort.getData());
      }
      dcmObj.setBytes(Tag.PixelData, VR.OW, bytesOut);
    }
  }

  private static byte[] fillShortArray(short[] data) {
    byte[] bytesOut = new byte[data.length * 2];
    for (int i = 0; i < data.length; i++) {
      bytesOut[i * 2] = (byte) (data[i] & 0xFF);
      bytesOut[i * 2 + 1] = (byte) ((data[i] >>> 8) & 0xFF);
    }
    return bytesOut;
  }

  private static boolean isByteInterleaved(BufferedImage image, int bands) {
    return image.getColorModel() instanceof ComponentColorModel
        && image.getColorModel().getNumComponents() == bands
        && image.getRaster().getDataBuffer() instanceof DataBufferByte
        && image.getType() != BufferedImage.TYPE_3BYTE_BGR;
  }

  private static BufferedImage toInterleavedRGB(BufferedImage image) {
    if (isByteInterleaved(image, 3)) {
      return image;
    }
    BufferedImage rgb = createRGBBufferedImage(image.getWidth(), image.getHeight());
    draw(image, rgb);
    return rgb;
  }

  private static BufferedImage toMonochrome(BufferedImage image) {
    if (isByteInterleaved(image, 1)) {
      return image;
    }
    BufferedImage gray =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
    draw(image, gray);
    return gray;
  }

  private static void draw(BufferedImage source, BufferedImage target) {
    Graphics g = target.getGraphics();
    try {
      g.drawImage(source, 0, 0, null);
    } finally {
      g.dispose();
    }
  }
}
