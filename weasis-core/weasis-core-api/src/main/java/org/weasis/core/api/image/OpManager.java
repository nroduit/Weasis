package org.weasis.core.api.image;

import java.awt.image.RenderedImage;

import org.weasis.core.api.util.Copyable;

public interface OpManager extends OpEventListener, Copyable<OpManager> {

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

    void removeParam(String opName, String param);

}