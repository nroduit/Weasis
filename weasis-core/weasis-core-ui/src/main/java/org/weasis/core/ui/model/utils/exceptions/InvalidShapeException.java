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