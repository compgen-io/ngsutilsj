package io.compgen.ngsutils.vcf.annotate;

public class VCFAnnotatorMissingAltException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2309365188057778809L;

	public VCFAnnotatorMissingAltException() {
	}

	public VCFAnnotatorMissingAltException(String message) {
		super(message);
	}

	public VCFAnnotatorMissingAltException(Throwable cause) {
		super(cause);
	}

	public VCFAnnotatorMissingAltException(String message, Throwable cause) {
		super(message, cause);
	}

	public VCFAnnotatorMissingAltException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
