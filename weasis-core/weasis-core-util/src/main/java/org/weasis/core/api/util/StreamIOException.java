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
package org.weasis.core.api.util;

import java.io.IOException;

public class StreamIOException extends IOException {
    private static final long serialVersionUID = -8606733870761909715L;

    public StreamIOException() {
        super();
    }

    public StreamIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public StreamIOException(String message) {
        super(message);
    }

    public StreamIOException(Throwable cause) {
        super(cause);
    }
}
