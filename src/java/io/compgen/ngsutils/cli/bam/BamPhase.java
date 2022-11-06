package io.compgen.ngsutils.cli.bam;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMProgramRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.bam.support.BamHeaderUtils;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.fasta.IndexedFastaFile;
import io.compgen.ngsutils.support.Warning;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;

@Command(name="bam-phase", desc="Given a BAM and VCF file, split the BAM file into smaller phased files.", category="bam", experimental=true, doc="For each heterozygous VCF variant, find the reads that support the variant. Then keep adding variants to phased sequences. ")
public class BamPhase extends AbstractOutputCommand {
    
    private String vcfFile = null;
    private String inBamFname = null;
    private String outBamFname = null;
    private String fastaFname = null;

    private String tmpDir = null;
    private String varOutFname = null;

    private int minCount = 1;
    
    private boolean lenient = false;
    private boolean silent = false;
    private boolean unique = false;
    
    private int filterFlags = 0;
    private int requiredFlags = 0;

    private SamReader reader = null;
    
    
    public class PhaseVariant {
    	public final String ref;
    	public final int pos;
    	public final String refbase;
    	public final String alt;
    	public final String call;
    	
    	public PhaseVariant(String ref, int pos, String refbase, String alt, String base) {
    		this.ref = ref;
    		this.pos = pos;
    		this.refbase = refbase;
    		this.alt = alt;
    		this.call = base;
    	}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getEnclosingInstance().hashCode();
			result = prime * result + ((alt == null) ? 0 : alt.hashCode());
			result = prime * result + ((call == null) ? 0 : call.hashCode());
			result = prime * result + pos;
			result = prime * result + ((ref == null) ? 0 : ref.hashCode());
			result = prime * result + ((refbase == null) ? 0 : refbase.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			PhaseVariant other = (PhaseVariant) obj;
			if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
				return false;
			if (alt == null) {
				if (other.alt != null)
					return false;
			} else if (!alt.equals(other.alt))
				return false;
			if (call == null) {
				if (other.call != null)
					return false;
			} else if (!call.equals(other.call))
				return false;
			if (pos != other.pos)
				return false;
			if (ref == null) {
				if (other.ref != null)
					return false;
			} else if (!ref.equals(other.ref))
				return false;
			if (refbase == null) {
				if (other.refbase != null)
					return false;
			} else if (!refbase.equals(other.refbase))
				return false;
			return true;
		}

		private BamPhase getEnclosingInstance() {
			return BamPhase.this;
		}
    }

    
    public class PhaseSet {
    	private final int phaseSetId;
    	private final int phaseId;
    	private final SAMFileWriterFactory factory;
    	private final SAMFileHeader header;
    	private final String bamOutputFilename;
    	private final String variantOutFilename;

    	private Map<String, SAMRecord> reads = new HashMap<String, SAMRecord>();
    	private Map<PhaseVariant, Integer> variants = new HashMap<PhaseVariant, Integer>();
    	
    	public PhaseSet(int phaseSetId, int phaseId, String bamOutFname, SAMFileHeader header, SAMFileWriterFactory factory) {
    		this(phaseSetId, phaseId, bamOutFname, null, header, factory);
    	}
    	public PhaseSet(int phaseSetId, int phaseId, String bamOutFname, String varOutFname, SAMFileHeader header, SAMFileWriterFactory factory) {
    		this.phaseSetId = phaseSetId;
    		this.phaseId = phaseId;
    		this.factory = factory;
    		this.header = header;
    		this.bamOutputFilename = bamOutFname+"."+phaseSetId+"_"+phaseId+".bam";
    		if (varOutFname == null) {
    			this.variantOutFilename = null;
    		} else {
    			this.variantOutFilename = varOutFname+"."+phaseSetId+"_"+phaseId+".txt";
    		}
    	}
    	
    	public int getPhaseSetId() {
    		return this.phaseSetId;
    	}
    	
