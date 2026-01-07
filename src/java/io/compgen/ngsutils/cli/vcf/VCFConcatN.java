package io.compgen.ngsutils.cli.vcf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFWriter;


@Command(name="vcf-concat-n", desc="Concatenate VCF files that have different variants but the same samples.", category="vcf", doc=""
		+ "This command can be used to re-combine VCF files that have been split into \n"
		+ "several smaller chunks. Crucially, this command will merge the files \n"
		+ "in a sorted manner the same time.\n"
		+ "\n"
		+ "This assumes the VCF file is coordinate sorted and the chromosomes are in a \n"
		+ "consistent order. If present in the header, chromosome order will be \n"
		+ "determined by the order of ##contig lines.\n"
		+ "\n"
		+ "If the files have different INFO, FORMAT, or FILTER elements, they will all \n"
		+ "be present in the output file.\n"
		+ "\n"
		+ "This version assumes that there are multiple split VCF files named inputA.1.vcf.gz,\n"
		+ "inputA.2.vcf.gz and these are in order. This way, only \".1\" files need to be opened\n"
		+ "at one time. You can have multiple inputs, but they should all conform to: \n"
		+ "inputA.1.vcf.gz inputB.1.vcf.gz inputC.1.vcf.gz, etc... If the files are named with\n"
		+ "padded zeros (*.001.*, *.002.*), then this will be respected.\n"
		+ "\n"
		+ "This version also DOES NOT PARSE THE VCF RECORDS. It assumes that each record is well-formed\n"
		+ "and in the proper sample order. Variant positions can only be present in one group of input files.\n"
		+ "\n"
		+ "The final merged output is written to stdout.")

public class VCFConcatN extends AbstractCommand {
	private String[] filenames = null;
    
    @UnnamedArg(name = "input1.vcf...", required=true)
    public void setFilenames(String[] filenames) throws CommandArgumentException {
    	this.filenames = filenames;
    }

	@SuppressWarnings("resource")
	@Exec
	public void exec() throws Exception {
		if (filenames == null || filenames.length < 1) {
    		throw new CommandArgumentException("You need to specify at least one input VCF file.");
		}

		for (String fname: filenames) {
			if (!new File(fname).exists()) {
	    		throw new CommandArgumentException("Missing file: "+ fname);
			}
		}

		VCFReader primary = new VCFReader(filenames[0]);
		VCFHeader header = primary.getHeader();

		for (int i=1; i<filenames.length; i++) {
			VCFReader r = new VCFReader(filenames[i]);
			for (String sample: r.getHeader().getSamples()) {
				if (!header.getSamples().contains(sample)) {
		    		throw new CommandArgumentException("File contains an extra sample? "+ sample);
				}
			}

			for (String filter: r.getHeader().getFilterIDs()) {
				if (!header.getFilterIDs().contains(filter)) {
					header.addFilter(r.getHeader().getFilterDef(filter));
				}
			}

			for (String info: r.getHeader().getInfoIDs()) {
				if (!header.getInfoIDs().contains(info)) {
					header.addInfo(r.getHeader().getInfoDef(info));
				}
			}

			for (String format: r.getHeader().getFormatIDs()) {
				if (!header.getFormatIDs().contains(format)) {
					header.addFormat(r.getHeader().getFormatDef(format));
				}
			}
			// TODO: actually check the order
			for (String contig: r.getHeader().getContigNames()) {
				if (!header.getContigNames().contains(contig)) {
		    		throw new CommandArgumentException("Unknown contig: " + contig +"! All chromosomes must be listed as a contig and the order must be the same between input files.");
				}
			}
			for (String alt: r.getHeader().getAlts()) {
				if (!header.getAlts().contains(alt)) {
					header.addAlt(r.getHeader().getAltDef(alt));
				}
			}
			for (String line: r.getHeader().getLines()) {
				if (!header.contains(line)) {
					header.addLine(line);
				}
			}
			r.close();
		}
		
		header.addLine("##ngsutilsj_vcf_concatCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_concatVersion="+NGSUtils.getVersion())) {
		    header.addLine("##ngsutilsj_vcf_concatVersion="+NGSUtils.getVersion());
		}

		Map<String, Integer> contigOrder = new HashMap<String, Integer>();
		for (String contig: header.getContigNames()) { 
			if (verbose) {
				System.err.println(contig+" ["+contigOrder.size()+"]");
			}
			contigOrder.put(contig, contigOrder.size());
		}
		
		primary.close();
		
