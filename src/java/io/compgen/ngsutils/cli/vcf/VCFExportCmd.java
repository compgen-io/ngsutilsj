package io.compgen.ngsutils.cli.vcf;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import io.compgen.ngsutils.vcf.export.FilterExport;
import io.compgen.ngsutils.vcf.export.IDExport;
import io.compgen.ngsutils.vcf.export.QualExport;
import io.compgen.ngsutils.vcf.export.VCFExport;


@Command(name="vcf-export", desc="Export information from a VCF file", category="vcf")
public class VCFExportCmd extends AbstractOutputCommand {
	private String filename = "-";
	
	List<VCFExport> chain = new ArrayList<VCFExport>();
	
	Map<String, String> extras = new LinkedHashMap<String,String>();
	
    private boolean noHeader = false;
    private boolean noVCFHeader = false;
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
    
    @Option(desc="Don't export the header line", name="no-header")
    public void setNoHeader(boolean noHeader) {
        this.noHeader = noHeader;
    }

    @Option(desc="Don't export the VCF header", name="no-vcf-header")
    public void setNoVCFHeader(boolean noVCFHeader) {
        this.noVCFHeader = noVCFHeader;
    }

    @Option(desc="Export VCF Filters", name="filter")
    public void setFilter() throws CommandArgumentException {
        chain.add(new FilterExport());
    }
    
    @Option(desc="Export VCF ID", name="id")
    public void setID() throws CommandArgumentException {
        chain.add(new IDExport());
    }
    
    @Option(desc="Export VCF Qual", name="qual")
    public void setQual() throws CommandArgumentException {
        chain.add(new QualExport());
    }
    
    @Option(desc="Add a column to the beginning of the line", name="col", helpValue="{name:}value", allowMultiple=true)
    public void setCol(String val) throws CommandArgumentException {
        if (val.indexOf(":") > -1) {
            String[] ar = val.split(":");
            extras.put(ar[0], ar[1]);
        } else {
            extras.put("col"+(extras.size()+1), val);
        }
    }
    
    @Option(desc="Export FORMAT field", name="format", helpValue="KEY{:SAMPLE:ALLELE}", allowMultiple=true)
    public void setFormat(String val) throws CommandArgumentException {
        boolean ignoreMissing = true;
        
        if (val.endsWith(":!")) {
            ignoreMissing = false;
            val = val.substring(0,  val.length()-2);
        } else if (val.endsWith(":?")) {
            ignoreMissing = true;
            val = val.substring(0,  val.length()-2);
        }
        
        String key=null;
        String sample=null;
        String allele=null;
        String newName=null;
        
        
        String[] spl = val.split(":");
        
        for (String s: spl) {
            if (key == null) {
                key = s;
            } else if (sample == null) {
                sample = s;
            } else if (allele == null) {
                allele = s;
            } else if (newName == null) {
                newName = s;
            }
        }

        if (key.equals("") || key == null) {
            throw new CommandArgumentException("Missing argument for --format!");
        }
        if (sample.equals("")) {
            sample = null;
        }
        if (allele.equals("")) {
            allele = null;
        }
        if (newName.equals("")) {
            newName = null;
        }

        if (newName != null && sample == null) {
            throw new CommandArgumentException("Invalid argument for --format! You must specify a SAMPLE is ALIAS is given.");
        }
        
        chain.add(new ExportFormatField(key, sample, allele, ignoreMissing, newName));
    }
        
    @Option(desc="Export INFO field", name="info", helpValue="KEY{:ALLELE}", allowMultiple=true)
    public void setInfo(String val) throws CommandArgumentException {
    	boolean ignoreMissing = true;
        if (val.endsWith(":!")) {
            ignoreMissing = false;
            val = val.substring(0,  val.length()-2);
        } else if (val.endsWith(":?")) {
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

        TabWriter writer = new TabWriter();

        if (!noVCFHeader) {
    		VCFHeader header = reader.getHeader();
    		for (VCFExport export: chain) {
    			export.setHeader(header);
    			if (missingBlank) {
    				export.setMissingValue("");
    			}
    		}
    		
    		ByteArrayOutputStream baos = new ByteArrayOutputStream();
    		header.write(baos, false, false);
    		baos.close();
    		String headerString = baos.toString();
    		for (String s: headerString.split("\n")) {
    			writer.write_line(s);
    		}
    		
            writer.write_line("##ngsutilsj_vcf_exportCommand="+NGSUtils.getArgs());
            writer.write_line("##ngsutilsj_vcf_exportVersion="+NGSUtils.getVersion());
		}

        if (!noHeader) {
            // write the column names
            for (String k: extras.keySet()) {
                writer.write(k);
            }
            
            writer.write("chrom", "pos", "ref", "alt");
    		for (VCFExport export: chain) {
    			writer.write(export.getFieldNames());
    		}
    		writer.eol();
        }

        Iterator<VCFRecord> it = reader.iterator();
		
		for (VCFRecord rec: IterUtils.wrap(it)) {
			if (onlyOutputPass && rec.isFiltered()) {
				continue;
			}
			
			List<String> outs = new ArrayList<String>();

	        for (String k: extras.keySet()) {
	            outs.add(extras.get(k));
	        }
			
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
