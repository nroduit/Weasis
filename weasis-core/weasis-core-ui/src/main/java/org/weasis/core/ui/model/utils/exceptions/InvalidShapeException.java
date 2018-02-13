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
package org.weasis.core.ui.model.utils.exceptions;

@SuppressWarnings("serial")
public class InvalidShapeException extends Exception {

    public InvalidShapeException() {
        super();
    }

    public InvalidShapeException(String message) {
        super(message);
    }

    public InvalidShapeException(Throwable cause) {
        super(cause);
    }

    public InvalidShapeException(String message, Throwable cause) {
        super(message, cause);
    }

}