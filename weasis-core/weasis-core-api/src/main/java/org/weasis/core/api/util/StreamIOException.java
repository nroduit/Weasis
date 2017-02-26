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
