package io.compgen.ngsutils.cli.vcf;

import java.util.ArrayList;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFWriter;


@Command(name="vcf-rename", desc="Change the names of samples", category="vcf")
public class VCFRenameSample extends AbstractOutputCommand {
	private String filename = "-";
    
	private List<String> oldNames = new ArrayList<String>();
	private List<String> newNames = new ArrayList<String>();
	
	
    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

    @Option(desc="Sample to rename (colon delimited: NAME:NEWNAME, can use sample number for NAME)", name="sample", allowMultiple=true)
    public void setSample(String val) throws CommandArgumentException {
    	String[] spl = val.split(":");
    	if (spl.length != 2) {
    		throw new CommandArgumentException("Invalid option for --sample: " + val);
    	}
    	oldNames.add(spl[0]);
    	newNames.add(spl[1]);
    }

	@Exec
	public void exec() throws Exception {
    	if (oldNames.size() ==0 ) {
    		throw new CommandArgumentException("You must specify at least one sample to rename!");
    	}

		VCFReader reader = new VCFReader(filename);
		
		VCFHeader header = reader.getHeader();
		header.addLine("##ngsutilsj_vcf_renameCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_renameVersion="+NGSUtils.getVersion())) {
		    header.addLine("##ngsutilsj_vcf_renameVersion="+NGSUtils.getVersion());
		}

		for (int i=0; i<oldNames.size(); i++) {
			header.renameSample(oldNames.get(i), newNames.get(i));
		}
		
		VCFWriter writer = new VCFWriter(out, header);
//		VCFWriter writer;
//		if (out.equals("-")) {
//			writer = new VCFWriter(System.out, header);
//		} else {
//			writer = new VCFWriter(out, header);
//		}

		for (VCFRecord rec: IterUtils.wrap(reader.iterator())) {

            writer.write(rec);
		}		
		reader.close();
		writer.close();
	}
}