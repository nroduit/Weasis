package org.weasis.core.api.image;

import java.awt.image.RenderedImage;

public interface OpManager extends OpEventListener, Cloneable {

    OpManager clone() throws CloneNotSupportedException;

    void removeAllImageOperationAction();

    void clearNodeParams();

    void clearNodeIOCache();

    void setFirstNode(RenderedImage imgSource);

    RenderedImage getFirstNodeInputImage();

    ImageOpNode getFirstNode();

    ImageOpNode getNode(String opName);

    ImageOpNode getLastNode();

    RenderedImage getLastNodeOutputImage();

    RenderedImage process();

    Object getParamValue(String opName, String param);

    boolean setParamValue(String opName, String param, Object value);

}