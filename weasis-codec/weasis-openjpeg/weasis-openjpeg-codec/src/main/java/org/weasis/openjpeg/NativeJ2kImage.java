/*******************************************************************************
 * Copyright (c) 2015 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
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
