package org.ngsutils;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMProgramRecord;

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

import org.ngsutils.cli.annotate.GTFAnnotate;
import org.ngsutils.cli.annotate.RepeatAnnotate;
import org.ngsutils.cli.bam.BamCheck;
import org.ngsutils.cli.bam.BamCount;
import org.ngsutils.cli.bam.BamCoverage;
import org.ngsutils.cli.bam.BamFilterCli;
import org.ngsutils.cli.bam.BamStats;
import org.ngsutils.cli.bam.BamToFastq;
import org.ngsutils.cli.bam.BinCount;
import org.ngsutils.cli.bam.PileupCli;
import org.ngsutils.cli.fasta.FastaCLI;
import org.ngsutils.cli.fastq.FastqFilterCli;
import org.ngsutils.cli.fastq.FastqMerge;
import org.ngsutils.cli.fastq.FastqSeparate;
import org.ngsutils.cli.fastq.FastqSort;
import org.ngsutils.cli.fastq.FastqSplit;
import org.ngsutils.cli.fastq.FastqToBam;
import org.ngsutils.cli.gtf.GTFExport;
import org.ngsutils.cli.splicing.FastaJunctions;
import org.ngsutils.cli.splicing.FindEvents;
import org.ngsutils.cli.splicing.JunctionCount;
import org.ngsutils.cli.splicing.JunctionDiffCli;
import org.ngsutils.cli.varcall.GermlineVarCall;
import org.ngsutils.support.cli.Command;
import org.ngsutils.support.cli.Exec;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.HelpRequestedException;

@SuppressWarnings("deprecation")
public class NGSUtils {
	static private Map<String, Class<Exec>> execs = new HashMap<String, Class<Exec>>();
	static {
        loadExec(FastqToBam.class);
		loadExec(FastqSort.class);
		loadExec(FastqMerge.class);
		loadExec(FastqSeparate.class);
        loadExec(FastqSplit.class);
        loadExec(FastqFilterCli.class);
        
        loadExec(BinCount.class);
        loadExec(BamCheck.class);
        loadExec(BamCount.class);
        loadExec(BamCoverage.class);
        loadExec(BamToFastq.class);
        loadExec(BamFilterCli.class);
        loadExec(BamStats.class);
        
        loadExec(JunctionCount.class);
        loadExec(FindEvents.class);
        loadExec(JunctionDiffCli.class);
        loadExec(FastaJunctions.class);
        
        loadExec(FastaCLI.class);
        loadExec(PileupCli.class);
        loadExec(RepeatAnnotate.class);
        
        loadExec(GTFAnnotate.class);
        loadExec(GTFExport.class);
        
        loadExec(GermlineVarCall.class);
	}

	@SuppressWarnings("unchecked")
	private static void loadExec(Class<?> cls) {
		String name = cls.getName().toLowerCase();
		Command cmd = (Command) cls.getAnnotation(Command.class);
		if (cmd != null) {
			name = cmd.name();
		}
		execs.put(name, (Class<Exec>) cls);
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
	            Command c = execs.get(cmd).getAnnotation(Command.class);
	            if (c.deprecated()) {
	                continue;
	            }
                minsize = cmd.length();
	            if (c.experimental()) {
	                minsize += 1;
	            }
			}
		}
		Map<String, List<String>> progs = new HashMap<String, List<String>>();

		for (String cmd : execs.keySet()) {
			Command c = execs.get(cmd).getAnnotation(Command.class);
			if (c != null) {
                if (c.deprecated()) {
                    continue;
                }
				if (!progs.containsKey(c.cat())) {
					progs.put(c.cat(), new ArrayList<String>());
				}

				if (!c.desc().equals("")) {
					spacer = "";
					for (int i = c.experimental() ? cmd.length()+1: cmd.length(); i < minsize; i++) {
						spacer += " ";
					}
					spacer += " - ";
					if (c.experimental()) { 
                        progs.get(c.cat()).add("  " + cmd + "*" + spacer + c.desc());
                    } else {
					    progs.get(c.cat()).add("  " + cmd + spacer + c.desc());
				    }   
				} else {
                    if (c.experimental()) { 
                        progs.get(c.cat()).add("  " + cmd + "*");
                    } else {
                        progs.get(c.cat()).add("  " + cmd);
                    }   
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
		System.err.println("[help]");
        System.err.println("  help command" + spacer
                + "Help message for the given command");
        System.err.println("  license     " + spacer
                + "Display licenses");
		
        System.err.println("");
        System.err.println("* = experimental command");
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
        } else if (args[0].equals("license")) {
            license();
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
				Exec exec = CliFactory.parseArgumentsUsingInstance(execs
						.get(args[0]).newInstance(), (String[]) l
						.toArray(new String[l.size()]));
				exec.exec();
			} catch (HelpRequestedException e) {
				System.err.println(e.getMessage());
				System.err.println("");
				System.err.println(getVersion());
                System.exit(1);
			} catch (ArgumentValidationException e) {
				System.err.println(e.getMessage());
				showHelp(execs.get(args[0]));
                System.exit(1);
            } catch (Throwable t) {
                t.printStackTrace(System.err);                        
                System.exit(1);
			}
			
		} else {
			usage("Unknown command: " + args[0]);
		}
	}

	private static void showHelp(Class<Exec> clazz) throws Exception {
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
				System.err.println("");
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
        System.err.println("Marcus R. Breese <marcus@breese.com>");
        System.err.println("http://ngsutils.org/ngsutilsj");
		System.err.println(getVersion());
	}

    public static SAMProgramRecord buildSAMProgramRecord(String prog) {
        return buildSAMProgramRecord(prog, null);
    }
    public static SAMProgramRecord buildSAMProgramRecord(String prog, SAMFileHeader header) {
        String pgTemplate = "ngsutilsj:" + prog + "-";
        String pgID = pgTemplate;
        boolean found = true;
        int i = 0;
        
        SAMProgramRecord mostRecent = null;
        
        while (found) {
            found = false;
            i++;
            pgID = pgTemplate + i;
            if (header!=null) {
                for (SAMProgramRecord record: header.getProgramRecords()) {
                    if (mostRecent == null) {
                        mostRecent = record;
                    }
                    if (record.getId().equals(pgID)) {
                        found = true;
                    }
                }
            }
        }
        
        SAMProgramRecord programRecord = new SAMProgramRecord(pgID);
        programRecord.setProgramName("ngsutilsj:"+prog);
        programRecord.setProgramVersion(NGSUtils.getVersion());
        programRecord.setCommandLine("ngsutilsj " + NGSUtils.getArgs());
        if (mostRecent!=null) {
            programRecord.setPreviousProgramGroupId(mostRecent.getId());
        }
        return programRecord;
    }
    
    private static void showFile(String fname) throws IOException {
        InputStream is = NGSUtils.class.getClassLoader().getResourceAsStream(fname);
        int c;
        while ((c = is.read()) > -1) {
            System.err.print((char) c);
        }
        is.close();
        
    }
    
    private static void license() throws IOException {
        showFile("LICENSE");
        System.err.println();
        System.err.println();
        showFile("INCLUDES");
    }
}
