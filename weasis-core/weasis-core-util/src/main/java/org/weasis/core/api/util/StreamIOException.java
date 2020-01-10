/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
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
