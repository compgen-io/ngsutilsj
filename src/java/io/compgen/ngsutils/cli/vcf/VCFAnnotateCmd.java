package io.compgen.ngsutils.cli.vcf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFWriter;
import io.compgen.ngsutils.vcf.annotate.BEDAnnotation;
import io.compgen.ngsutils.vcf.annotate.CopyNumberLogRatio;
import io.compgen.ngsutils.vcf.annotate.FisherStrandBias;
import io.compgen.ngsutils.vcf.annotate.FlankingBases;
import io.compgen.ngsutils.vcf.annotate.GTFGene;
import io.compgen.ngsutils.vcf.annotate.Indel;
import io.compgen.ngsutils.vcf.annotate.InfoInFile;
import io.compgen.ngsutils.vcf.annotate.MinorStrandPct;
import io.compgen.ngsutils.vcf.annotate.NullAnnotator;
import io.compgen.ngsutils.vcf.annotate.TabixAnnotation;
import io.compgen.ngsutils.vcf.annotate.VCFAnnotation;
import io.compgen.ngsutils.vcf.annotate.VCFAnnotator;
import io.compgen.ngsutils.vcf.annotate.VariantAlleleFrequency;
import io.compgen.ngsutils.vcf.annotate.VariantDistance;


@Command(name="vcf-annotate", desc="Annotate a VCF file", category="vcf")
public class VCFAnnotateCmd extends AbstractOutputCommand {
	private String filename = "-";
	private boolean onlyPassing = false;
	private String altChrom = null;
	private String altPos = null;
	
	List<VCFAnnotator> chain = new ArrayList<VCFAnnotator>();
	
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyPassing(boolean onlyPassing) {
    	this.onlyPassing = onlyPassing;
    }
    
    @Option(desc="Use an alternate INFO field for the chromosome (ex: SV). If missing, skip annotation.", name="alt-chrom")
    public void setAltChrom(String key) throws CommandArgumentException {
        this.altChrom = key;
    }
    
    @Option(desc="Use an alternate INFO field for the position (ex: SV). If missing, skip annotation.", name="alt-pos")
    public void setAltPos(String key) throws CommandArgumentException {
        this.altPos = key;
    }
    
    @Option(desc="Add distance to nearest variant (INFO:CG_VARDIST)", name="vardist")
    public void setVarDist() throws CommandArgumentException {
        chain.add(new VariantDistance());
    }
    
    @Option(desc="Add variant allele frequencies (FORMAT:CG_VAF, requires SAC)", name="vaf")
    public void setVAF() throws CommandArgumentException {
        chain.add(new VariantAlleleFrequency());
    }
    
    @Option(desc="Add INSERT and DELETE flags (INFO:CG_INSERT, CG_DELETE, etc...)", name="indel")
    public void setIndel() throws CommandArgumentException {
    	chain.add(new Indel());
    }
    
    @Option(desc="Add Fisher strand bias by sample (FORMAT:CG_FSB, requires SAC)", name="fisher-sb")
    public void setFisherStrandBias() throws CommandArgumentException {
    	chain.add(new FisherStrandBias());
    }
    
    @Option(desc="Add minor strand pct (FORMAT:CG_SBPCT, requires SAC)", name="minor-strand")
    public void setMinorStrandPct() throws CommandArgumentException {
    	chain.add(new MinorStrandPct());
    }

