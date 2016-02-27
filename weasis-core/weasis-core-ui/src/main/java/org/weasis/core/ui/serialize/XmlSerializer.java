package org.weasis.core.ui.serialize;

import java.io.File;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.Unmarshaller.Listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.ui.graphic.BasicGraphic;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.model.GraphicList;

public class XmlSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(XmlSerializer.class);

    public static GraphicList readMeasurementGraphics(File gpxFile) {
        if (gpxFile.canRead()) {
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(GraphicList.class, BasicGraphic.class);
                Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
                jaxbUnmarshaller.setListener(new Listener() {
                    @Override
                    public void beforeUnmarshal(Object target, Object parent) {
                    }

                    @Override
                    public void afterUnmarshal(Object target, Object parent) {
                        if (target instanceof Graphic) {
                            ((Graphic) target).buildShape();
                        }
                    }
                });

                return (GraphicList) jaxbUnmarshaller.unmarshal(gpxFile);

            } catch (Exception e) {
                AuditLog.logError(LOGGER, e, "Cannot load xml: ");
            }
        }
        return null;
    }

    public static void readMeasurementGraphics(ImageElement img, File destinationFile) {
        File gpxFile = new File(destinationFile.getPath() + ".xml"); //$NON-NLS-1$

        if (gpxFile.canRead()) {
            // XMLInputFactory xif = XMLInputFactory.newFactory();
            // StreamSource xml = new StreamSource(gpxFile);
            // try {
            // XMLStreamReader xsr = xif.createXMLStreamReader(xml);
            // while (xsr.hasNext()) {
            // if (xsr.isStartElement() && "graphicList".equals(xsr.getLocalName())) {
            // break;
            // }
            // xsr.next();
            // }
            // } catch (XMLStreamException e) {
            // e.printStackTrace();
            // }

            GraphicList list = readMeasurementGraphics(gpxFile);
            img.setTag(TagW.MeasurementGraphics, list);
        }
    }

    public static void writeMeasurementGraphics(ImageElement img, File destinationFile) {
        GraphicList list = (GraphicList) img.getTagValue(TagW.MeasurementGraphics);
        if (list != null && list.list.size() > 0) {
            File gpxFile = new File(destinationFile.getParent(), destinationFile.getName() + ".xml"); //$NON-NLS-1$

            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(GraphicList.class, BasicGraphic.class);
                Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

                // output pretty printed
                jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

                jaxbMarshaller.marshal(list, System.out);
                jaxbMarshaller.marshal(list, gpxFile);
            } catch (Exception e) {
                AuditLog.logError(LOGGER, e, "Cannot save xml: ");
            }
        }
    }
}
