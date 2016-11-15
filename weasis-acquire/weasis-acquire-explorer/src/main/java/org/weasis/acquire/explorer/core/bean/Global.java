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

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;

public class Global extends AbstractTagable {

    private static final Integer PatientDicomGroupNumber = Integer.parseInt("0010", 16);

    protected boolean allowFullEdition = true;

    public Global() {
        init(null);
    }

    public void init(Document xml) {
        clear();
        tags.put(TagD.get(Tag.StudyInstanceUID), UIDUtils.createUID());

        Optional.ofNullable(xml).map(o -> o.getDocumentElement()).ifPresent(element -> {

            NodeList nodeList = element.getChildNodes();
            if (nodeList != null) {
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Optional.ofNullable(nodeList.item(i)).ifPresent(this::setTag);
                }

                if (getTagValue(TagD.get(Tag.PatientID)) != null && getTagValue(TagD.get(Tag.PatientName)) != null) {
                    allowFullEdition = false;
                }
            }
        });
    }

    /**
     * Updates all Dicom Tags from the given document except Patient Dicom Group Tags
     *
     * @param xml
     */
    public void updateAllButPatient(Document xml) {

        Optional.ofNullable(xml).map(o -> o.getDocumentElement()).ifPresent(element -> {

            NodeList nodeList = element.getChildNodes();
            if (nodeList != null) {
                for (int i = 0; i < nodeList.getLength(); i++) {

                    Optional.ofNullable(nodeList.item(i))
                        .ifPresent(node -> Optional.ofNullable(TagD.get(node.getNodeName())).ifPresent(tag -> {
                            if (TagUtils.groupNumber(tag.getId()) != PatientDicomGroupNumber) {
                                tag.readValue(node.getTextContent(), this);
                            }
                        }));
                }
            }
        });
    }

    private void setTag(final Node node) {
        Optional.ofNullable(node).ifPresent(
            n -> Optional.ofNullable(TagD.get(n.getNodeName())).ifPresent(t -> t.readValue(n.getTextContent(), this)));
    }

    /**
     * Check if all patient tag values in the given document XML are equals to the global DICOM Tags According to the
     * TagD data Model <br>
     * Patient Tag have the Dicom Group Number : 0x0010
     *
     * @param xmlDoc
     * @return
     */
    public boolean containsSamePatientTagValues(Document xmlDoc) {
        return containsSameTagValues(xmlDoc, PatientDicomGroupNumber);
    }

    /**
     * Check if all tag values in the given document XML are equals to the global DICOM Tags According to the TagD data
     * Model <br>
     *
     *
     * @param xmlDoc
     * @param dicomGroupNumber
     *            is the restriction for the Tag values equality check.<br>
     *            Null involves no filtering
     * @return
     */

    public boolean containsSameTagValues(Document xmlDoc, final Integer dicomGroupNumber) {

        Optional<NodeList> nodeList = Optional.of(xmlDoc).map(Document::getDocumentElement).map(Element::getChildNodes);

        if (!nodeList.isPresent() || nodeList.get().getLength() == 0) {
            return this.isEmpty();
        }

        for (int nodeIndex = 0; nodeIndex < nodeList.get().getLength(); nodeIndex++) {
            Node node = nodeList.get().item(nodeIndex);
            TagW tag = TagD.get(Optional.ofNullable(node).map(Node::getNodeName).orElse(null));

            if (tag != null && (dicomGroupNumber == null || TagUtils.groupNumber(tag.getId()) == dicomGroupNumber)) {
                Object xmlTagVal = Optional.ofNullable(node).map(Node::getTextContent).map(tag::getValue).orElse(null);

                if (this.containTagKey(tag)) {
                    Object globalTagVal = this.getTagValue(tag);

                    if (!TagUtil.isEquals(globalTagVal, xmlTagVal)) {
                        return false;
                    }
                } else if (xmlTagVal != null) {
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

    public boolean isAllowFullEdition() {
        return allowFullEdition;
    }
}
