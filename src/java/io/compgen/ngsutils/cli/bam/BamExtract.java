package io.compgen.ngsutils.cli.bam;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bam.support.BamHeaderUtils;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFRecord.VCFAltPos;

@Command(name="bam-extract", desc="Extract reads from a BAM file using either VCF or BED file coordinates", category="bam", experimental=true)
public class BamExtract extends AbstractOutputCommand {
    
    private String inBamFname = null;
    private String bamFname2 = null;
    private String outBamFname = null;
    private String tmpDir = null;

    private String vcfFile = null;
    private String bedFile = null;
    private GenomeSpan region = null;
    
    private boolean vcfAlt = false;
    private String vcfAltRef = null;
    private String vcfAltPos = null;

    private boolean stranded = false;
    private boolean contained = false;
    private boolean spanning = false;
    private boolean lenient = false;
    private boolean silent = false;
    private boolean paired = false;
    
    private Orientation orient = Orientation.UNSTRANDED;
    
    private SAMFileWriter writer = null;
    private SamReader reader = null;
    private SamReader reader2 = null;
    private SamReader readerBam2 = null;

    private Set<String> readsWritten = new HashSet<String>();
    private boolean unique = false;

    private int flanking = 0;
    private int filterFlags = 0;
    private int requiredFlags = 0;


    @UnnamedArg(name = "INFILE OUTFILE")
    public void setFilenames(String[] filenames) throws CommandArgumentException {
    	if (filenames.length != 2) {
    		throw new CommandArgumentException("You must specify an INFILE and and OUTFILE");
    	}
    	if (filenames[0].equals("-")) {
    		throw new CommandArgumentException("Can't extract reads from stdin!");
    	}

        this.inBamFname = filenames[0];
        this.outBamFname = filenames[1];
    }
    

