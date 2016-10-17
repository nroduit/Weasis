/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer.core.bean;

import java.util.Optional;
import java.util.function.Consumer;

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.weasis.core.api.media.data.TagUtil;
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

    /**
     * Check if all tag values in the given document XML are equals to the global DICOM Tags According to the TagD data
     * Model
     *
     * @param xmlDoc
     * @return
     */
    public boolean containSameTagsValues(Document xmlDoc) {

        Optional<NodeList> nodeList = Optional.of(xmlDoc).map(Document::getDocumentElement).map(Element::getChildNodes);

        if (!nodeList.isPresent() || nodeList.get().getLength() == 0) {
            return this.isEmpty();
        }

        for (int nodeIndex = 0; nodeIndex < nodeList.get().getLength(); nodeIndex++) {
            Node node = nodeList.get().item(nodeIndex);
            TagW tag = TagD.get(Optional.ofNullable(node).map(Node::getNodeName).orElse(null));

            if (this.containTagKey(tag)) {

                Object globalTagVal = this.getTagValue(tag);
                Object xmlTagVal =
                    Optional.ofNullable(node).map(Node::getTextContent).map(s -> tag.getValue(s)).orElse(null);

                if (!TagUtil.isEquals(globalTagVal, xmlTagVal)) {
                    return false;
                }
            }
        }

        return true;

    }

    @Override
    public String toString() {
        TagW name = TagD.get(Tag.PatientName);
        return name.getFormattedTagValue(getTagValue(name), null);
    }
}
