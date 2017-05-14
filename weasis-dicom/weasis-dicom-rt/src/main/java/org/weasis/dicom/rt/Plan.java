package org.weasis.dicom.rt;

import java.util.Date;

/**
 * Created by toskrip on 2/1/15.
 */
public class Plan {

    private String label;
    private Date date;
    private String name;
    private Float rxDose;

    public String getLabel() {
        return this.label;
    }

    public void setLabel(String value) {
        this.label = value;
    }

    public Date getDate() {
        return this.date;
    }

    public void setDate(Date value) {
        this.date = value;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Float getRxDose() {
        return this.rxDose;
    }

    public void setRxDose(Float value) {
        this.rxDose = value;
    }

}
