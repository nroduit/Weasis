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
package org.weasis.core.api.image.op;

public class ByteLut {

    private final String name;
    private final byte[][] lutTable;


    public ByteLut(String name, byte[][] lutTable) {
        this.name = name;
        this.lutTable = lutTable;
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    public byte[][] getLutTable() {
        return lutTable;
    }
}
