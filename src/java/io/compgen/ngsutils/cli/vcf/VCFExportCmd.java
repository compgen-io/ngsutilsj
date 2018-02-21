package io.compgen.ngsutils.cli.vcf;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.export.ExportFormatField;
import io.compgen.ngsutils.vcf.export.ExportInfoField;
import io.compgen.ngsutils.vcf.export.VCFExport;


@Command(name="vcf-export", desc="Export information from a VCF file", category="vcf")
public class VCFExportCmd extends AbstractOutputCommand {
	private String filename = "-";
	
	List<VCFExport> chain = new ArrayList<VCFExport>();
	
	private boolean onlyOutputPass = false;
	private boolean missingBlank = false;
	
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
    	this.onlyOutputPass = onlyOutputPass;
    }
    
    @Option(desc="Set missing values to be \"\".", name="missing-blank")
    public void setMissingBlank(boolean missingBlank) {
    	this.missingBlank = missingBlank;
    }
    
    @Option(desc="Export INFO field", name="info", helpValue="KEY{:ALLELE}", allowMultiple=true)
    public void setInfo(String val) throws CommandArgumentException {
    	boolean ignoreMissing = false;
    	if (val.endsWith(":?")) {
    		ignoreMissing = true;
    		val = val.substring(0,  val.length()-2);
    	}

    	String[] spl = val.split(":");
    	if (spl.length == 1) {
    		chain.add(new ExportInfoField(spl[0], null, ignoreMissing));
    	} else {
    		chain.add(new ExportInfoField(spl[0], spl[1], ignoreMissing));
    	}
    }
    
    @Option(desc="Export FORMAT field", name="format", helpValue="KEY{:SAMPLE:ALLELE}", allowMultiple=true)
    public void setFormat(String val) throws CommandArgumentException {
    	boolean ignoreMissing = false;
    	if (val.endsWith(":?")) {
    		ignoreMissing = true;
    		val = val.substring(0,  val.length()-2);
    	}
    	
    	String[] spl = val.split(":");
    	if (spl.length == 1) {
    		chain.add(new ExportFormatField(spl[0], null, null, ignoreMissing));
    	} else if (spl.length == 2) {
        	chain.add(new ExportFormatField(spl[0], spl[1], null, ignoreMissing));
    	} else {
        	chain.add(new ExportFormatField(spl[0], spl[1], spl[2], ignoreMissing));
    	}
    }
        
    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

	@Exec
	public void exec() throws Exception {		
		VCFReader reader;
		if (filename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(filename);
		}
		
		VCFHeader header = reader.getHeader();
		for (VCFExport export: chain) {
			export.setHeader(header);
			if (missingBlank) {
				export.setMissingValue("");
			}
		}
		
		TabWriter writer = new TabWriter();
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		header.write(baos, false);
		baos.close();
		String headerString = baos.toString();
		for (String s: headerString.split("\n")) {
			writer.write_line(s);
		}
		
        writer.write_line("##ngsutilsj_vcf_exportCommand="+NGSUtils.getArgs());
        writer.write_line("##ngsutilsj_vcf_exportVersion="+NGSUtils.getVersion());

        writer.write("chrom", "pos", "ref", "alt");
		for (VCFExport export: chain) {
			writer.write(export.getFieldNames());
		}
		writer.eol();
				
		Iterator<VCFRecord> it = reader.iterator();
		
		for (VCFRecord rec: IterUtils.wrap(it)) {
			if (onlyOutputPass && rec.isFiltered()) {
				continue;
			}
			
			List<String> outs = new ArrayList<String>();
			
			outs.add(rec.getChrom());
			outs.add(""+rec.getPos());
			outs.add(rec.getRef());
			outs.add(StringUtils.join(",", rec.getAlt()));
			
			for (VCFExport export: chain) {
				export.export(rec, outs);
			}
			writer.write(outs);
			writer.eol();
		}
		
		reader.close();
		writer.close();
	}

}
