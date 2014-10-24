package org.ngsutils.support;

/**
 * This relies on org.ngsutils.support.tty.fd[0-2] being set by the ngsutils stub
 * shell script.
 * @author mbreese
 *
 */
public class TTY {
    public static boolean isattyStdIn() {
        String val = System.getProperty("org.ngsutils.support.tty.fd0");
        if (val == null || !val.equals("F")) {
            return true;
        }
        return false;
    }
    public static boolean isattyStdOut() {
        String val = System.getProperty("org.ngsutils.support.tty.fd1");
        if (val == null || !val.equals("F")) {
            return true;
        }
        return false;
    }
    public static boolean isattyStdErr() {
        String val = System.getProperty("org.ngsutils.support.tty.fd2");
        if (val == null || !val.equals("F")) {
            return true;
        }
        return false;
    }
}