		VCFWriter writer = new VCFWriter(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				System.out.write(b);
			}}, header);
		writer.flush();
		writer.close();
		
		int[] curChrom = new int[filenames.length];
		String[] curChromStr = new String[filenames.length];
		int[] curPos = new int[filenames.length];
		
		int[] inputIndex = new int[filenames.length];
		int[] inputPadding = new int[filenames.length];
		
		String[] fnamePrefix = new String[filenames.length];
		String[] fnameSuffix = new String[filenames.length];
		String[] curFilename = new String[filenames.length];
		
		BufferedReader[] readers = new BufferedReader[filenames.length];

		for (int i=0; i<filenames.length; i++) {	
			File f = new File(filenames[i]);
			inputIndex[i] = 1;
			fnamePrefix[i] = null;
			fnameSuffix[i] = "";
			String[] spl = f.getName().split("\\.");
			boolean found = false;
			for (String el: spl) {
				if (found) {
					fnameSuffix[i] += "." + el;
				} else {
					try {
						int num = Integer.parseInt(el);
						if (num == 1) {
							found = true;
							int zeros = 0;
							if (el.charAt(zeros) == '0') {
								zeros++;
							}
							inputPadding[i] = zeros + 1;
						}
					} catch (NumberFormatException e) {
						// skip
					}
					if (!found) {
						if (fnamePrefix[i] != null) {
							fnamePrefix[i] += "." + el;
						} else {
							fnamePrefix[i] = el;							
						}
					}
				}
			}
			if (f.getParent()!=null) {
				fnamePrefix[i] = f.getParent() + File.separator + fnamePrefix[i] + ".";
			} else {
				fnamePrefix[i] = fnamePrefix[i] + ".";
			}
			
			String chrom = null;
			while (chrom == null) {
				String fname = buildFilename(fnamePrefix[i], fnameSuffix[i], inputIndex[i], inputPadding[i]);
				System.err.println("Opening file: "+fname);
				File f2 = new File(fname);
				if (f2.exists()) {
					curFilename[i] = fname;
					inputIndex[i]++;
					if (readers[i] != null) {
						readers[i].close();
					}
					readers[i] = new BufferedReader(new InputStreamReader(new GzipCompressorInputStream(new FileInputStream(f2), true)));
					chrom = readChrom(readers[i]);
					System.err.println("  chrom: " + chrom);
					if (chrom != null) {
						int pos = readPos(readers[i]);
						System.err.println("  pos: " + pos);
						curChrom[i] = contigOrder.get(chrom);
						curChromStr[i] = chrom;
						curPos[i] = pos;
					}
				} else {
					readers[i] = null;
					curChrom[i] = -1;
					curPos[i] = -1;
					break;
				}
			}
		}
		
		
		while (true) {
			int minIdx = -1;
			int minChrom = -1;
			int minPos = -1;
			String minChromStr = null;
			for (int i=0; i<readers.length; i++) {
				if (curChrom[i] > -1) {
					if (minChrom == -1 || curChrom[i] < minChrom) {
						minIdx = i;
						minChrom = curChrom[i];
						minPos = curPos[i];
						minChromStr = curChromStr[i];
					} else if (curChrom[i] == minChrom && curPos[i] < minPos) {
						minIdx = i;
						minChrom = curChrom[i];
						minPos = curPos[i];
						minChromStr = curChromStr[i];
					}
				}
			}
			
//			System.err.println("["+minIdx+"] " + curFilename[minIdx] + " " + minChrom +":"+minPos);
			
			if (minIdx == -1) {
				break;
			}
		
			System.out.print(minChromStr + "\t" + minPos + "\t");	
			writeEOL(readers[minIdx], System.out);

			curChrom[minIdx] = -1;
			curChromStr[minIdx] = null;
			curPos[minIdx] = -1;

			String chrom = readChrom(readers[minIdx]);
			if (chrom != null) {
				int pos = readPos(readers[minIdx]);
				curChrom[minIdx] = contigOrder.get(chrom);
				curPos[minIdx] = pos;
				curChromStr[minIdx] = chrom;
			} else {
				while (chrom == null) {
					String fname = buildFilename(fnamePrefix[minIdx], fnameSuffix[minIdx], inputIndex[minIdx], inputPadding[minIdx]);
					System.err.println("Opening file: "+fname);
					File f = new File(fname);
					if (f.exists()) {
						curFilename[minIdx] = fname;
						inputIndex[minIdx]++;
						readers[minIdx].close();
						readers[minIdx] = new BufferedReader(new InputStreamReader(new GzipCompressorInputStream(new FileInputStream(f), true)));
						chrom = readChrom(readers[minIdx]);
						System.err.println("  chrom: " + chrom);
						if (chrom != null) {
							int pos = readPos(readers[minIdx]);
							System.err.println("  pos: " + pos);
							curChrom[minIdx] = contigOrder.get(chrom);
							curPos[minIdx] = pos;
							curChromStr[minIdx] = chrom;
						}
					}
				}
			}
		}
	}

	private void writeEOL(BufferedReader r, OutputStream os) throws IOException {
		int c;
		while (true) { 
			c = r.read();
			os.write((char) c);
			if (c == '\n') {
				os.flush();
				return;
			}
		}
	}

	private int readPos(BufferedReader r) throws IOException {
		String buf = "";
		int c = r.read();
		if (c == -1) {
			return -1;
		}
		while (c != '\t') { 
			buf = buf + (char) c;
			c = r.read();
			if (c == -1) {
				return -1;
			}
		}
		return Integer.parseInt(buf);
	}

	private String readChrom(BufferedReader r) throws IOException {
		String buf = "";

		int c = r.read();
		if (c == -1) {
			return null;
		}
		
		// skip comments
		while (c == '#') {
			while (c != '\n') {
				c = r.read();
			}
		 	c = r.read();
			if (c == -1) {
				return null;
			}
		}
		while (c != '\t') { 
			buf = buf + (char) c;
			c = r.read();
		}
		return buf;
	}

	private String buildFilename(String prefix, String suffix, int i, int zeroPadding) {
		String val = ""+i;
		while (val.length() < zeroPadding) {
			val = "0" + val;
		}
		return prefix + val + suffix;
	}


}
