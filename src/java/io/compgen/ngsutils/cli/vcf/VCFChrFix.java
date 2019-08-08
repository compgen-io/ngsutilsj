package io.compgen.ngsutils.cli.vcf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.SetBuilder;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.VCFContigDef;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFWriter;


@Command(name="vcf-chrfix", desc="Changes the reference (chrom) format (Ensembl/UCSC)", category="vcf")
public class VCFChrFix extends AbstractOutputCommand {
	private String filename = "-";
	private boolean ucsc = false;
    private boolean ensembl = false;
    private boolean primary = false;
    private Set<String> keepContigs = null;

    @Option(desc="UCSC references (chr1, chr2, etc...)", name="ucsc")
    public void setUCSC(boolean val) {
        this.ucsc = val;
    }
    
    @Option(desc="Ensembl references (1, 2, etc...)", name="ensembl")
    public void setEnsembl(boolean val) {
        this.ensembl = val;
    }
    
    @Option(desc="Only keep primary human contigs (1,2,3,...,22,X,Y,M)", name="primary-human")
    public void setPrimary(boolean val) {
        this.primary = val;
    }
    
    @Option(desc="Only keep these contigs/chrom (comma separated list)", name="contigs")
    public void setContigs(String val) {
        this.keepContigs = new HashSet<String>();
        for (String v: val.split(",")) {
            this.keepContigs.add(v.trim());
        }
    }
    
    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

	@Exec
	public void exec() throws Exception {
		if (!ucsc && !ensembl && !primary && keepContigs == null) {
    		throw new CommandArgumentException("No changers specified");
		}

		if (ucsc && ensembl) {
    		throw new CommandArgumentException("You must set either --ucsc or --ensembl, not both.");
		}

		VCFReader reader;
		if (filename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(filename);
		}
		
		VCFHeader header = reader.getHeader();

		if (ucsc) {
    		for (String contig: new ArrayList<String>(header.getContigNames())) {
                if (!contig.startsWith("chr")) {
                    String newid;
                    if (contig.equals("MT")) {
                        newid = "chrM";
                    } else {
                        newid = "chr"+contig;
                    }           
                    
                    long len = header.getContigLength(contig);
                    header.removeContig(contig);
                    header.addContig(VCFContigDef.build(newid, len));
                }
    		}
		}

		if (ensembl) {
            for (String contig: new ArrayList<String>(header.getContigNames())) {
                if (contig.startsWith("chr")) {
                    String newid;
                    if (contig.equals("chrM")) {
                        newid = "MT";
                    } else {
                        newid = contig.substring(3);
                    }           
                    
                    long len = header.getContigLength(contig);
                    header.removeContig(contig);
                    header.addContig(VCFContigDef.build(newid, len));
                }
    		}
   		}
		
		if (primary || keepContigs!= null) {
		      for (String contig: new ArrayList<String>(header.getContigNames())) {
                if (!keepChrom(contig)) {
                    header.removeContig(contig);
                    continue;
                }
		      }
		}
		
		
		header.addLine("##ngsutilsj_vcf_chrfixCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_chrfixVersion="+NGSUtils.getVersion())) {
		    header.addLine("##ngsutilsj_vcf_chrfixVersion="+NGSUtils.getVersion());
		}

	
		VCFWriter writer;
		if (out.equals("-")) {
			writer = new VCFWriter(System.out, header);
		} else {
			writer = new VCFWriter(out, header);
		}


		for (VCFRecord rec: IterUtils.wrap(reader.iterator())) {
			if (ucsc && !rec.getChrom().startsWith("chr")) {
				if (rec.getChrom().equals("MT")) {
	                rec.setChrom("chrM");
				} else {
	                rec.setChrom("chr"+rec.getChrom());
				}			
				
			} else if (ensembl && rec.getChrom().startsWith("chr")) {
                if (rec.getChrom().equals("chrM")) {
                    rec.setChrom("MT");
                } else {
                    rec.setChrom(rec.getChrom().substring(3));
                }
			}

			if (keepChrom(rec.getChrom())) {
			    writer.write(rec);
			}
		}		
		reader.close();
		writer.close();
	}

	Set<String> humanContigs = new SetBuilder<String>()
	        .add("1")
	        .add("2")
	        .add("3")
	        .add("4")
	        .add("5")
	        .add("6")
	        .add("7")
	        .add("8")
	        .add("9")
	        .add("10")
            .add("11")
            .add("12")
            .add("13")
            .add("14")
            .add("15")
            .add("16")
            .add("17")
            .add("18")
            .add("19")
            .add("20")
            .add("21")
            .add("22")
            .add("X")
            .add("Y")
            .add("M")
            .add("MT")
            .build();
        
	
    private boolean keepChrom(String chrom) {
        if (!primary && keepContigs == null) {
            return true;
        }
        
        if (keepContigs != null) {
            return keepContigs.contains(chrom);
        }
        
        String sub;
        if (chrom.startsWith("chr")) {
            sub = chrom.substring(3);
        } else {
            sub = chrom;
        }
        
        return humanContigs.contains(sub);
    }

}
