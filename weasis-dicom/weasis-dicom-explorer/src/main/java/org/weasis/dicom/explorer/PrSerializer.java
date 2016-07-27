package org.weasis.dicom.explorer;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Collectors;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class PrSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrSerializer.class);

    public static void writePresentation(DicomImageElement img, File destinationFile) {
        GraphicModel model = (GraphicModel) img.getTagValue(TagW.PresentationModel);
        if (model != null && !model.getModels().isEmpty()) {
            File prFile = new File(destinationFile.getParent(), destinationFile.getName() + ".dcm"); //$NON-NLS-1$

            try {
                if (img.getMediaReader() instanceof DicomMediaIO) {
                    DicomMediaIO dicomImageLoader = (DicomMediaIO) img.getMediaReader();
                    Attributes attributes = DicomMediaUtils.createDicomPR(dicomImageLoader.getDicomObject(), null);

                    writeCommonTags(img, attributes);
                    writeReferences(img, attributes);
                    writeGraphics(img, model, attributes);

                    saveToFile(prFile, attributes);
                }
            } catch (Exception e) {
                LOGGER.error("Cannot save xml: ", e);
            }
        }
    }

    private static void writeCommonTags(DicomImageElement img, Attributes attributes) {
        String label = AppProperties.WEASIS_NAME + " GSPS";
        attributes.setString(Tag.ContentCreatorName, VR.PN, AppProperties.WEASIS_USER);
        attributes.setString(Tag.ContentLabel, VR.CS, label);
        attributes.setString(Tag.ContentDescription, VR.LO, "Description");
        attributes.setInt(Tag.SeriesNumber, VR.IS, 999);
        try {
            attributes.setString(Tag.StationName, VR.SH, InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        attributes.setString(Tag.SoftwareVersions, VR.LO, AppProperties.WEASIS_VERSION);
        attributes.setString(Tag.SeriesDescription, VR.LO, label);
    }

    private static void writeReferences(DicomImageElement img, Attributes attributes) {
        Attributes rfs = new Attributes(2);
        rfs.setString(Tag.SeriesInstanceUID, VR.UI, TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class));

        Attributes rfi = new Attributes(2);
        rfi.setString(Tag.ReferencedSOPClassUID, VR.UI, TagD.getTagValue(img, Tag.SOPClassUID, String.class));
        rfi.setString(Tag.ReferencedSOPInstanceUID, VR.UI, TagD.getTagValue(img, Tag.SOPInstanceUID, String.class));
        int frames = img.getMediaReader().getMediaElementNumber();
        if (frames > 1 && img.getKey() instanceof Integer) {
            rfi.setInt(Tag.ReferencedFrameNumber, VR.IS, (Integer) img.getKey());
        }
        rfs.newSequence(Tag.ReferencedImageSequence, 1).add(rfi);

        attributes.newSequence(Tag.ReferencedSeriesSequence, 1).add(rfs);
    }

    private static void writeGraphics(DicomImageElement img, GraphicModel model, Attributes attributes) {
        List<GraphicLayer> layers = model.getLayers();

        Sequence annotationSeq = attributes.newSequence(Tag.GraphicAnnotationSequence, layers.size());
        Sequence layerSeq = attributes.newSequence(Tag.GraphicLayerSequence, layers.size());
        for (int i = 0; i < layers.size(); i++) {
            GraphicLayer layer = layers.get(i);
            Attributes l = new Attributes(2);
            l.setString(Tag.GraphicLayer, VR.CS, layer.getType().name());
            l.setInt(Tag.GraphicLayerOrder, VR.IS, i);
            layerSeq.add(l);

            Attributes a = new Attributes(2);
            a.setString(Tag.GraphicLayer, VR.CS, layer.getType().name());
            List<Graphic> graphics = getGraphicsByLayer(model, layer.getUuid());
            Sequence graphicSeq = a.newSequence(Tag.GraphicObjectSequence, graphics.size());

            for (Graphic graphic : graphics) {
                Attributes g = new Attributes(5);
                g.setString(Tag.GraphicAnnotationUnits, VR.CS, "PIXEL");
                g.setInt(Tag.GraphicDimensions, VR.US, 2);
                buildDicomGraphic(graphic, g);
                graphicSeq.add(g);
            }
            annotationSeq.add(a);
        }

    }

    private static List<Graphic> getGraphicsByLayer(GraphicModel model, String layerUid) {
        return model.getModels().stream().filter(g -> layerUid.equals(g.getLayer().getUuid()))
            .collect(Collectors.toList());
    }

    private static double[] getGraphicsPoints(Graphic graphic) {
        List<java.awt.geom.Point2D.Double> pts = graphic.getPts();
        double[] list = new double[pts.size() * 2];
        for (int i = 0; i < pts.size(); i++) {
            Point2D.Double p = pts.get(i);
            list[i * 2] = p.x;
            list[i * 2 + 1] = p.y;
        }
        return list;
    }

    private static void buildDicomGraphic(Graphic graphic, Attributes dcm) {
        dcm.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.POLYLINE);
        dcm.setInt(Tag.NumberOfGraphicPoints, VR.US, graphic.getPts().size());
        dcm.setDouble(Tag.GraphicData, VR.FL, getGraphicsPoints(graphic));
    }

    private static boolean saveToFile(File output, Attributes dcm) {
        if (dcm != null) {
            try (DicomOutputStream out = new DicomOutputStream(output)) {
                out.writeDataset(dcm.createFileMetaInformation(UID.ImplicitVRLittleEndian), dcm);
                return true;
            } catch (IOException e) {
                LOGGER.error("Cannot write dicom PR: {}", e); //$NON-NLS-1$
            }
        }
        return false;
    }
}
