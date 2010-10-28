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

import org.weasis.core.api.Messages;

public class ByteLut {

    public final static ByteLut defaultLUT = new ByteLut("Default", null, null); //$NON-NLS-1$
    public final static ByteLut grayLUT =
        new ByteLut("Gray", ByteLutCollection.grays, ByteLutCollection.invert(ByteLutCollection.grays)); //$NON-NLS-1$
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
