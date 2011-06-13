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
package org.weasis.core.ui.graphic;

import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Interface DragSequence.
 * 
 * @author Nicolas Roduit
 */
public interface DragSequence {

    public void startDrag(MouseEventDouble mouseevent);

    public void drag(MouseEventDouble mouseevent);

    public boolean completeDrag(MouseEventDouble mouseevent);

}
