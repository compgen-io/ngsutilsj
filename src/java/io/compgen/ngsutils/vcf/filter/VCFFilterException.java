package io.compgen.ngsutils.vcf.filter;

public class VCFFilterException extends Exception {

	public VCFFilterException(String s) {
		super(s);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -3283770429177564396L;

	public VCFFilterException() {
		super();
	}

	public VCFFilterException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public VCFFilterException(String message, Throwable cause) {
		super(message, cause);
	}

	public VCFFilterException(Throwable cause) {
		super(cause);
	}

}
