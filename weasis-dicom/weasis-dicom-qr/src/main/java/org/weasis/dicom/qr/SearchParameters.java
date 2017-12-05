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
