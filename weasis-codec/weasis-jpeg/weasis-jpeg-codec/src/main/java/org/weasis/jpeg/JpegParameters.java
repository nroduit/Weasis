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
package org.weasis.jpeg;

import org.weasis.image.jni.ImageParameters;

public class JpegParameters extends ImageParameters {

    private int allowedLossyError;

    public JpegParameters() {
        super();
        this.allowedLossyError = 0;
    }

    public int getAllowedLossyError() {
        return allowedLossyError;
    }

    public void setAllowedLossyError(int allowedLossyError) {
        this.allowedLossyError = allowedLossyError;
    }

    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer(super.toString());
        buf.append(" Allowed Lossy Error:");
        buf.append(allowedLossyError);
        return buf.toString();
    }

}
