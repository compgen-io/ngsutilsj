package io.compgen.ngsutils.cli.vcf;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.tabix.BGZipOutputStream;
import io.compgen.ngsutils.vcf.VCFReader;


@Command(name="vcf-split", desc="Splits a VCF file into smaller files with N variants per file.")

public class VCFSplit extends AbstractCommand {
	private String filename = null;
	private String baseout = null;
	private int numVariants = 0;
    
    @Option(desc="Base output name to use. Outputs will be ${base}.${num}.vcf.gz.", name="out")
    public void setBaseout(String baseout) {
        this.baseout = baseout;
    }

    @Option(desc="Number of variants to include per subfile", name="num")
    public void setNumVariants(int numVariants) {
        this.numVariants = numVariants;
    }

    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilenames(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

	@Exec
	public void exec() throws Exception {
		if (filename == null) {
    		throw new CommandArgumentException("You need to specify a VCF file. Use (-) for stdin.");
		}
		if (baseout == null) {
    		throw new CommandArgumentException("You need to specify a base output name.");
		}
		if (numVariants <= 0 ) {
    		throw new CommandArgumentException("Invalid --num value.");
		}

		BufferedReader reader;
		int bufSize = 1024*1024;
		FileChannel channel = null;
		
		if (filename.equals("-")) {
			// we will assume uncompressed stdin
            reader = new BufferedReader(new InputStreamReader(System.in), bufSize);

		} else if (VCFReader.isGZipFile(filename)) {
			// gzip and bgzip will be handled the same as we aren't doing
			// random io.
			if (verbose) {
				System.err.println("Reading bgzip/gzip file");
			}
            FileInputStream fis = new FileInputStream(filename);
            channel = fis.getChannel();
            reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(fis)), bufSize);
		} else {
			// plain text
			if (verbose) {
				System.err.println("Reading text file");
			}
            FileInputStream fis = new FileInputStream(filename);
            channel = fis.getChannel();
            reader = new BufferedReader(new InputStreamReader(fis),  bufSize);
		}

		boolean inheader = true;
		boolean newLine = true;
		int outFileNum = 0;
		int lineCount = 0;
		int bufPos = 0;
		int nCharRead = 0;
		
		List<String> headerLines = new ArrayList<String>();
		String chromLine = null;
		
		StringBuilder sb = new StringBuilder();
		
		BufferedOutputStream bos = null;
		
		// we'll keep a 1MiB buffer (it never reads this much)
		char[] buf = new char[bufSize];		
		
		while (true) {
			if (bufPos >= nCharRead) {
				nCharRead = reader.read(buf);
				if (nCharRead == -1) {
					break;
				}
				bufPos = 0;				
			}

			if (inheader) {
				if (newLine) {
					if (buf[bufPos] == '#') {
						if (sb.length() > 0) {
							if (verbose) {
								System.err.println("Added header: "+sb.toString().strip());
							}
							headerLines.add(sb.toString());
						}
						sb = new StringBuilder();
						sb.append(buf[bufPos]);
						bufPos ++;
						newLine = false;
					} else {
						inheader = false;
						// the last line is the chrom line
						chromLine = sb.toString();
						sb = null;

						// add extra header lines now...
						headerLines.add("##ngsutilsj_vcf_splitCommand="+NGSUtils.getArgs()+"\n");
						if (!headerLines.contains("##ngsutilsj_vcf_splitVersion="+NGSUtils.getVersion()+"\n")) {
							headerLines.add("##ngsutilsj_vcf_splitVersion="+NGSUtils.getVersion()+"\n");
						}
						
						if (verbose) {
							System.err.println("Found sample line");
						}

						// don't do anything and fall through to the main processing						
					}
				} else {
					sb.append(buf[bufPos]);
					if (buf[bufPos] == '\n') {
						newLine = true;
					}
					bufPos ++;
				}
				continue;
			}
			
			if (bos == null) {
				outFileNum++;
				if (verbose) {
					System.err.println("read: " + channel.position()+"/"+channel.size()+" "+(100*channel.position()/channel.size())+"%");
					System.err.println(baseout +"."+outFileNum+".vcf.gz");
				}
				// write to tmp file first
				bos = new BufferedOutputStream(new BGZipOutputStream(baseout +"."+outFileNum+".vcf.gz.tmp"));
				for (String line: headerLines) {
					bos.write(line.getBytes());
				}
				bos.write(chromLine.getBytes());
				lineCount = 0;
			}
			
			bos.write(buf[bufPos]);

			if (buf[bufPos] == '\n') {
				lineCount ++;
				if (lineCount>=numVariants) {
					// end of this file. close it and
					// let the new one open in the next round;
					bos.flush();
					bos.close();

					// rename temp file
					File f = new File(baseout +"."+outFileNum+".vcf.gz.tmp");
					f.renameTo(new File(baseout +"."+outFileNum+".vcf.gz"));
					bos = null;
				}
			}
			bufPos ++;
		}		
		
		if (bos != null) {
			bos.flush();
			bos.close();

			// rename temp file
			File f = new File(baseout +"."+outFileNum+".vcf.gz.tmp");
			f.renameTo(new File(baseout +"."+outFileNum+".vcf.gz"));
		}

		reader.close();
		
		if (verbose) {
			System.err.println("Done");
		}
	}
}
