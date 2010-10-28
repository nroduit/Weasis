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

/**
 * The listener interface for receiving graphicsChange events.
 * 
 * @see GraphicsChangeEvent
 */
public interface GraphicsChangeListener {

    void handleGraphicAdded(Graphic graphic);

    void handleGraphicRemoved(Graphic graphic);

    void handleGraphicMoved(Graphic graphic);

    void handleGraphicResized(Graphic graphic);
}
