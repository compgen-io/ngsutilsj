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
import io.compgen.ngsutils.vcf.annotate.VariantDistance;


@Command(name="vcf-annotate", desc="Annotate a VCF file", category="vcf")
public class VCFAnnotateCmd extends AbstractOutputCommand {
	private String filename = "-";
	private boolean onlyPassing = false;
	
	List<VCFAnnotator> chain = new ArrayList<VCFAnnotator>();
	
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyPassing(boolean onlyPassing) {
    	this.onlyPassing = onlyPassing;
    }
    
    @Option(desc="Add distance to nearest variant (CG_VARDIST)", name="vardist")
    public void setVarDist() throws CommandArgumentException {
    	chain.add(new VariantDistance());
    }
    
    @Option(desc="Add INSERT and DELETION flags (CG_INSERT, CG_DELETION)", name="indel")
    public void setIndel() throws CommandArgumentException {
    	chain.add(new Indel());
    }
    
    @Option(desc="Add Fisher strand bias by sample (CG_FSB, requires SAC)", name="fisher-sb")
    public void setFisherStrandBias() throws CommandArgumentException {
    	chain.add(new FisherStrandBias());
    }
    
    @Option(desc="Add minor strand pct (CG_SBPCT, requires SAC)", name="minor-strand")
    public void setMinorStrandPct() throws CommandArgumentException {
    	chain.add(new MinorStrandPct());
    }

    @Option(desc="Add copy-number (log2 ratio, somatic/germline) (CG_CNLR, requires AD)", name="copy-logratio", helpValue="SOMATIC:GERMLINE{:somatic-total-count:germline-total-count} (sample-ids)")
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
    @Option(desc="Add flanking bases (ex: A<C>A) from reference FASTA file (FAI indexed)", name="flanking", helpValue="ref.fa")
    public void setFlanking(String fasta) throws CommandArgumentException {
    	try{
			chain.add(new FlankingBases(fasta));
		} catch (IOException e) {
    		throw new CommandArgumentException(e);
		}
    }
    

    @Option(desc="Add annotations from a BED4 file (name column) (add ',n' to NAME to make value a number)", name="bed", helpValue="NAME:FILENAME", allowMultiple=true)
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
    
    @Option(desc="Add annotations from a Tabix file (If col is left out, this is treaded as a VCF flag; add ',n' for a number; set alt=col# to specify an alternative allele column -- will use exact matching)", name="tab", helpValue="NAME:FILENAME{,col,n,alt=X}", allowMultiple=true)
    public void setTabix(String bed) throws CommandArgumentException {
        String[] spl = bed.split(":");
        if (spl.length == 2) {
            String[] spl2 = spl[1].split(",");
            try {
                String fname = null;
                int col = -1;
                boolean isNumber = false;
                int altCol = -1;
                
                for (String t:spl2) {
                    if (fname == null) {
                        fname = t;
                    } else if (col == -1) {
                        col = Integer.parseInt(t)-1;
                    } else if (t.equals("n")) {
                        isNumber = true;
                    } else if (t.startsWith("alt=")) {
                        altCol = Integer.parseInt(t.substring(4))-1;
                    }
                }
                if (col > -1) {
                    chain.add(new TabixAnnotation(spl[0], fname, col, isNumber, altCol));
                } else {
                    chain.add(new TabixAnnotation(spl[0], fname));
                }
            } catch (NumberFormatException | IOException  e) {
                throw new CommandArgumentException(e);
            }
        } else {
            throw new CommandArgumentException("Unable to parse argument for --bed: "+bed);
        }       
    }
    
    @Option(desc="Flag variants within a BED3 region", name="bed-flag", helpValue="NAME:FILENAME", allowMultiple=true)
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


    @Option(desc="Add INFO annotation from a VCF file (CSI indexed, add '!' for exact matches)", name="vcf", helpValue="NAME:FIELD:FILENAME{:!}", allowMultiple=true)
    public void setVCF(String vcf) throws CommandArgumentException {
    	String[] spl = vcf.split(":");
    	if (spl.length == 3) {
    		try {
				chain.add(new VCFAnnotation(spl[0], spl[2], spl[1]));
			} catch (IOException e) {
	    		throw new CommandArgumentException(e);
			}
    	} else if (spl.length == 4) {
    		try {
				chain.add(new VCFAnnotation(spl[0], spl[2], spl[1], spl[3].equals("!")));
			} catch (IOException e) {
	    		throw new CommandArgumentException(e);
			}
    	} else {
    		throw new CommandArgumentException("Unable to parse argument for --vcf: "+vcf);
    	}    	
    }    
    
    @Option(desc="Flag variants within a VCF file (CSI indexed, add '!' for exact matches)", name="vcf-flag", helpValue="NAME:FILENAME{:!}", allowMultiple=true)
    public void setVCFFlag(String vcf) throws CommandArgumentException {
    	String[] spl = vcf.split(":");
    	if (spl.length == 2) {
    		try {
				chain.add(new VCFAnnotation(spl[0], spl[1], null));
			} catch (IOException e) {
	    		throw new CommandArgumentException(e);
			}
    	} else if (spl.length == 3) {
        		try {
    				chain.add(new VCFAnnotation(spl[0], spl[1], null, spl[2].equals("!")));
    			} catch (IOException e) {
    	    		throw new CommandArgumentException(e);
    			}
        } else {
    		throw new CommandArgumentException("Unable to parse argument for --vcf-flag: "+vcf);
    	}
    }
    
    @Option(desc="Flag if an existing INFO value present is in a file", name="in-file", helpValue="FLAG:INFO:FILENAME", allowMultiple=true)
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
    
    @Option(desc="Add gene annotations", name="gtf", helpValue="filename.gtf")
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
		
		NullAnnotator nullAnn = new NullAnnotator(reader, onlyPassing);
		
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
