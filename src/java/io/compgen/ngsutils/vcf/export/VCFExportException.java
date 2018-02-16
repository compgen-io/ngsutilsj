package io.compgen.ngsutils.vcf.export;

public class VCFExportException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6823889919862683351L;

	/**
	 * 
	 */

	public VCFExportException() {
	}

	public VCFExportException(String message) {
		super(message);
	}

	public VCFExportException(Throwable cause) {
		super(cause);
	}

	public VCFExportException(String message, Throwable cause) {
		super(message, cause);
	}

	public VCFExportException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
