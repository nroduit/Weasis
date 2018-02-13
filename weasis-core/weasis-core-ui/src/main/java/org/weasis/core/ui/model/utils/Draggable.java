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
package org.weasis.core.ui.model.utils;

import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Interface DragSequence.
 *
 * @author Nicolas Roduit
 */
public interface Draggable {

    void startDrag(MouseEventDouble mouseevent);

    void drag(MouseEventDouble mouseevent);

    Boolean completeDrag(MouseEventDouble mouseevent);

}