    @Option(desc="Add copy-number estimate (log2 ratio, somatic/germline -- at variant position) (INFO:CG_CNLR, requires AD)", name="copy-logratio", helpValue="SOMATIC:GERMLINE{:somatic-total-count:germline-total-count} (sample-ids)")
    public void setCopyNumberLogRatio(String samples) throws CommandArgumentException {
    	String[] spl = samples.split(":");
    	if (spl.length == 2) {
    		chain.add(new CopyNumberLogRatio(spl[1], spl[0]));
    	} else if (spl.length == 4) {
    		chain.add(new CopyNumberLogRatio(spl[1], spl[0], Integer.parseInt(spl[3]), Integer.parseInt(spl[2])));
    	} else {
    		throw new CommandArgumentException("Unable to parse argument for --copy-logratio: "+samples);
    	}
    	
    }
    @Option(desc="Add flanking bases (ex: ACA) from reference FASTA file (FAI indexed) (INFO:CG_FLANKING). By default 1 base on either side is used.", name="flanking", helpValue="ref.fa{:num_of_bases}")
    public void setFlanking(String arg) throws CommandArgumentException {
    	try{
    	    String[] spl = arg.split(":");
    	    if (spl.length == 1) {
                chain.add(new FlankingBases(spl[0]));
    	    } else {
                chain.add(new FlankingBases(spl[0], Integer.parseInt(spl[1])));
    	    }
		} catch (IOException e) {
    		throw new CommandArgumentException(e);
		}
    }
    

    @Option(desc="Add annotations from a BED4 file (using the name column, INFO) (add ',n' to NAME to make value a number)", name="bed", helpValue="NAME:FILENAME", allowMultiple=true)
    public void setBED(String bed) throws CommandArgumentException {
        String[] spl = bed.split(":");
        if (spl.length == 2) {
            try {
                chain.add(new BEDAnnotation(spl[0], spl[1], false));
            } catch (IOException e) {
                throw new CommandArgumentException(e);
            }
        } else {
            throw new CommandArgumentException("Unable to parse argument for --bed: "+bed);
        }       
    }
    
    @Option(desc="Add annotations from a Tabix file (INFO: If col is left out, this is treaded as a VCF flag; add ',n' for a number; set alt=col# to specify an alternative allele column -- will use exact matching; add ,collapse to collapse unique values to one)", name="tab", helpValue="NAME:FILENAME{,col,n,alt=X,collapse}", allowMultiple=true)
    public void setTabix(String tab) throws CommandArgumentException {
        String[] spl = tab.split(":");
        if (spl.length == 2) {
            String[] spl2 = spl[1].split(",");
            
            try {
                String fname = null;
                int col = -1;
                boolean isNumber = false;
                int altCol = -1;
                boolean collapse = false;
                
                for (String t:spl2) {
                    if (fname == null) {
                        fname = t;
                    } else if (t.equals("n")) {
                        isNumber = true;
                    } else if (t.equals("collapse")) {
                        collapse = true;
                    } else if (t.startsWith("alt=")) {
                        altCol = Integer.parseInt(t.substring(4))-1;
                    } else if (col == -1) {
                        col = Integer.parseInt(t)-1;
                    }
                }

                chain.add(new TabixAnnotation(spl[0], fname, col, isNumber, altCol, collapse));
                
            } catch (NumberFormatException | IOException  e) {
                throw new CommandArgumentException(e);
            }
        } else {
            throw new CommandArgumentException("Unable to parse argument for --tab: "+tab);
        }       
    }
    
    @Option(desc="Flag variants within a BED3 region (INFO)", name="bed-flag", helpValue="NAME:FILENAME", allowMultiple=true)
    public void setBEDFlag(String bed) throws CommandArgumentException {
    	String[] spl = bed.split(":");
    	if (spl.length == 2) {
    		try {
				chain.add(new BEDAnnotation(spl[0], spl[1], true));
			} catch (IOException e) {
	    		throw new CommandArgumentException(e);
			}
    	} else {
    		throw new CommandArgumentException("Unable to parse argument for --bed-flag: "+bed);
    	}
    }


    @Option(desc="Add INFO annotation from a VCF file (CSI indexed, add '!' for exact matches, add '@' for only using records passing filters)", name="vcf", helpValue="NAME:FIELD:FILENAME{:!@}", allowMultiple=true)
    public void setVCF(String vcf) throws CommandArgumentException {
    	String[] spl = vcf.split(":");
    	boolean exact = false;
    	boolean passing = false;
    	if (spl.length == 4) {
            passing = spl[3].contains("@");
            exact = spl[3].contains("!");
    	}
        try {
            chain.add(new VCFAnnotation(spl[0], spl[2], spl[1], exact, passing));
        } catch (IOException e) {
            throw new CommandArgumentException("Unable to parse argument for --vcf: "+vcf+"\n"+e.getMessage());
        }
    }    
    
