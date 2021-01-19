package org.intocps.maestro.faultinject;

public class WrapperException extends RuntimeException {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public WrapperException() {
    }

    public WrapperException(String message) {
        super(message);
    }

    public WrapperException(String message, Throwable cause) {
        super(message, cause);
    }

    public WrapperException(Throwable cause) {
        super(cause);
    }

    public WrapperException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
