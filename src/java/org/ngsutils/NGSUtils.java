package org.ngsutils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ngsutils.cli.Command;
import org.ngsutils.cli.NGSExec;
import org.ngsutils.cli.annotate.RepeatAnnotate;
import org.ngsutils.cli.bam.BAMCount;
import org.ngsutils.cli.fastq.FastqFilter;
import org.ngsutils.cli.fastq.FastqMerge;
import org.ngsutils.cli.fastq.FastqSeparate;
import org.ngsutils.cli.fastq.FastqSort;
import org.ngsutils.cli.fastq.FastqSplit;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.HelpRequestedException;

public class NGSUtils {
	static private Map<String, Class<NGSExec>> execs = new HashMap<String, Class<NGSExec>>();
	static {
		loadExec(FastqSort.class);
		loadExec(FastqMerge.class);
		loadExec(FastqSeparate.class);
		loadExec(FastqSplit.class);
        loadExec(FastqFilter.class);
        loadExec(BAMCount.class);
        loadExec(RepeatAnnotate.class);
	}

	@SuppressWarnings("unchecked")
	private static void loadExec(Class<?> cls) {
		String name = cls.getName().toLowerCase();
		Command cmd = (Command) cls.getAnnotation(Command.class);
		if (cmd != null) {
			name = cmd.name();
		}
		execs.put(name, (Class<NGSExec>) cls);
	}

	public static void usage() {
		usage(null);
	}

	public static void usage(String msg) {
		if (msg != null) {
			System.err.println(msg);
			System.err.println();
		}
		System.err.println("NGSUtilsJ - Tools for processing NGS datafiles");
		System.err.println("");
		System.err.println("Usage: ngsutilsj cmd {options}");
		System.err.println("");
		System.err.println("Available commands:");
		int minsize = 12;
		String spacer = "            ";
		for (String cmd : execs.keySet()) {
			if (cmd.length() > minsize) {
				minsize = cmd.length();
			}
		}
		Map<String, List<String>> progs = new HashMap<String, List<String>>();

		for (String cmd : execs.keySet()) {
			Command c = execs.get(cmd).getAnnotation(Command.class);
			if (c != null) {
				if (!progs.containsKey(c.cat())) {
					progs.put(c.cat(), new ArrayList<String>());
				}

				if (!c.desc().equals("")) {
					spacer = "";
					for (int i = cmd.length(); i < minsize; i++) {
						spacer += " ";
					}
					spacer += " - ";
					progs.get(c.cat()).add("  " + cmd + spacer + c.desc());
				} else {
					progs.get(c.cat()).add("  " + cmd);
				}
			} else {
				if (!progs.containsKey("General")) {
					progs.put("General", new ArrayList<String>());
				}
				progs.get("General").add("  " + cmd);

			}
		}

		List<String> cats = new ArrayList<String>(progs.keySet());
		Collections.sort(cats);

		for (String cat : cats) {
            System.err.println("[" + cat + "]");
			Collections.sort(progs.get(cat));
			for (String line : progs.get(cat)) {
				System.err.println(line);
			}
            System.err.println("");
		}

		spacer = "";
		for (int i = 12; i < minsize; i++) {
			spacer += " ";
		}
		spacer += " - ";
		System.err.println("  help command" + spacer
				+ "Help message for the given command");
		
		System.err.println("");
		System.err.println(getVersion());
	}
	
	public static String getVersion() {
		try {
			InputStream is = NGSUtils.class.getResourceAsStream("/VERSION"); 
			BufferedReader r = new BufferedReader(new InputStreamReader(is));
			return r.readLine();
		} catch (IOException e) {
			return "ngsutilsj-unknown";
		}
	}

	private static String args;
	
	public static String getArgs() {
	    return args;
	}
	
	public static void main(String[] args) throws Exception {
	    NGSUtils.args = "";
	    for (String arg: args) {
	        if (NGSUtils.args.equals("")) {
	            NGSUtils.args = arg;
	        } else {
	            NGSUtils.args = NGSUtils.args + " " + arg;
	        }
	    }
	    
		if (args.length == 0) {
			usage();
		} else if (args[0].equals("help")) {
			if (args.length == 1) {
				usage();
			} else {
				if (!execs.containsKey(args[1])) {
					usage("Unknown command: " + args[1]);
				} else {
					showHelp(execs.get(args[1]));
				}
			}
		} else if (execs.containsKey(args[0])) {
			List<String> l = Arrays.asList(args).subList(1, args.length);
			try {
				NGSExec exec = CliFactory.parseArgumentsUsingInstance(execs
						.get(args[0]).newInstance(), (String[]) l
						.toArray(new String[l.size()]));
				exec.exec();
			} catch (HelpRequestedException e) {
				System.err.println(e.getMessage());
				System.err.println("");
				System.err.println(getVersion());
			} catch (ArgumentValidationException e) {
				System.err.println(e.getMessage());
				showHelp(execs.get(args[0]));
			}
		} else {
			usage("Unknown command: " + args[0]);
		}
	}

	private static void showHelp(Class<NGSExec> clazz) throws Exception {
		Command cmd = clazz.getAnnotation(Command.class);
		if (cmd != null) {
			if (cmd.desc().equals("")) {
				System.err.println(cmd.name());
			} else {
				System.err.println(cmd.name() + " - " + cmd.desc());
			}
			System.err.println("");

			if (!cmd.doc().equals("")) {
				System.err.println(cmd.doc());
			}
		} else {
			System.err.println(clazz.getName().toLowerCase());
		}
		String[] args = { "--help" };
		try {
			CliFactory.parseArgumentsUsingInstance(clazz.newInstance(), args);
		} catch (HelpRequestedException e) {
			System.err.println(e.getMessage());
		}
		System.err.println("");
		System.err.println(getVersion());
	}
}
