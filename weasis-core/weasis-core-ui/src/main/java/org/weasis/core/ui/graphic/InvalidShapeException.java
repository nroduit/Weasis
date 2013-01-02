package org.weasis.core.ui.graphic;

public class InvalidShapeException extends Exception {

    public InvalidShapeException() {
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