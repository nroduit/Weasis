package org.weasis.dicom.rt;

import java.util.List;
import java.util.Map;

/**
 * Created by toskrip on 2/1/15.
 */
public class Contour {

    private String uid;
    private String geometricType;
    private int contourPoints;
    private List<Map<Character, Float>> contourData;

    public String getUid() {
        return this.uid;
    }

    public void setUid(String value) {
        this.uid = value;
    }

    public String getGeometricType() {
        return this.geometricType;
    }

    public void setGeometricType(String value) {
        this.geometricType = value;
    }

    public int getContourPoints() {
        return this.contourPoints;
    }

    public void setContourPoints(int value) {
        this.contourPoints = value;
    }

    public List<Map<Character, Float>> getContourData() {
        return this.contourData;
    }

    public void setContourData(List<Map<Character, Float>> value) {
        this.contourData = value;
    }

}
