/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.qr;

import java.util.ArrayList;

import org.weasis.core.api.util.StringUtil;
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
