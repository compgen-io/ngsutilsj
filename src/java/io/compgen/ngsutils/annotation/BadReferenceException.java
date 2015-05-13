package io.compgen.ngsutils.annotation;

public class BadReferenceException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 2165919064411333166L;

    public BadReferenceException() {
    }

    public BadReferenceException(String message) {
        super(message);
    }
}
