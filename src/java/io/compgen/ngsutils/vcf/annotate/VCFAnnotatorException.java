package io.compgen.ngsutils.vcf.annotate;

public class VCFAnnotatorException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2309365188057778809L;

	public VCFAnnotatorException() {
	}

	public VCFAnnotatorException(String message) {
		super(message);
	}

	public VCFAnnotatorException(Throwable cause) {
		super(cause);
	}

	public VCFAnnotatorException(String message, Throwable cause) {
		super(message, cause);
	}

	public VCFAnnotatorException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
