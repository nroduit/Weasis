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
package org.weasis.dicom.codec;

import java.util.Arrays;
import java.util.Objects;

public class DicomInstance implements Comparable<DicomInstance> {

    private final String sopInstanceUID;
    private String directDownloadFile;
    private int instanceNumber;
    private Object graphicModel;

    public DicomInstance(String sopInstanceUID) {
        this.sopInstanceUID = Objects.requireNonNull(sopInstanceUID);
        this.instanceNumber = -1;
    }

    public String getSopInstanceUID() {
        return sopInstanceUID;
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

    public Object getGraphicModel() {
        return graphicModel;
    }

    public void setGraphicModel(Object graphicModel) {
        this.graphicModel = graphicModel;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((sopInstanceUID == null) ? 0 : sopInstanceUID.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        return sopInstanceUID.equals(((DicomInstance) obj).sopInstanceUID);
    }

    @Override
    public int compareTo(DicomInstance dcm2) {
        // Sort by Instance Number or by the ending index of sopInstanceUID
        int number1 = getInstanceNumber();
        int number2 = dcm2.getInstanceNumber();
        if (number1 == -1 && number2 == -1) {
            String str1 = getSopInstanceUID();
            String str2 = dcm2.getSopInstanceUID();
            int length1 = str1.length();
            int length2 = str2.length();
            if (length1 < length2) {
                char[] c = new char[length2 - length1];
                Arrays.fill(c, '0');
                int index = str1.lastIndexOf(".") + 1; //$NON-NLS-1$
                str1 = str1.substring(0, index) + new String(c) + str1.substring(index);
            } else if (length1 > length2) {
                char[] c = new char[length1 - length2];
                Arrays.fill(c, '0');
                int index = str2.lastIndexOf(".") + 1; //$NON-NLS-1$
                str2 = str2.substring(0, index) + new String(c) + str2.substring(index);
            }
            return str1.compareTo(str2);
        } else {
            return number1 < number2 ? -1 : (number1 == number2 ? 0 : 1);
        }
    }
}
