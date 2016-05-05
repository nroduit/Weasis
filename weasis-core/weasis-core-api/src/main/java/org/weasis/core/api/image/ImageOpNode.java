/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse  License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.image;

import java.util.Map;

public interface ImageOpNode extends Cloneable {

    public final class Param {

        public static final String NAME = "op.display.name"; //$NON-NLS-1$
        public static final String ENABLE = "op.enable"; //$NON-NLS-1$

        public static final String INPUT_IMG = "op.input.img"; //$NON-NLS-1$
        public static final String OUTPUT_IMG = "op.output.img"; //$NON-NLS-1$

        private Param() {
        }
    }

    void process() throws Exception;

    ImageOpNode clone() throws CloneNotSupportedException;

    boolean isEnabled();

    void setEnabled(boolean enabled);

    String getName();

    void setName(String name);

    Object getParam(String key);

    void setParam(String key, Object value);

    void setAllParameters(Map<String, Object> map);

    void removeParam(String key);

    void clearParams();

    /**
     * Clear all the parameter values starting by "op.input" or "op.output"
     */
    void clearIOCache();

    void handleImageOpEvent(ImageOpEvent event);

}
