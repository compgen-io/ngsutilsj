package io.compgen.ngsutils.vcf.vcf;

public class VCFParseException extends Exception {

	public VCFParseException(String s) {
		super(s);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -3283770429177564396L;

	public VCFParseException() {
		super();
	}

	public VCFParseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public VCFParseException(String message, Throwable cause) {
		super(message, cause);
	}

	public VCFParseException(Throwable cause) {
		super(cause);
	}

}
