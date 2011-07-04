/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.codec;

public class DicomInstance {

    private final String sopInstanceUID;
    private final String transferSyntaxUID;
    private String directDownloadFile;
    private int instanceNumber;

    public DicomInstance(String sopInstanceUID, String transferSyntaxUID) {
        // sopInstanceUID is absolutely required
        if (sopInstanceUID == null)
            throw new IllegalArgumentException("sopInstanceUID tag cannot be null"); //$NON-NLS-1$
        this.sopInstanceUID = sopInstanceUID;
        // If we know the tsuid before downloading file, it can be useful (for instance dicom/mpeg)
        this.transferSyntaxUID = transferSyntaxUID;
        this.instanceNumber = -1;
    }

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    public String getTransferSyntaxUID() {
        return transferSyntaxUID;
    }

    public int getInstanceNumber() {
        return instanceNumber;
    }

    public void setInstanceNumber(int instanceNumber) {
        this.instanceNumber = instanceNumber;
    }

    public String getDirectDownloadFile() {
        return directDownloadFile;
    }

    public void setDirectDownloadFile(String directDownloadFile) {
        this.directDownloadFile = directDownloadFile;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DicomInstance)
            return sopInstanceUID.equals(((DicomInstance) obj).sopInstanceUID);
        return sopInstanceUID.equals(obj);
    }
}
