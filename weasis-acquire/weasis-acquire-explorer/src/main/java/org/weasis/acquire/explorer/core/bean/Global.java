package org.weasis.acquire.explorer.core.bean;

import java.util.Optional;
import java.util.function.Consumer;

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;

public class Global extends AbstractTagable {

    public void init(Document xml) {
        tags.put(TagD.get(Tag.StudyInstanceUID), UIDUtils.createUID());
        Optional.of(xml).map(o -> o.getDocumentElement()).ifPresent(init);
    }

    private final Consumer<Element> init = e -> {
        NodeList nodes = e.getChildNodes();
        if (nodes != null) {
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                setTag(node);
            }
        }
    };

    private void setTag(Node node) {
        if (node != null) {
            TagW tag = TagD.get(node.getNodeName());
            if (tag != null) {
                tag.readValue(node.getTextContent(), this);
            }
        }
    }

    @Override
    public String toString() {
        TagW name = TagD.get(Tag.PatientName);
        return TagW.getFormattedText(getTagValue(name), null);
    }
}
