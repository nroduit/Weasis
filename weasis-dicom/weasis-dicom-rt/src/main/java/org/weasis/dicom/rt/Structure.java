package org.weasis.dicom.rt;

import java.util.ArrayList;
import java.util.Map;

/**
 * Created by toskrip on 2/1/15.
 */
public class Structure {

    private int roiNumber;
    private String roiName;
    private String rtRoiInterpretedType;
    private float thickness;

    private Map<Character, Float> color;
    private Map<Float, ArrayList<Contour>> planes;

    public int getRoiNumber() {
        return this.roiNumber;
    }

    public void setRoiNumber(int number) {
        this.roiNumber = number;
    }

    public String getRoiName() {
        return this.roiName;
    }

    public void setRoiName(String name) {
        this.roiName = name;
    }

    public String getRtRoiInterpretedType() {
        return this.rtRoiInterpretedType;
    }

    public void setRtRoiInterpretedType(String value) {
        this.rtRoiInterpretedType = value;
    }

    public float getThickness() {
        return this.thickness;
    }

    public void setThickness(float value) {
        this.thickness = value;
    }

    public Map<Character, Float> getColor() {
        return this.color;
    }

    public void setColor(Map<Character, Float> color) {
        this.color = color;
    }

    public Map<Float, ArrayList<Contour>> getPlanes() {
        return this.planes;
    }

    public void setPlanes(Map<Float, ArrayList<Contour>> contours) {
        this.planes = contours;
    }

}
