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

import org.weasis.image.jni.NativeImage;

public class NativeJ2kImage extends NativeImage {

    public NativeJ2kImage() {
        imageParameters = new J2kParameters();
    }

    public J2kParameters getJ2kParameters() {
        return (J2kParameters) imageParameters;
    }
}
