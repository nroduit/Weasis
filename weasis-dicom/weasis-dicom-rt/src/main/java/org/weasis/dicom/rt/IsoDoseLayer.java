/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.rt;

import java.util.Objects;

/**
 * 
 * @author Tomas Skripcak
 */
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
