package io.compgen.ngsutils.cli.bam;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMProgramRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.Pair;
import io.compgen.common.TabWriter;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.bam.support.BamHeaderUtils;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;
import io.compgen.ngsutils.support.WindowCounter;

@Command(name="bam-softclip", desc="Calculate the amount of soft-clipping at each position across a genome. Output in bedGraph format.", category="bam", experimental=true)
public class BamSoftClip extends AbstractOutputCommand {
    private String filename = null;
    private boolean lenient = false;
    private boolean silent = false;

    private int minLength = 0;
    private boolean unique = false;
    private int filterFlags = 0;
    private int requiredFlags = 0;

    private String outBamFilename = null;
    private String tmpDir = null;

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc = "Minimum length of clipping to count", name="min", defaultValue="1")
    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    @Option(desc = "Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc = "Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    
    
    @Option(desc="Write out reads that contain soft clipping to the BAM file", name="out-bam", helpValue="fname.bam")
    public void setOutBamFilename(String outBamFilename) throws CommandArgumentException {
    	if (outBamFilename.equals("-")) {
    		throw new CommandArgumentException("You cannot write soft clipped reads to stdout!");
    	}
        this.outBamFilename = outBamFilename;
    }
    
    @Option(desc="Write temporary files here", name="tmpdir", helpValue="dir")
    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
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
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAM filename!");
        }
        
        
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        SamReader reader = null;
        String name;
        FileChannel channel = null;
        if (filename.equals("-")) {
            reader = readerFactory.open(SamInputResource.of(System.in));
            name = "<stdin>";
        } else {
            File f = new File(filename);
            FileInputStream fis = new FileInputStream(f);
            channel = fis.getChannel();
            reader = readerFactory.open(SamInputResource.of(fis));
            name = f.getName();
        }

        SAMFileWriter outBam = null;

        if (outBamFilename != null) {
            SAMFileWriterFactory factory = new SAMFileWriterFactory();
    
            File outfile = new File(outBamFilename);
            
            if (tmpDir != null) {
                factory.setTempDirectory(new File(tmpDir));
            } else {
                factory.setTempDirectory(outfile.getParentFile());
            }
    
            SAMFileHeader header = reader.getFileHeader().clone();
            SAMProgramRecord pg = BamHeaderUtils.buildSAMProgramRecord("bam-softclip", header);
            List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(header.getProgramRecords());
            pgRecords.add(0, pg);
            header.setProgramRecords(pgRecords);
        	outBam = factory.makeBAMWriter(header, true, outfile);
        }
        
        long totalCount = 0;
        long clippedCount = 0;
        long filteredCount = 0;

        TabWriter tab = new TabWriter(out);
        final WindowCounter counter = new WindowCounter();

        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            @Override
            public String msg(SAMRecord current) {
            	Pair<Integer, Integer> head = counter.head();
            	if (head == null) {
            		return current.getReferenceName()+":"+current.getAlignmentStart();
            	}
                return counter.size()+"/"+head.one+" "+current.getReferenceName()+":"+current.getAlignmentStart();
            }}, new CloseableFinalizer<SAMRecord>());

        String curRef = null;
        
        while (it.hasNext()) {
            SAMRecord read = it.next();

            // filters go here
            if (unique) {
            	if (!ReadUtils.isReadUniquelyMapped(read)) {
            		filteredCount++;
            		continue;
            	}
            }

            if (filterFlags > 0) {
            	if ((read.getFlags() & filterFlags) != 0) {
            		filteredCount++;
            		continue;
            	}
            }

            if (requiredFlags > 0) {
            	if ((read.getFlags() & requiredFlags) != requiredFlags) {
            		filteredCount++;
            		continue;
            	}
            }

            totalCount++;
            
            boolean clipped = false;

            if (curRef == null || !read.getReferenceName().equals(curRef)) {
            	if (curRef != null) {
	            	while (counter.size() > 0 ) {
	                	Pair<Integer,Integer> posCount = counter.pop();
	            		tab.write(curRef);
	            		tab.write(posCount.one);
	            		tab.write(posCount.one+1);
	            		tab.write(posCount.two);
	            		tab.eol();
	            	}
            	}
                curRef = read.getReferenceName();
            }

            int refpos = read.getAlignmentStart() - 1; // alignment-start is 1-based

            while (counter.size() > 0 && counter.head().one < refpos) {
            	Pair<Integer,Integer> posCount = counter.pop();
        		tab.write(curRef);
        		tab.write(posCount.one);
        		tab.write(posCount.one+1);
        		tab.write(posCount.two);
        		tab.eol();
            }
            
            
	        for (CigarElement el: read.getCigar().getCigarElements()) {
				switch (el.getOperator()) {
				case M:
				case EQ:
				case X:
				case N:
				case D:
					refpos += el.getLength();
					break;
				case S:
					if (el.getLength()>= minLength) {
						clipped = true;
						counter.incr(refpos);
					}
					break;
				default:
					break;
				}
			}
			
			if (clipped) {
				clippedCount++;
				
				if (outBam!=null) {
				    outBam.addAlignment(read);
				}
			}
        }

    	while (counter.size() > 0 ) {
        	Pair<Integer,Integer> posCount = counter.pop();
    		tab.write(curRef);
    		tab.write(posCount.one);
    		tab.write(posCount.one+1);
    		tab.write(posCount.two);
    		tab.eol();
    	}

    	tab.close();    	
        reader.close();
        if (outBam != null) {
        	outBam.close();
        }
        System.err.println("Total reads     : "+totalCount);
        System.err.println("Soft-clipped reads: "+clippedCount);
        System.err.println("Filtered: "+ filteredCount);
    }
}
