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
package org.weasis.image.jni;

import javax.imageio.ImageReadParam;

import com.sun.media.imageioimpl.common.SignedDataImageParam;

public class NativeImageReadParam extends ImageReadParam implements SignedDataImageParam {

    private boolean signedData = false;

    @Override
    public boolean isSignedData() {
        return signedData;
    }

    @Override
    public void setSignedData(boolean signedData) {
        this.signedData = signedData;
    }
}
