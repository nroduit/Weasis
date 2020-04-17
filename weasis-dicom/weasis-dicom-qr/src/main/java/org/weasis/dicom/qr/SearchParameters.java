/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.qr;

import java.util.ArrayList;

import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.DicomParam;

public class SearchParameters {
    private String name;
    private final ArrayList<DicomParam> parameters = new ArrayList<>();

    public SearchParameters(String name) {
        setName(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (StringUtil.hasText(name)) {
            this.name = name;
        }
    }

    public ArrayList<DicomParam> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return name;
    }

}
