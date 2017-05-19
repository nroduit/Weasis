/*******************************************************************************
 * Copyright (c) 2014 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.qr.manisfest;

import org.dcm4che3.data.Tag;
import org.weasis.dicom.qr.manisfest.xml.TagUtil;
import org.weasis.dicom.qr.manisfest.xml.XmlDescription;

public class SOPInstance implements XmlDescription {

    private final String sopInstanceUID;
    private String transferSyntaxUID = null;
    private String instanceNumber = null;
    private String directDownloadFile = null;

    public SOPInstance(String sopInstanceUID) {
        if (sopInstanceUID == null) {
            throw new IllegalArgumentException("sopInstanceIUID is null"); //$NON-NLS-1$
        }
        this.sopInstanceUID = sopInstanceUID;
    }

    public String getTransferSyntaxUID() {
        return transferSyntaxUID;
    }

    public void setTransferSyntaxUID(String transferSyntaxUID) {
        this.transferSyntaxUID = transferSyntaxUID;
    }

    public String getSOPInstanceIUID() {
        return sopInstanceUID;
    }

    public String getInstanceNumber() {
        return instanceNumber;
    }

    public void setInstanceNumber(String instanceNumber) {
        this.instanceNumber = instanceNumber == null ? null : instanceNumber.trim();
    }

    public String getDirectDownloadFile() {
        return directDownloadFile;
    }

    public void setDirectDownloadFile(String directDownloadFile) {
        this.directDownloadFile = directDownloadFile;
    }

    @Override
    public String toXml() {
        StringBuilder result = new StringBuilder();
        result.append("\n<"); //$NON-NLS-1$
        result.append(TagUtil.Level.INSTANCE);
        result.append(" "); //$NON-NLS-1$
        TagUtil.addXmlAttribute(Tag.SOPInstanceUID, sopInstanceUID, result);
        // file_tsuid DICOM Transfer Syntax UID (0002,0010)
        TagUtil.addXmlAttribute(Tag.TransferSyntaxUID, transferSyntaxUID, result);
        TagUtil.addXmlAttribute(Tag.InstanceNumber, instanceNumber, result);
        TagUtil.addXmlAttribute(TagUtil.DirectDownloadFile, directDownloadFile, result);
        result.append("/>"); //$NON-NLS-1$

        return result.toString();
    }

}
