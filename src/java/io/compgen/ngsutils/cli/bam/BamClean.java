package io.compgen.ngsutils.cli.bam;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileHeader.SortOrder;
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
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.bam.support.BamHeaderUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

@Command(name="bam-clean", desc="Cleans a BAM file from common errors", category="bam")
public class BamClean extends AbstractCommand {
    private String[] filenames = null;
    private String tmpDir = null;

    private boolean unmappedMAPQ0 = false;
    private boolean secondaryUnique = false;
    private boolean sortingReset = false;
    private String failedFilename = null;
    
    @UnnamedArg(name = "INFILE OUTFILE")
    public void setFilename(String[] filenames) {
        this.filenames = filenames;
    }

    @Option(desc = "Remove secondary reads and reset unique tags (fixes NH tag if secondary alignments have been removed, requires name-sorted BAM)", name="fix-secondary")
    public void setSecondaryUnique(boolean secondaryUnique) {
        this.secondaryUnique = secondaryUnique;
    }

    @Option(desc = "Unmapped reads should have MAPQ=0", name="mapq0")
    public void setUnmappedMAPQ0(boolean unmappedMAPQ0) {
        this.unmappedMAPQ0 = unmappedMAPQ0;
    }

    @Option(desc="Write temporary files here", name="tmpdir", helpValue="dir")
    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }

    @Option(desc = "Write failed reads to this file (BAM)", name = "failed", helpValue = "fname")
    public void setFailedFilename(String failedFilename) {
        this.failedFilename = failedFilename;
    }

    @Option(desc = "Remove the sort-order flag from a BAM file (sets the sort order to 'unsorted')", name="reset-sorting")
    public void setSortingReset(boolean sortingReset) {
        this.sortingReset = sortingReset;
    }




    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filenames == null || filenames.length != 2) {
            throw new CommandArgumentException("You must specify input and output filename!");
        }
        
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        readerFactory.validationStringency(ValidationStringency.SILENT);

        SamReader reader = null;
        String name;
        FileChannel channel = null;
        if (filenames[0].equals("-")) {
            reader = readerFactory.open(SamInputResource.of(System.in));
            name = "<stdin>";
        } else {
            File f = new File(filenames[0]);
            FileInputStream fis = new FileInputStream(f);
            channel = fis.getChannel();
            reader = readerFactory.open(SamInputResource.of(fis));
            name = f.getName();
        }

        if (secondaryUnique && reader.getFileHeader().getSortOrder() != SortOrder.queryname) {
            throw new CommandArgumentException("In order to fix secondary alignment/unique flags, the BAM file must be name-sorted!");
        }
        
        SAMFileWriter writer = null;

        SAMFileWriterFactory factory = new SAMFileWriterFactory();

        File outfile = null;
        OutputStream outStream = null;
        
        if (filenames[1].equals("-")) {
            outStream = new BufferedOutputStream(System.out);
        } else {
            outfile = new File(filenames[1]);
        }
        
        if (tmpDir != null) {
            factory.setTempDirectory(new File(tmpDir));
        } else if (outfile == null || outfile.getParent() == null) {
            factory.setTempDirectory(new File(".").getCanonicalFile());
        } else if (outfile!=null) {
            factory.setTempDirectory(outfile.getParentFile());
        }

        SAMFileHeader header = reader.getFileHeader().clone();
        
        if (sortingReset) {
        	header.setSortOrder(SortOrder.unsorted);
        }
        
        SAMProgramRecord pg = BamHeaderUtils.buildSAMProgramRecord("bam-clean", header);
        List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(header.getProgramRecords());
        pgRecords.add(0, pg);
        header.setProgramRecords(pgRecords);

        if (secondaryUnique) {
        	header.setSortOrder(SortOrder.unsorted); // samtools sort -n uses a different sorting method than HTSJDK, so we need to set this.
        }

        if (outfile != null) {
            writer = factory.makeBAMWriter(header, true, outfile);
        } else {
            writer = factory.makeBAMWriter(header,  true,  outStream);
        }
 
        
        SAMFileWriter failedWriter = null;
        if (failedFilename != null) {
        	SAMFileHeader failedHeader = header.clone();
        	failedHeader.setSortOrder(SortOrder.unsorted);
            failedWriter = factory.makeBAMWriter(failedHeader, true, new File(failedFilename));
        }


        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReadName();
            }}, new CloseableFinalizer<SAMRecord>());
        long totalCount = 0;
        long alteredCount = 0;
        
        List<SAMRecord> records = new ArrayList<SAMRecord>();
        String curName = "";
        
        while (it.hasNext()) {
            totalCount++;
            SAMRecord read = it.next();
            if (unmappedMAPQ0) {
            	if (read.getReadUnmappedFlag()) {
	                if (read.getMappingQuality() != 0) {
	                    read.setMappingQuality(0);
	                    alteredCount++;
	                }
	                writer.addAlignment(read);
                }
            } else if (secondaryUnique){
            	if (!read.getReadName().equals(curName)) {
            		alteredCount += writeAndFixSecondary(writer, records);
            		records.clear();
            	} 
            	
            	if (read.getSupplementaryAlignmentFlag() || read.getReadFailsVendorQualityCheckFlag() || read.getNotPrimaryAlignmentFlag() || read.getDuplicateReadFlag()) {
            		if (failedWriter != null) {
            			failedWriter.addAlignment(read);
            		}
            	} else {
            		records.add(read);
            	}
            } else {
                writer.addAlignment(read);
            }
        }

        if (secondaryUnique){
        	alteredCount += writeAndFixSecondary(writer, records);
        	records.clear();
    	} 

        reader.close();
        writer.close();
        System.err.println("Total reads     : "+totalCount);
        System.err.println("Altered reads: "+alteredCount);
    }

	private int writeAndFixSecondary(SAMFileWriter writer, List<SAMRecord> records) {
		int altered = 0;
		if (records == null) {
			return altered;
		}

		int NH = 0;
		
		for (SAMRecord read: records) {
			if (read.getFirstOfPairFlag()) {
				NH++;
			}
		}

		int HI = 0;
		for (SAMRecord read1: records) {
			if (read1.getFirstOfPairFlag()) {
				HI++;
				
				if (read1.getIntegerAttribute("HI") != HI || read1.getIntegerAttribute("NH") != NH) {
					altered++;
				}
				
				// reset the NH attribute
				read1.setAttribute("NH", NH);
				read1.setAttribute("HI", HI);
				writer.addAlignment(read1);
				
				for (SAMRecord read2:records) {
					// find the pair to get the HI values correct (there still might be multiple alignments for a given read that aren't secondary)
					if (read2.getFirstOfPairFlag()) {
						continue;
					}
					if (read1.getMateReferenceIndex() == read2.getReferenceIndex() && read1.getMateAlignmentStart() == read2.getAlignmentStart()) {
						// found the mate
						read2.setAttribute("NH", NH);
						read2.setAttribute("HI", HI);
						writer.addAlignment(read2);
					}
				}
			}
		}
		return altered;
	}
}
