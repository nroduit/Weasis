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
