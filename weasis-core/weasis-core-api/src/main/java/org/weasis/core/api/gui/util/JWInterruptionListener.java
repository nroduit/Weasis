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

/**
 * An interface used by <code>JmvThread</code> to request an interruption of the task.
 * 
 * @author Nicolas Roduit
 * @see org.weasis.media.gui.util.JmvThread
 */

public interface JWInterruptionListener {

    /**
     * Action to execute when the thread is interrupted.
     * 
     * @see org.weasis.media.gui.util.JmvThread
     */
    public abstract void interruptionRequested();
}
