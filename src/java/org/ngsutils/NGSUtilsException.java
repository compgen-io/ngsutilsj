package org.ngsutils;

public class NGSUtilsException extends Exception {
	public NGSUtilsException(String msg) {
		super(msg);
	}
	public NGSUtilsException(Exception e) {
		super(e);
	}

	private static final long serialVersionUID = 6388968582218017316L;

}