    	public int getPhaseId() {
    		return this.phaseId;
    	}
    	
    	public boolean containsRead(String readName) {
    		return reads.containsKey(readName);
    	}

    	public void addVariant(VCFRecord rec, String base, Collection<SAMRecord> reads) {

    		variants.put(new PhaseVariant(rec.getChrom(), rec.getPos(), rec.getRef(), rec.getAltOrig(), base), reads.size());
    		for (SAMRecord read:reads) {
    			this.reads.put(read.getReadName(), read);
    		}
    	}
    	
    	public Set<PhaseVariant> getVariants() {
    		return Collections.unmodifiableSet(variants.keySet());
    	}
    	
    	public void close() throws IOException {
    		header.setSortOrder(SortOrder.coordinate);
    		SAMFileWriter writer = factory.makeBAMWriter(header, false, new File(this.bamOutputFilename));
    		for (SAMRecord read: reads.values()) {
    			writer.addAlignment(read);
    		}
    		writer.close();
    		
    		if (this.variantOutFilename != null) {
    			TabWriter tab = new TabWriter(this.variantOutFilename);
    			for (PhaseVariant var: variants.keySet()) {
    				tab.write(phaseSetId);
    				tab.write(phaseId);
    				tab.write(var.ref);
    				tab.write(var.pos);
    				tab.write(var.refbase);
    				tab.write(var.alt);
    				tab.write(var.call);
    				tab.write(variants.get(var));
    			}
    		}
    	}
    }
    
    
//    
//    
//    @UnnamedArg(name = "VCF BAM OUTPUT_BAM")
//    public void setFilenames(String[] filenames) throws CommandArgumentException {
//    	if (filenames.length != 3) {
//    		throw new CommandArgumentException("You must specify a VCF file, an input BAM file and the template filename for output BAM files");
//    	}
//    	if (filenames[0].equals("-") || filenames[1].equals("-")) {
//    		throw new CommandArgumentException("Can't extract reads from stdin!");
//    	}
//
//        this.vcfFile = filenames[0];
//        this.inBamFname = filenames[1];
//        this.outBamFname = filenames[2];
//    }


    @Option(desc = "VCF file to define variants to phase", name = "vcf", required=true)
    public void setVCFFile(String vcfFile) {
        this.vcfFile = vcfFile;
    }

    @Option(desc = "BAM file to find reads", name = "bam", required=true)
    public void setInBamFile(String inBamFname) throws CommandArgumentException {
    	if (inBamFname.equals("-")) {
    		throw new CommandArgumentException("Input BAM file cannot be read from stdin");
    	}

        this.inBamFname = inBamFname;
    }

    @Option(desc = "Genome reference FASTA (indexed)", name = "fasta", required=true)
    public void setFastaFilename(String fastaFname) throws CommandArgumentException {
    	if (fastaFname.equals("-")) {
    		throw new CommandArgumentException("FASTA file cannot be read from stdin");
    	}
    	
    	if (!new File(fastaFname + ".fai").exists()) {
    		throw new CommandArgumentException("FASTA file must be indexed!");
    	}
    	
        this.fastaFname = fastaFname;
    }

    @Option(desc = "Output BAM file name template", name = "out-bam", required=true)
    public void setOutBamFname(String outBamFname) throws CommandArgumentException {
    	if (outBamFname.equals("-")) {
    		throw new CommandArgumentException("Output BAM files can't be written to stdout");
    	}
        this.outBamFname = outBamFname;
    }

    @Option(desc = "Minimum number of variant reads required for phasing", name = "min-count")
    public void setMinCount(int minCount) {
        this.minCount = minCount;
    }


    @Option(desc = "Write a list of the variants for each phase to these files (file output template)", name = "varlist")
    public void setVarOutFname(String varOutFname) {
        this.varOutFname = varOutFname;
    }

