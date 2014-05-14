/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.image.op;

public class ByteLut {

    public static final ByteLut defaultLUT = new ByteLut("Default (from image)", null, null);
    public static final ByteLut grayLUT = new ByteLut("Gray", ByteLutCollection.grays,
        ByteLutCollection.invert(ByteLutCollection.grays));
    private final String name;
    private final byte[][] lutTable;
    private final byte[][] invertedLutTable;

    public ByteLut(String name, byte[][] lutTable, byte[][] invertedLutTable) {
        this.name = name;
        this.lutTable = lutTable;
        this.invertedLutTable = invertedLutTable;
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

    public byte[][] getInvertedLutTable() {
        return invertedLutTable;
    }

}
