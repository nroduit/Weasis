package org.weasis.dicom.explorer.pref.node;

import org.weasis.dicom.codec.TransferSyntax;

public class DcmNode {

    protected static final String T_NODES = "nodes";
    protected static final String T_NODE = "node";
    protected static final String T_DESCRIPTION = "description";
    protected static final String T_TSUID = "tsuid";
    protected static final String T_TYPE = "type";

    private String description;
    private TransferSyntax tsuid;

    public DcmNode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }

    public TransferSyntax getTsuid() {
        return tsuid;
    }

    public void setTsuid(TransferSyntax tsuid) {
        this.tsuid = tsuid;
    }

    public String getToolTips() {
        return description;
    }

}