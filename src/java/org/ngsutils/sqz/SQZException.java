package org.ngsutils.sqz;

public class SQZException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 6262949347384064664L;

    public SQZException() {
    }

    public SQZException(String message) {
        super(message);
    }

    public SQZException(Throwable cause) {
        super(cause);
    }

    public SQZException(String message, Throwable cause) {
        super(message, cause);
    }

    public SQZException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
