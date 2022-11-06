package io.compgen.ngsutils.support;

import java.io.File;

public class FileUtils {

	/**
	 * Replace ~/foo/bar filenames with $HOME/foo/bar
	 * 
	 * Usually this is done by the shell, but in some cases, filenames get passed in alternative ways, so we need to manually adjust.
	 * 
	 * @param path
	 * @return
	 */
	public static String expandUserPath(String path) {
		if (path.startsWith("~" + File.separator)) {
		    path = System.getProperty("user.home") + path.substring(1);
		} else if (path.startsWith("~")) {
		    throw new UnsupportedOperationException("Path expansion for other user home-directories is not supported.");
		}
		return path;
	}
}
