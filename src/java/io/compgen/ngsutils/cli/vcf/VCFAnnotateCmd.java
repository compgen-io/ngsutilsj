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
import io.compgen.ngsutils.support.FileUtils;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFWriter;
import io.compgen.ngsutils.vcf.annotate.BEDAnnotation;
import io.compgen.ngsutils.vcf.annotate.ConstantTag;
import io.compgen.ngsutils.vcf.annotate.CopyNumberLogRatio;
import io.compgen.ngsutils.vcf.annotate.FisherStrandBias;
import io.compgen.ngsutils.vcf.annotate.FlankingBases;
import io.compgen.ngsutils.vcf.annotate.GTFGene;
import io.compgen.ngsutils.vcf.annotate.Indel;
import io.compgen.ngsutils.vcf.annotate.InfoInFile;
import io.compgen.ngsutils.vcf.annotate.MinorStrandPct;
import io.compgen.ngsutils.vcf.annotate.NullAnnotator;
import io.compgen.ngsutils.vcf.annotate.TabixAnnotation;
import io.compgen.ngsutils.vcf.annotate.TransitionTransversion;
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
	private String endPos = null;
	
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
    
    @Option(desc="Use an INFO field for the end of the variant (ex: CNA). Only useful for BED annotations.", name="end-pos")
    public void setEndPos(String key) throws CommandArgumentException {
        this.endPos = key;
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
    
    @Option(desc="Add a TS, TV annotations (TS: A<->G, C<->T)", name="tstv")
    public void setTsTv() throws CommandArgumentException {
    	chain.add(new TransitionTransversion());
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
    @Option(desc="Add flanking bases/normalized mutation (ex: A[C>A]A) from reference FASTA file (FAI indexed) (INFO:CG_FLANKING). By default 1 base on either side is used.", name="flanking", helpValue="ref.fa{:num_of_bases}")
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
                chain.add(new BEDAnnotation(spl[0],FileUtils.expandUserPath(spl[1]), false));
            } catch (IOException e) {
                throw new CommandArgumentException(e);
            }
        } else {
            throw new CommandArgumentException("Unable to parse argument for --bed: "+bed);
        }       
    }
    @Option(desc="Add annotations from a BED4 file (as a format field \"KEY\") (add ',n' to NAME to make value a number)", name="format-bed", helpValue="KEY:ALLELE:FILENAME", allowMultiple=true)
    public void setFormatBED(String bed) throws CommandArgumentException {
        String[] spl = bed.split(":");
        if (spl.length == 3) {
            try {
                chain.add(new BEDAnnotation(spl[0],FileUtils.expandUserPath(spl[2]), false, spl[1]));
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
                
                String colName = null;
                String altName = null;
                String refName = null;
                
                int col = -1;
                int refCol = -1;
                int altCol = -1;
                
                boolean isNumber = false;
                boolean max = false;
                boolean collapse = false;
                boolean noHeader = false;
                
                for (String t:spl2) {
                    if (fname == null) {
                        fname = FileUtils.expandUserPath(t);
                    } else if (t.equals("n")) {
                        isNumber = true;
                    } else if (t.equals("max")) {
                        max = true;
                    } else if (t.equals("collapse")) {
                        collapse = true;
                    } else if (t.equals("noheader")) {
                        noHeader = true;
                    } else if (t.startsWith("alt=")) {
                    	try {
                            altCol = Integer.parseInt(t.substring(4))-1;
	                    } catch (NumberFormatException e) {
	                    	altName = t.substring(4);
	                    }
                    } else if (t.startsWith("ref=")) {
                    	try {
                            refCol = Integer.parseInt(t.substring(4))-1;
	                    } catch (NumberFormatException e) {
	                    	refName = t.substring(4);
	                    }
                    } else if (col == -1) {
                    	try {
	                        col = Integer.parseInt(t)-1;
	                    } catch (NumberFormatException e) {
	                    	colName = t;
	                    }
                    }
                }

                if (refName != null && altName == null) {
                    throw new CommandArgumentException("If you set ref=, you must also set alt=): "+tab);
                }


                if (max && collapse) {
                    throw new CommandArgumentException("max and collapse can't be set at the same time: "+tab);
                }

                TabixAnnotation ann = new TabixAnnotation(spl[0], fname);
                if (colName != null) {
            		ann.setCol(colName);
                } else if (col > -1 ) {
            		ann.setCol(col);
//            	} else {
//                    throw new CommandArgumentException("Missing column number for tab annotation (required for format): "+tab);
                }

                if (altName != null) {
                	ann.setAltCol(altName);
                } else if (altCol > -1) {
                	ann.setAltCol(altCol);
                }
                
                if (refName != null) {
                	ann.setRefCol(refName);
                } else if (refCol > -1) {
                	ann.setRefCol(refCol);
                }
                
                if (isNumber) {
                	ann.setNumber();
                }
                
                if (max) {
                	if (!isNumber) {
                        throw new CommandArgumentException("max also requires ,n to be set: "+tab);
                	}
                	ann.setMax();
                }
                
                if (collapse) {
                	ann.setCollapse();
                }
                
                
                if (noHeader) {
                	ann.setNoHeader();
                }
                
                chain.add(ann);
//                
//                if (colName != null && altName != null) {
//                	chain.add(new TabixAnnotation(spl[0], fname, colName, isNumber, altName, collapse));
//                } else if (colName != null) {
//                	chain.add(new TabixAnnotation(spl[0], fname, colName, isNumber, altCol, collapse));
//                } else if (altName != null) {
//                	chain.add(new TabixAnnotation(spl[0], fname, col, isNumber, altName, collapse));
//                } else {
//                	chain.add(new TabixAnnotation(spl[0], fname, col, isNumber, altCol, collapse));
//                }
                
            } catch (IOException  e) {
                throw new CommandArgumentException(e);
            }
        } else {
            throw new CommandArgumentException("Unable to parse argument for --tab: "+tab);
        }       
    }
    
    
    @Option(desc="Add annotations from a Tabix file (as a format field; add ',n' for a number; set alt=col# to specify an alternative allele column -- will use exact matching; add ,collapse to collapse unique values to one)", name="format-tab", helpValue="NAME:SAMPLE:FILENAME,col{,n,alt=X,collapse,noheader}", allowMultiple=true)
    public void setFormatTabix(String tab) throws CommandArgumentException {
        String[] spl = tab.split(":");
        if (spl.length == 3) {
            String sampleName = spl[1];
            String[] spl2 = spl[2].split(",");
            
            try {
                String fname = null;
                
                String colName = null;
                String altName = null;
                String refName = null;
                
                int col = -1;
                int altCol = -1;
                int refCol = -1;

                boolean max = false;
                boolean collapse = false;
                boolean isNumber = false;
                boolean noHeader = false;
                
                for (String t:spl2) {
                    if (fname == null) {
                        fname = FileUtils.expandUserPath(t);
                    } else if (t.equals("n")) {
                        isNumber = true;
                    } else if (t.equals("max")) {
                        max = true;
                    } else if (t.equals("collapse")) {
                        collapse = true;
                    } else if (t.equals("noheader")) {
                    	noHeader = true;
                    } else if (t.startsWith("alt=")) {
                    	try {
                            altCol = Integer.parseInt(t.substring(4))-1;
	                    } catch (NumberFormatException e) {
	                    	altName = t.substring(4);
	                    }
                    } else if (t.startsWith("ref=")) {
                    	try {
                            refCol = Integer.parseInt(t.substring(4))-1;
	                    } catch (NumberFormatException e) {
	                    	refName = t.substring(4);
	                    }
                    } else if (col == -1) {
                    	try {
	                        col = Integer.parseInt(t)-1;
	                    } catch (NumberFormatException e) {
	                    	colName = t;
	                    }
                    }
                }
                if (refName != null && altName == null) {
                    throw new CommandArgumentException("If you set ref=, you must also set alt=): "+tab);
                }
                if (max && collapse) {
                    throw new CommandArgumentException("max and collapse can't be set at the same time: "+tab);
                }

                TabixAnnotation ann = new TabixAnnotation(spl[0], fname, sampleName);
                if (colName != null) {
            		ann.setCol(colName);
                } else if (col > -1 ) {
            		ann.setCol(col);
            	} else {
                    throw new CommandArgumentException("Missing column number for tab annotation (required for format): "+tab);
                }

                if (altName != null) {
                	ann.setAltCol(altName);
                } else if (altCol > -1) {
                	ann.setAltCol(altCol);
                }
                
                if (refName != null) {
                	ann.setRefCol(refName);
                } else if (refCol > -1) {
                	ann.setRefCol(refCol);
                }
                
                if (isNumber) {
                	ann.setNumber();
                }

                if (collapse) {
                	ann.setCollapse();
                }

                if (max) {
                	if (!isNumber) {
                        throw new CommandArgumentException("max also requires ,n to be set: "+tab);
                	}
                	ann.setMax();
                }
                
                
                if (noHeader) {
                	ann.setNoHeader();
                }
                
                chain.add(ann);
                
//                if (colName != null) {
//                	if (altName != null) {
//                		chain.add(new TabixAnnotation(spl[0], fname, colName, isNumber, altName, collapse, sampleName));
//                	} else {
//                		chain.add(new TabixAnnotation(spl[0], fname, colName, isNumber, altCol, collapse, sampleName));
//                	}
//                } else if (col>=0) {
//                	if (altName != null) {
//                		chain.add(new TabixAnnotation(spl[0], fname, col, isNumber, altName, collapse, sampleName));
//                	} else {
//                		chain.add(new TabixAnnotation(spl[0], fname, col, isNumber, altCol, collapse, sampleName));
//                	}
//                } else {
//   	             throw new CommandArgumentException("Missing column number for tab annotation (required for format): "+tab);
//                }
                
            } catch (IOException  e) {
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
				chain.add(new BEDAnnotation(spl[0], FileUtils.expandUserPath(spl[1]), true));
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
    	boolean unique = false;
    	if (spl.length == 4) {
            passing = spl[3].contains("@");
            exact = spl[3].contains("!");
            unique = spl[3].contains("$");
    	}
        try {
            chain.add(new VCFAnnotation(spl[0], FileUtils.expandUserPath(spl[2]), spl[1], exact, passing, unique));
        } catch (IOException e) {
            throw new CommandArgumentException("Unable to parse argument for --vcf: "+vcf+"\n"+e.getMessage());
        }
    }    
    
    @Option(desc="Flag variants within a VCF file (INFO, CSI indexed, add '!' for exact matches, add '@' for only using records passing filters)", name="vcf-flag", helpValue="NAME:FILENAME{:!@}", allowMultiple=true)
    public void setVCFFlag(String vcf) throws CommandArgumentException {
        String[] spl = vcf.split(":");
        boolean exact = false;
        boolean passing = false;
    	boolean unique = false;
        if (spl.length == 3) {
            passing = spl[2].contains("@");
            exact = spl[2].contains("!");
            unique = spl[2].contains("$");
        }
        try {
            chain.add(new VCFAnnotation(spl[0], FileUtils.expandUserPath(spl[1]), null, exact, passing, unique));
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
    
    @Option(desc="Add flag if an existing INFO value present is in a file (add csv if the INFO field is potentially comma-delimited, add tabcol= for a column in the file to add, tab-delimtied, 1-based)", name="in-file", helpValue="FLAGNAME:INFOKEY:FILENAME{:csv:tabcol=n}", allowMultiple=true)
    public void setInfoInFile(String val) throws CommandArgumentException {
    	String[] spl = val.split(":");

    	if (spl.length >= 3) {
        	String delimiter = null;
        	int col = -1;
        	
        	String filename = FileUtils.expandUserPath(spl[2]);
        	String infokey = spl[1];
        	String flagname = spl[0];

        	for (int i=3; i<spl.length; i++) {
        		if (spl[i].equals("csv")) {
        			delimiter = ",";
        		} else if (spl[i].equals(",")) {
        			delimiter = ",";
        		} else if (spl[i].startsWith("tabcol=")) {
        			col = Integer.parseInt(spl[i].substring(7));
        		}
        	}
        	
        	try {
				chain.add(new InfoInFile(filename, infokey, flagname, delimiter, col));
			} catch (IOException e) {
	    		throw new CommandArgumentException(e);
			}

    	} else {
    		throw new CommandArgumentException("Unable to parse argument for --in-file: "+val);
    	}
    }
    
    
//    @Option(desc="Add peptide annotation for SNVs", name="gtf", helpValue="filename.gtf")
//    public void setGTFPeptide(String filename) throws CommandArgumentException {
//		try {
//			chain.add(new GTFGene(filename));
//		} catch (IOException e) {
//			throw new CommandArgumentException(e);
//		}    	
//    }

    
    @Option(desc="Add gene annotations (INFO: CG_GENE, CG_GENE_STRAND, CG_GENE_REGION)", name="gtf", helpValue="filename.gtf")
    public void setGTF(String filename) throws CommandArgumentException {
		try {
			chain.add(new GTFGene(filename));
		} catch (IOException e) {
			throw new CommandArgumentException(e);
		}    	
    }


    @Option(desc="Add a constant INFO annotation to each record (KEY:VALUE or FLAG)", name="tag", helpValue="KEY{:VALUE}")
    public void setTag(String arg) throws CommandArgumentException {
    	if (arg.contains(":")) {
    		String[] spl = arg.split(":");
			chain.add(new ConstantTag(spl[0], spl[1]));

    	} else {
			chain.add(new ConstantTag(arg));
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

        if (endPos != null) {
            for (int i=0; i< chain.size(); i++) {
                chain.get(i).setEndPos(endPos);
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
		
		VCFWriter writer = new VCFWriter(out, header);
//		VCFWriter writer;
//		if (out.equals("-")) {
//			writer = new VCFWriter(System.out, header);
//		} else {
//			writer = new VCFWriter(out, header);
//		}

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