    @Option(desc = "Write temporary files here", name = "tmpdir", helpValue = "dir")
    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }

    @Option(desc = "Bases of extra flanking sequence to include", name = "flanking")
    public void setFlanking(int flanking) {
        this.flanking = flanking;
    }


    @Option(desc="Extract reads using these VCF coordinates", name="vcf")
    public void setVCF(String vcfFile) {
        this.vcfFile = vcfFile;
    }

    @Option(desc="Also export reads from the alt position (SV end)", name="vcf-alt")
    public void setVCFAlt(boolean val) {
    	this.vcfAlt = val;
    }

    @Option(desc="If extracting from the SV alt position, use this INFO value as the secondary chromosome (default: auto extracted)", name="vcf-ref2")
    public void setVCFAltRef(String vcfAltRef) {
    	this.vcfAlt = true;
        this.vcfAltRef = vcfAltRef;
    }

    @Option(desc="If extracting from the SV alt position, use this INFO value as the secondary position (default: auto extracted, END)", name="vcf-pos2")
    public void setVCFAltPos(String vcfAltPos) {
    	this.vcfAlt = true;
        this.vcfAltPos = vcfAltPos;
    }

    @Option(desc="Extract reads using these BED coordinates", name="bed")
    public void setBED(String bedFile) {
        this.bedFile = bedFile;
    }

    @Option(desc="If paired reads aren't found in the primary BAM file, look in this secondary BAM file.", name="bam2")
    public void setBAM2(String bamFile2) {
        this.bamFname2 = bamFile2;
    }

    @Option(desc="Extract reads with these coordinates", name="region")
    public void setRegion(String region) throws CommandArgumentException {
        this.region = GenomeSpan.parse(region);
        if (this.region == null) {
        	throw new CommandArgumentException("Unable to parse region: "+region);
        }
    }

    @Option(desc="Only return reads that span a variant/region (default: false)", name="spanning")
    public void setSpanning(boolean spanning) {
        this.spanning = spanning;
    }

    @Option(desc="Only return reads that are completely contained with the region (default: false, Not valid for VCF input)", name="contained")
    public void setContained(boolean contained) {
        this.contained = contained;
    }

    @Option(desc="Only return reads that are in the proper orientation (required BED6 input)", name="stranded")
    public void setStranded(boolean stranded) {
        this.stranded = stranded;
    }


    @Option(desc="Return both reads for paired-end data", name="paired")
    public void setPaired(boolean paired) {
        this.paired = paired;
    }

    @Option(desc="Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc="Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }
    
    @Option(desc="Library is in FR orientation", name="library-fr")
    public void setLibraryFR(boolean val) {
        if (val) {
            orient = Orientation.FR;
        }
    }

    @Option(desc="Library is in RF orientation", name="library-rf")
    public void setLibraryRF(boolean val) {
        if (val) {
            orient = Orientation.RF;
        }
    }

    @Option(desc="Library is in unstranded orientation (default)", name="library-unstranded")
    public void setLibraryUnstranded(boolean val) {
        if (val) {
            orient = Orientation.UNSTRANDED;
        }
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
    public void exec() throws IOException, VCFParseException, CommandArgumentException {
    	
    	if ((vcfAltRef != null && vcfAltPos == null) || (vcfAltRef == null && vcfAltPos != null)) {
    		throw new CommandArgumentException("You must specify both --vcf-alt-ref and --vcf-alt-pos!");
    	}
    	
    	
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        reader = readerFactory.open(new File(inBamFname));

        if (paired) {
        	// we need a separate reader open to extract paired data
            reader2 = readerFactory.open(new File(inBamFname));
        }
        
        if (bamFname2 != null) {
        	this.readerBam2 = readerFactory.open(new File(bamFname2));
        }
        
        
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();

        File outfile = null;
        OutputStream outStream = null;

        if (outBamFname.equals("-")) {
            outStream = new BufferedOutputStream(System.out);
        } else {
            outfile = new File(outBamFname);
        }

        if (tmpDir != null) {
            factory.setTempDirectory(new File(tmpDir));
        } else if (outfile == null || outfile.getParent() == null) {
            factory.setTempDirectory(new File(".").getCanonicalFile());
        } else if (outfile != null) {
            factory.setTempDirectory(outfile.getParentFile());
        }

        final SAMFileHeader header = reader.getFileHeader().clone();
        final SAMProgramRecord pg = BamHeaderUtils.buildSAMProgramRecord("bam-extract", header);
        final List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(
                header.getProgramRecords());
        pgRecords.add(0, pg);
        header.setProgramRecords(pgRecords);
        header.setSortOrder(SortOrder.unsorted);

        if (outfile != null) {
            writer = factory.makeBAMWriter(header, true, outfile);
        } else {
            writer = factory.makeBAMWriter(header, true, outStream);
        }
        

        
        if (region != null) {
        	extractReads(region.ref, region.start, region.end);
        } else if (bedFile != null) {
        	Iterator<BedRecord> it = BedReader.readFile(bedFile);
//        	int lastRecLen = 0;
        	
        	while (it.hasNext()) {
        		BedRecord rec = it.next();
//        		for (int i=0; i<lastRecLen;i++) {
//        			System.err.write('\b');
//        		}
//        		for (int i=0; i<lastRecLen;i++) {
//        			System.err.write(' ');
//        		}
//        		for (int i=0; i<lastRecLen;i++) {
//        			System.err.write('\b');
//        		}
//        		System.err.print(rec);
//        		lastRecLen = rec.toString().length();
            	extractReads(rec.getCoord().ref, rec.getCoord().start, rec.getCoord().end, rec.getCoord().strand);
        	}
        	System.err.println("Done");
        } else if (vcfFile != null) {
        	VCFReader vcfReader = new VCFReader(vcfFile);
        	Iterator<VCFRecord> it = vcfReader.iterator();
        	while (it.hasNext()) {
        		VCFRecord rec = it.next();
            	extractReads(rec.getChrom(), rec.getPos()-1, rec.getPos());
            	
            	if (vcfAlt) {
            		for (VCFAltPos alt: rec.getAltPos(vcfAltRef,  vcfAltPos, null, null)) {
                    	extractReads(alt.chrom, alt.pos-1, alt.pos);
            		}
            	}
            	
        	}
        	vcfReader.close();
        }

        writer.close();
        reader.close();
        if (reader2 != null) {
        	reader2.close();
        }
        if (readerBam2 != null) {
        	readerBam2.close();
        }
    }
    
    protected void extractReads(String ref, int start, int end) {
    	extractReads(ref, start, end, null);
    }
    protected void extractReads(String ref, int start, int end, Strand strand) {
        // SAM reader uses 0-based start pos
        SAMRecordIterator it = reader.query(ref, start+1-flanking, end+flanking, contained);
        if (verbose) {
        	System.err.println("Looking for reads in: " + ref+":"+(start+1-flanking)+"-" + (end+flanking));
        }
        while (it.hasNext()) {
            SAMRecord read = it.next();
            
            if (strand != null && strand != Strand.NONE && stranded) {
            	Strand readStrand = ReadUtils.getFragmentEffectiveStrand(read, orient);
            	if (!readStrand.matches(strand)) {
            		continue;                    		
            	}
            }
            
            
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

            
        	// this is R1
    		if (!readsWritten.contains(read.getReadName())) {
        		if (paired) {
        			SAMRecord mate = ReadUtils.findMate(reader2, read, filterFlags, requiredFlags);
        			if (mate == null && readerBam2 != null) {
        				// mate wasn't in the primary BAM file, so look in the secondary one...
            			mate = ReadUtils.findMate(readerBam2, read, filterFlags, requiredFlags);
        			}
//                	SAMRecord mate = reader2.queryMate(read);
                	if (mate != null) {

                    	boolean passing = true;
                        if (spanning) {
                        	passing = false;
                        	if (read.getAlignmentStart() <= start && read.getAlignmentEnd() >= end) {
                        		// split read
                        		passing = true;
                        	}
                        	if (mate.getAlignmentStart() <= start && mate.getAlignmentEnd() >= end) {
                        		// split read
                        		passing = true;
                        	}
                        	
                        	if (read.getReferenceName().equals(mate.getReferenceName())) {
                        		if (read.getAlignmentStart() < mate.getAlignmentStart()) {
                                	if (read.getAlignmentStart() <= start && mate.getAlignmentEnd() >= end) {
                                		// split read
                                		passing = true;
                                	}
                        		} else {
                                	if (mate.getAlignmentStart() <= start && read.getAlignmentEnd() >= end) {
                                		// split read
                                		passing = true;
                                	}
                        		}
                        	}
                        }

                        if (passing) {
	                		readsWritten.add(read.getReadName());
	                        writer.addAlignment(read);
	                        writer.addAlignment(mate);
                        }
                	} else {
                		if (verbose) {
                        	System.err.println("Read missing pair (not written): " + read.getReadName());
                		}
                	}
        		} else {
                	boolean passing = true;
                    if (spanning) {
                    	passing = false;
                    	if (read.getAlignmentStart() <= start && read.getAlignmentEnd() >= end) {
                    		// split read
                    		passing = true;
                    	}
                    }

                    if (passing) {
	        			readsWritten.add(read.getReadName());
	                    writer.addAlignment(read);
                    }
        		}
    		}
        }
        it.close();
    }

}