    @Option(desc = "Write temporary files here", name = "tmpdir", helpValue = "dir")
    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }

    @Option(desc="Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc="Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }
    
    
    @Option(desc = "Only keep properly paired reads", name = "proper-pairs")
    public void setProperPairs(boolean val) {
        if (val) {
            requiredFlags |= ReadUtils.PROPER_PAIR_FLAG;
        }
    }

    @Option(desc = "Only keep reads that have one unique mapping (NH==1|IH==1|MAPQ>0)", name = "unique-mapping")
    public void setUniqueMapping(boolean val) {
        unique = val;
        setMapped(true);
    }

    @Option(desc = "Only keep mapped reads (both reads if paired)", name = "mapped")
    public void setMapped(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.READ_UNMAPPED_FLAG | ReadUtils.MATE_UNMAPPED_FLAG;
        }
    }
    
    @Option(desc = "No secondary mappings", name = "no-secondary")
    public void setNoSecondary(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.NOT_PRIMARY_ALIGNMENT_FLAG;
        }
    }

    @Option(desc = "No supplementary mappings", name = "no-supplementary")
    public void setNoSupplementary(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.SUPPLEMENTARY_ALIGNMENT_FLAG;
        }
    }

    @Option(desc = "No PCR duplicates", name = "no-pcrdup")
    public void setNoPCRDuplicates(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.DUPLICATE_READ_FLAG;
        }
    }

    @Option(desc = "No QC failures", name = "no-qcfail")
    public void setNoQCFail(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.READ_FAILS_VENDOR_QUALITY_CHECK_FLAG;
        }
    }

    @Option(desc = "Filtering flags", name = "filter-flags", defaultValue = "0")
    public void setFilterFlags(int flag) {
        filterFlags |= flag;
    }

    @Option(desc = "Required flags", name = "required-flags", defaultValue = "0")
    public void setRequiredFlags(int flag) {
        requiredFlags |= flag;
    }

    @Exec
    public void exec() throws IOException, VCFParseException, CommandArgumentException, VCFAttributeException {
    	
    	if (vcfFile == null || inBamFname == null || outBamFname == null || fastaFname == null) { 
    		throw new CommandArgumentException("You must specify a VCF file, an input BAM file and the template filename for output BAM files");
    	}

    	IndexedFastaFile fasta = new IndexedFastaFile(fastaFname);
    	
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        reader = readerFactory.open(new File(inBamFname));

        
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();

        File outfile = null;
        int phaseSetNum = 0;

        outfile = new File(outBamFname);

        if (tmpDir != null) {
            factory.setTempDirectory(new File(tmpDir));
        } else if (outfile == null || outfile.getParent() == null) {
            factory.setTempDirectory(new File(".").getCanonicalFile());
        } else if (outfile != null) {
            factory.setTempDirectory(outfile.getParentFile());
        }
        
        final SAMFileHeader header = reader.getFileHeader().clone();
        final SAMProgramRecord pg = BamHeaderUtils.buildSAMProgramRecord("bam-phase", header);
        final List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(
                header.getProgramRecords());
        pgRecords.add(0, pg);
        header.setProgramRecords(pgRecords);

    	VCFReader vcfReader = new VCFReader(vcfFile);
    	Iterator<VCFRecord> it = vcfReader.iterator();
    	Warning warn = new Warning();

    	PhaseSet phaseOne = null;
    	PhaseSet phaseTwo = null;
    	
    	while (it.hasNext()) {
    		// note - these could also be indels, so pass the full VCF record, not just the positions
    		VCFRecord rec = it.next();
    		
    		if (rec.isFiltered()) {
    			continue;
    		}
    		
    		if (rec.getSampleAttributes().size() > 1) {
    			warn.once("Only heterozygous variants from the first sample will be used");
    		}
    		
    		String alleleA = rec.getRef();
    		String alleleB = rec.getAlt().get(0); // default to ref/alt1 if no GT field present (it should be present)
    		
    		if (rec.getSampleAttributes().get(0).contains("GT")) {
    			String gt = rec.getSampleAttributes().get(0).get("GT").asString(null);
    			String[] spl;
    			if (gt.indexOf("/") > -1) {
    				spl = gt.split("/");
    			} else if (gt.indexOf("|") > -1) { // this shouldn't happen as it is already phased...
    				spl = gt.split("|");
    			} else {
    				continue;
    			}
    			
    			if (spl[0].equals(spl[1])) {
    				// homozygous
    				continue;
    			}

    			int val1 = Integer.parseInt(spl[0]);
    			int val2 = Integer.parseInt(spl[1]);
    			
    			if (val1 == 0) {
    				alleleA = rec.getRef(); // 0
    			} else {
    				alleleA = rec.getAlt().get(val1-1); // 1, 2, 3...
    			}

    			if (val2 == 0) {
    				alleleB = rec.getRef(); // 0
    			} else {
    				alleleB = rec.getAlt().get(val2-1); // 1, 2, 3...
    			}
    			
    		}
        		
        	List<SAMRecord> reads = findReads(rec.getChrom(), rec.getPos()-1, rec.getPos());
        	List<SAMRecord> readsA = filterReads(reads, rec, alleleA, fasta);
        	List<SAMRecord> readsB = filterReads(reads, rec, alleleB, fasta);
        	
        	if (readsA.size() < minCount || readsB.size() < minCount) {
        		// TODO: add VCF writer to output a phased VCF file
        		//       Here: write an unphased variant out
        		warn.warn("Skipping variant: "+ rec +" (not enough reads found for one allele)");
        		continue;
        	}
        	
    		int matchesOneA = 0;
    		int matchesOneB = 0;
    		int matchesTwoA = 0;
    		int matchesTwoB = 0;

        	boolean matched = false;
        	
        	if (phaseOne != null && phaseTwo != null) {
	    		for (SAMRecord read: readsA) {
	    			if (phaseOne.containsRead(read.getReadName())) {
	    				matchesOneA++;
	    			} else if (phaseTwo.containsRead(read.getReadName())) {
	    				matchesTwoA++;
	    			}
	    		}
	        	for (SAMRecord read: readsB) {
	    			if (phaseOne.containsRead(read.getReadName())) {
	    				matchesOneB++;
	    			} else if (phaseTwo.containsRead(read.getReadName())) {
	    				matchesTwoB++;
	    			}
	    		}

	        	if (matchesOneA > matchesTwoA && matchesTwoB > matchesOneB) {
	        		// a 1, b 2
	        		phaseOne.addVariant(rec, alleleA, readsA);
	        		phaseTwo.addVariant(rec, alleleB, readsB);
	        		matched = true;
	        		
	        		// TODO: write phased variant to VCF (re-order GT field, add PS format) 
	        		
	        	} else if (matchesTwoA > matchesOneA && matchesOneB > matchesTwoB) {
	        		// a 2, b 1
	        		phaseOne.addVariant(rec, alleleB, readsB);
	        		phaseTwo.addVariant(rec, alleleA, readsA);
	        		matched = true;

	        		// TODO: write phased variant to VCF (re-order GT field, add PS format) 

	        	} else {
	        		// ambiguous... we can't keep the phase, or the phase block is over
	        		warn.warn("Can't add variant to phase block: "+ rec +" (not enough matches or ambiguous assignment); alleleA => p1:"+ matchesOneA+", p2:"+matchesTwoA+"; alleleB => p1:"+matchesOneB+", p2:"+matchesTwoB);
	        		if (matchesOneA + matchesTwoA >  0 && matchesOneB + matchesTwoB > 0) {
	        			// we found matched alleles in both phases -- so, we are still within the phaseset, 
	        			// but this variant can't be phased. Don't split, but don't phase this variant.
	        			matched = true;
	        		}
	        	}
        	} 

        	if (!matched) {
        		// if we haven't added any variants ** and** we have a definite A/B split 
        		//     if the VCF and BAM are a bit out of sync, then it's possible that a VCF record
        		//     won't be found in the BAM. Local re-alignment would solve this, but be computationally expensive
        		//     TODO: perform a realignment *if* a variant isn't seen?
        		
        		// cycle phase set
        		if (phaseOne != null) {
        			phaseOne.close();
        		}
        		if (phaseTwo != null) {
        			phaseTwo.close();
        		}
        		
        		phaseOne = new PhaseSet(phaseSetNum, 1, outBamFname, varOutFname, header, factory);
        		phaseTwo = new PhaseSet(phaseSetNum, 2, outBamFname, varOutFname, header, factory);
        		phaseSetNum++;
        		
        		phaseOne.addVariant(rec, alleleA, readsA);
        		phaseTwo.addVariant(rec, alleleB, readsB);

        		// TODO: write variants to VCF -- the actual number (phaseOne, phaseTwo) is arbitrary here
        		//       0 = ref, 1 = alt, but the order is the phase
        		//	     ref alt GT
        		//       A   C   0|1
        		//       C   G   1|0
        		//       A   T   1|0
        		//       A   T   0|1
        		//
        		//       alleles: AGTA, CCAT
        	}
        }
    	vcfReader.close();

    	if (phaseOne != null) {
    		phaseOne.close();
    	}
    	
    	if (phaseTwo != null) {
    		phaseTwo.close();
    	}
    	
        reader.close();
    }
    
    /**
     * Given a set of reads, return the list of reads that match a given variant (including indels)
     * @param reads
     * @param rec     VCF record (remember -- one-based position)
     * @param allele  the variant to match (ref or one of the alts) indels determined by comparing to the ref allele.
     * @return list of reads that match the variant allele
     * @throws IOException 
     */
    protected List<SAMRecord> filterReads(List<SAMRecord> reads, VCFRecord rec, String allele, IndexedFastaFile fasta) throws IOException {
    	if (verbose) {
    		System.err.println("Looking for variant: "+rec.getChrom()+":"+rec.getPos()+":"+rec.getRef()+">"+allele);
    	}
    	List<SAMRecord> ret = new ArrayList<SAMRecord>();
    	
    	for (SAMRecord read: reads) {
    		if (ReadUtils.containsVariant(read, rec, allele, fasta)) {
    	    	if (verbose) {
	    			System.err.println("Read: "+ read.getReadName()+" MATCH");
	     		}
    			ret.add(read);
     		} else {
     	    	if (verbose) {
     	    		System.err.println("Read: "+ read.getReadName()+" FAIL");
         		}
     		}
    	}
//		if (rec.getPos() == 29943806) {
//			System.exit(1);
//		}
    	
		return ret;
	}


    /**
     * Find all reads that cover this position
     * @param ref
     * @param start
     * @param end
     * @return
     */
	protected List<SAMRecord> findReads(String ref, int start, int end) {
        // SAM reader uses 0-based start pos
    	List<SAMRecord> reads = new ArrayList<SAMRecord>();
        SAMRecordIterator it = reader.query(ref, start+1, end, false);
        if (verbose) {
        	System.err.println("Looking for reads in: " + ref+":"+(start+1)+"-" + (end));
        }
        while (it.hasNext()) {
            SAMRecord read = it.next();
            
            // filters go here
            if (unique) {
            	if (!ReadUtils.isReadUniquelyMapped(read)) {
            		continue;
            	}
            }

            if (filterFlags > 0) {
            	if ((read.getFlags() & filterFlags) != 0) {
            		continue;
            	}
            }

            if (requiredFlags > 0) {
            	if ((read.getFlags() & requiredFlags) != requiredFlags) {
            		continue;
            	}
            }
            
            reads.add(read);
        }
        it.close();
        return reads;
    }

}
