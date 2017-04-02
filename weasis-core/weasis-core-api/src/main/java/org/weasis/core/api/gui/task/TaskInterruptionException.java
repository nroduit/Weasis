/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.gui.task;

public class TaskInterruptionException extends RuntimeException {

    private static final long serialVersionUID = -2417786582629445179L;

    public TaskInterruptionException() {
        super();
    }

    public TaskInterruptionException(String message, Throwable cause) {
        super(message, cause);
    }

    public TaskInterruptionException(String message) {
        super(message);
    }

    public TaskInterruptionException(Throwable cause) {
        super(cause);
    }

}
