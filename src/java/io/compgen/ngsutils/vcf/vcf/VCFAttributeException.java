package io.compgen.ngsutils.vcf.vcf;

public class VCFAttributeException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -113281045112976040L;

	public VCFAttributeException(String s) {
		super(s);
	}

	/**
	 * 
	 */

	public VCFAttributeException() {
		super();
	}

	public VCFAttributeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public VCFAttributeException(String message, Throwable cause) {
		super(message, cause);
	}

	public VCFAttributeException(Throwable cause) {
		super(cause);
	}

}
