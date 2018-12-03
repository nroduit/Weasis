/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Tomas Skripcak - initial API and implementation
 ******************************************************************************/

package org.weasis.dicom.rt;

import java.util.Objects;

public class IsoDoseLayer extends RtLayer {
    private final IsoDose isoDose;

    public IsoDoseLayer(IsoDose isoDose) {
        super();
        this.isoDose = Objects.requireNonNull(isoDose);
        this.layer.setName(isoDose.getLabel());
    }

    public IsoDose getIsoDose() {
        return this.isoDose;
    }

    @Override
    public String toString() {
        return isoDose.toString();
    }
}
