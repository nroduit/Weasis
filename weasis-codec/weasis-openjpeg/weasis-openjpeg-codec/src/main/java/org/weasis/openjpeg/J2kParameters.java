/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.openjpeg;

import org.weasis.image.jni.ImageParameters;

public class J2kParameters extends ImageParameters {

    private int type;
    private int allowedLossyError;

    public J2kParameters() {
        super();
        this.allowedLossyError = 0;
    }

    public int getAllowedLossyError() {
        return allowedLossyError;
    }

    public void setAllowedLossyError(int allowedLossyError) {
        this.allowedLossyError = allowedLossyError;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer(super.toString());
        buf.append(" Allowed Lossy Error:");
        buf.append(allowedLossyError);
        return buf.toString();
    }

}
