package io.compgen.ngsutils.cli.vcf;

import java.util.HashSet;
import java.util.Set;

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


@Command(name="vcf-strip", desc="Remove all annotation and sample information (FILTER,INFO,FORMAT,dbSNP) but keep output in VCF format", category="vcf")
public class VCFStrip extends AbstractOutputCommand {
	private String filename = "-";

    private boolean onlyOutputPass = false;
    private boolean onlySNVs = false;

    private boolean removeDBSNP = false;
    private Set<String> removeFilter = null;
    private Set<String> removeInfo = null;
    private Set<String> removeFormat = null;
  
    @Option(desc="Only output passing variants (warning -- this works on the post-stripped filters)", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
        this.onlyOutputPass = onlyOutputPass;
    }
    
    @Option(desc="Only output SNVs (no idels)", name="only-snvs")
    public void setOnlySNVs(boolean onlySNVs) {
        this.onlySNVs = onlySNVs;
    }
    
    @Option(desc="Remove ALL annotations", name="all")
    public void setStripAll(boolean strip) {
        setStripDBSNP(true);
        setStripInfo("*");
        setStripFormat("*");
        setStripFilter("*");
    }
    
    @Option(desc="Remove specific INFO annotations (multiple allowed, wildcard '*' allowed)", name="info")
    public void setStripInfo(String remove) {
        if (removeInfo == null) {
            removeInfo = new HashSet<String>();
        }
        removeInfo.add(remove);
    }
    
    @Option(desc="Remove specific FORMAT annotations (multiple allowed, wildcard '*' allowed)", name="format")
    public void setStripFormat(String remove) {
        if (removeFormat == null) {
            removeFormat = new HashSet<String>();
        }
        removeFormat.add(remove);
    }
    
    @Option(desc="Remove specific FILTER annotations (multiple allowed, wildcard '*' allowed)", name="filter")
    public void setStripFilter(String remove) {
        if (removeFilter == null) {
            removeFilter = new HashSet<String>();
        }
        removeFilter.add(remove);
    }
    
    @Option(desc="Remove DBSNP annotations", name="dbsnp")
    public void setStripDBSNP(boolean removeDBSNP) {
        this.removeDBSNP = removeDBSNP;
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
		
		if (removeDBSNP) {
		    reader.setRemoveID(true);
		}
        if (removeFilter != null) {
            reader.addRemoveFilter(removeFilter);
        }
        if (removeFormat != null) {
            reader.addRemoveFormat(removeFormat);
        }
        if (removeInfo != null) {
            reader.addRemoveInfo(removeInfo);
        }
		
		VCFHeader header = reader.getHeader();
		header.addLine("##ngsutilsj_vcf_stripCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_stripVersion="+NGSUtils.getVersion())) {
		    header.addLine("##ngsutilsj_vcf_stripVersion="+NGSUtils.getVersion());
		}
	
		VCFWriter writer;
		if (out.equals("-")) {
			writer = new VCFWriter(System.out, header);
		} else {
			writer = new VCFWriter(out, header);
		}

		for (VCFRecord rec: IterUtils.wrap(reader.iterator())) {
            if (onlyOutputPass && rec.isFiltered()) {
                continue;
            }
            if (onlySNVs) {
                if (rec.getRef().length() != 1) {
                    continue;
                }
                boolean altOK = true;
                for (String alt: rec.getAlt()) {
                    if (alt.length() != 1) {
                        // if ANY of the alt's are indels, remove the record
                        altOK = false;
                        continue;
                    }
                }
                if (!altOK) {
                    continue;
                }
            }
	        writer.write(rec);
		}		
		reader.close();
		writer.close();
	}

}
