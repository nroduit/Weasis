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