    @Option(desc="Flag variants within a VCF file (INFO, CSI indexed, add '!' for exact matches, add '@' for only using records passing filters)", name="vcf-flag", helpValue="NAME:FILENAME{:!@}", allowMultiple=true)
    public void setVCFFlag(String vcf) throws CommandArgumentException {
        String[] spl = vcf.split(":");
        boolean exact = false;
        boolean passing = false;
        if (spl.length == 3) {
            passing = spl[2].contains("@");
            exact = spl[2].contains("!");
        }
        try {
            chain.add(new VCFAnnotation(spl[0], spl[1], null, exact, passing));
        } catch (IOException e) {
            throw new CommandArgumentException("Unable to parse argument for --vcf-flag: "+vcf+"\n"+e.getMessage());
        }
    }
    
    @Option(desc="Copy the VCF ID field from this source VCF file (exact matches only)", name="vcf-id", helpValue="FILENAME", allowMultiple=false)
    public void setVCFID(String vcf) throws CommandArgumentException {
        try {
            chain.add(new VCFAnnotation("@ID", vcf, null));
        } catch (IOException e) {
            throw new CommandArgumentException(e);
        }
    }
    
    @Option(desc="Add flag if an existing INFO value present is in a file", name="in-file", helpValue="FLAGNAME:INFOKEY:FILENAME", allowMultiple=true)
    public void setInfoInFile(String val) throws CommandArgumentException {
    	String[] spl = val.split(":");
    	if (spl.length == 3) {
    		try {
				chain.add(new InfoInFile(spl[2], spl[1], spl[0]));
			} catch (IOException e) {
	    		throw new CommandArgumentException(e);
			}
    	} else {
    		throw new CommandArgumentException("Unable to parse argument for --in-file: "+val);
    	}
    }
    
    @Option(desc="Add gene annotations (INFO: CG_GENE, CG_GENE_STRAND, CG_GENE_REGION)", name="gtf", helpValue="filename.gtf")
    public void setGTF(String filename) throws CommandArgumentException {
		try {
			chain.add(new GTFGene(filename));
		} catch (IOException e) {
			throw new CommandArgumentException(e);
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
		
		NullAnnotator nullAnn = new NullAnnotator(reader, onlyPassing, true);

        if (altChrom != null) {
            for (int i=0; i< chain.size(); i++) {
                chain.get(i).setAltChrom(altChrom);
            }
        }
        
        if (altPos != null) {
            for (int i=0; i< chain.size(); i++) {
                chain.get(i).setAltPos(altPos);
            }
        }

		VCFHeader header = reader.getHeader();
		for (int i=0; i< chain.size(); i++) {
			chain.get(i).setHeader(header);
			if (i == 0) {
				chain.get(i).setParent(nullAnn);
			} else {
				chain.get(i).setParent(chain.get(i-1));
			}
		}
		
		header.addLine("##ngsutilsj_vcf_annotateCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_annotateVersion="+NGSUtils.getVersion())) {
			header.addLine("##ngsutilsj_vcf_annotateVersion="+NGSUtils.getVersion());
		}
		
		VCFWriter writer;
		if (out.equals("-")) {
			writer = new VCFWriter(System.out, header);
		} else {
			writer = new VCFWriter(out, header);
		}

		VCFRecord rec = chain.get(chain.size()-1).next();
		while (rec != null) {
			writer.write(rec);
			rec = chain.get(chain.size()-1).next();
		}
		
		for (int i=0; i< chain.size(); i++) {
			chain.get(i).close();
		}
		reader.close();
		writer.close();
	}

}
