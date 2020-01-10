/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
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
