package org.weasis.core.ui.serialize;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.imp.XmlGraphicModel;

public class XmlSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(XmlSerializer.class);

    public static GraphicModel readPresentationModel(File gpxFile) {
        if (gpxFile.canRead()) {
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(XmlGraphicModel.class);
                Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
                GraphicModel model = (GraphicModel) jaxbUnmarshaller.unmarshal(gpxFile);
                int length = model.getModels().size();
                model.getModels().removeIf(g -> g.getLayer() == null);
                if (length > model.getModels().size()) {
                    LOGGER.error("Removing {} graphics wihout a attached layer", model.getModels().size() - length);
                }
                return model;
            } catch (Exception e) {
                LOGGER.error("Cannot load xml: ", e);
            }
        }
        return null;
    }

    public static void writePresentation(ImageElement img, File destinationFile) {
        GraphicModel model = (GraphicModel) img.getTagValue(TagW.PresentationModel);
        if (model != null && !model.getModels().isEmpty()) {
            File gpxFile = new File(destinationFile.getParent(), destinationFile.getName() + ".xml"); //$NON-NLS-1$

            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(model.getClass());
                Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

                // output pretty printed
                jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

                jaxbMarshaller.marshal(model, System.out);
                jaxbMarshaller.marshal(model, gpxFile);
            } catch (Exception e) {
                LOGGER.error("Cannot save xml: ", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> T deserialize(String input, Class<T> clazz) throws JAXBException {
        StringReader sr = new StringReader(input);
        JAXBContext context = JAXBContext.newInstance(clazz);
        Unmarshaller unmarshaller = context.createUnmarshaller();

        return (T) unmarshaller.unmarshal(sr);
    }

    public static String serialize(GraphicModel model) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(model.getClass());
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            StringWriter sw = new StringWriter();
            jaxbMarshaller.marshal(model, sw);
            return sw.toString();
        } catch (Exception e) {
            LOGGER.error("Cannot serialize xml: ", e);
        }
        return null;
    }
}
