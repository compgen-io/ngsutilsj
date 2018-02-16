package io.compgen.ngsutils.vcf.support;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

public class LogUtils {

	private static Set<String> printed = new HashSet<String>();
	
	public static void printOnce(PrintStream ps, String str) {
		if (!printed.contains(str)) {
			printed.add(str);
			ps.println(str);
		}
	}

}
