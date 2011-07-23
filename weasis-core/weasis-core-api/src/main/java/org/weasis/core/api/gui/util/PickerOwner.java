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
package org.weasis.core.api.gui.util;

import java.awt.Rectangle;

/**
 * The Interface PickerOwner.
 * 
 * @author Nicolas Roduit
 */
public interface PickerOwner {

    /**
     * setPickerValues
     */
    void setPickerValues(int[][] values, Rectangle area);
}
