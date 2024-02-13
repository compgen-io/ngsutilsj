package io.compgen.ngsutils.cli.bam; 

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import io.compgen.common.TabWriter;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

@Command(name="bam-wps", desc="For each location in the genome, calculate a window positioning score (WPS)", category="bam", experimental=true, 
doc=      "WPS scores each base in the genome for the number of reads\n"
		+ "that overlap the window minus the reads that span one side\n"
		+ "of the window. The window is defined as +/- Xbp from the base.\n"
		+ "A default window of 60bp is used. Output is bedgraph.\n"
		+ "See: doi:10.1038/s41586-019-1272-6")

public class BamWPS extends AbstractOutputCommand {
	
	class PairSpan {
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getEnclosingInstance().hashCode();
			result = prime * result + Objects.hash(chrom, name);
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
			PairSpan other = (PairSpan) obj;
			if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
				return false;
			return Objects.equals(chrom, other.chrom) && Objects.equals(name, other.name);
		}
		public PairSpan(String name, String chrom, int start, int end) {
			super();
			this.name = name;
			this.chrom = chrom;
			this.start = start;
			this.end = end;
		}
		private String name;
		private String chrom;
		private int start;
		private int end;
		private BamWPS getEnclosingInstance() {
			return BamWPS.this;
		}
		
	}
	
    private String filename = null;
    private boolean lenient = false;
    private boolean silent = false;
    
    private boolean bedGraph = false;

    private boolean paired = false;
    private boolean overlap = false;
    private boolean noDiscord = false;
    private int discordantDistance = 10000;
    private int readBufferLength = 100000;
    private int window = 60;

    private List<String> readBufferList = new ArrayList<String>();
    private Map<String, SAMRecord> readBuffer1 = new HashMap<String, SAMRecord>();
    private Map<String, SAMRecord> readBuffer2 = new HashMap<String, SAMRecord>();
//    private List<SAMRecord> read2Buffer = new ArrayList<SAMRecord>();
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc = "Window size", name="window", defaultValue="60")
    public void setWindow(int window) {
        this.window = window;
    }

    @Option(desc="Output in BedGraph format", name="bg")
    public void setBedGraph(boolean val) {
        this.bedGraph = val;
    }    

    @Option(desc="Only count properly paired reads", name="paired")
    public void setPaired(boolean val) {
        this.paired = val;
    }    

    @Option(desc="Require overlapping reads (R1/R2 overlap, implies --paired, --no-discord)", name="overlap")
    public void setOverlap(boolean overlap) {
        this.overlap = overlap;
        if (overlap) {
        	setPaired(true);
        	setNoDiscord(true);
        }
    }    

    @Option(desc = "Don't count discordant reads (R1/R2 on different chrom)", name="no-discord")
    public void setNoDiscord(boolean noDiscord) {
        this.noDiscord = noDiscord;
    }

    @Option(desc = "Discordant distance (reads on same chrom > this distance)", name="discord-dist", defaultValue="10000")
    public void setDiscordantDistance(int discordantDistance) {
        this.discordantDistance = discordantDistance;
    }

    @Option(desc = "Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc = "Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    


    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAM filename!");
        }

        if (filename.equals("-")) {
            throw new CommandArgumentException("You must specify an input BAM file, not stdin!");
        }


        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        SamReader reader = null;
        SamReader reader2 = null; // for pulling the mate pair

        String name;
        FileChannel channel = null;
        File f = new File(filename);
        FileInputStream fis = new FileInputStream(f);
        channel = fis.getChannel();
        reader = readerFactory.open(SamInputResource.of(fis));
        name = f.getName();

        // open the second reader with the same file
        reader2 = readerFactory.open(SamInputResource.of(f));
        if (!reader2.hasIndex()) {
            throw new CommandArgumentException("You must specify an indexed BAM file!");
        }

        
        TabWriter writer = new TabWriter(out);
        if (!bedGraph) {
	        writer.write_line("## program: " + NGSUtils.getVersion());
	        writer.write_line("## cmd: " + NGSUtils.getArgs());
	        writer.write("chrom");
	        writer.write("pos");
	        writer.write("wps");
	    	writer.eol();
        }
    
    	String curChrom = null;    	
    	PairSpan nextRead = null;
    	
    	// we will key this by read name, so we can process either R1 or R2 first.f
    	Set<PairSpan> windowBuffer = new HashSet<PairSpan>();    	
    	Set<PairSpan> nextBuf = new HashSet<PairSpan>();    	
    	
    	int low = 0;
    	int curPos = 0;
    	int high = 0;
    	
        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReferenceName()+":"+current.getAlignmentStart() + " / " + readBufferList.size();
            }}, new CloseableFinalizer<SAMRecord>());

        long i = 0;
        String lastChrom = null;
        int lastStart = -1;
        int lastEnd = -1;
        int lastWPS = -10000000;
        
//        try {
		        while (it.hasNext()) {

		        	if (windowBuffer.size() == 0) {
		        		if (nextRead == null) {
		        			nextRead = getNextRead(it, reader2);
		        		}
		        		if (nextRead == null) {
		        			break;
		        		}
		        		
		        		curChrom = nextRead.chrom;
		        		curPos = nextRead.start + (window/2);
		//        		System.out.println("NEWBUF "+curChrom+":"+curPos);
		        	} 
		//    		System.out.println("CURPOS "+curChrom+":"+curPos);
		
		        	low = curPos - (window/2);
		    		high = curPos + (window - (window/2));
		        	
		    		if (high > reader.getFileHeader().getSequence(curChrom).getSequenceLength()) {
		    			// if the window is past the end of the chromosome, skip the rest
		    			windowBuffer.clear();
		    			continue;
		    		}
		    		
		    		if (nextRead!= null && nextRead.chrom.equals(curChrom) && (
		     				(nextRead.start<= low && nextRead.end >= low) ||  // spans the low thres
		     				(nextRead.start<= high && nextRead.end >= high ) || // spans the high thres
		     				(nextRead.start>= low && nextRead.end <= high )
		     				)
		 				){ // contained within the window
		//        		System.out.println("ADD "+nextRead.name);
		     			
		     			windowBuffer.add(nextRead);
		     			nextRead = null;
		     		}
		    		 
		        	while (nextRead == null) {
		        		nextRead = getNextRead(it, reader2);
		        		if (nextRead == null) {
		        			// no more reads -- it.hasNext == false;        			
		        			break;
		        		} else if (nextRead.chrom.equals(curChrom) && (
		        				(nextRead.start<= low && nextRead.end >= low) ||  // spans the low thres
		        				(nextRead.start<= high && nextRead.end >= high ) || // spans the high thres
		        				(nextRead.start>= low && nextRead.end <= high )
		        				)
		    				){ // contained within the window
		//            		System.out.println("ADD "+nextRead.name);
		
		        			windowBuffer.add(nextRead);
		        			nextRead = null;
		        		} else {
		//            		System.out.println("NEXT "+nextRead.name);
		
		        		}
		        		
		        	}
		        	
		        	int accSpan = 0;
		        	int accOneEnd = 0;
		        	
		        	for (PairSpan pair: windowBuffer) {
		        		if (pair.start <= low && pair.end >= high) {
		        			accSpan ++;
		        			nextBuf.add(pair);
		        		} else if (pair.start <= low && pair.end < low) {
		        			// overlaps low end
		        			accOneEnd ++;
		        			nextBuf.add(pair);
		        		} else if (pair.start <= high && pair.end > high) {
		        			// overlaps high end
		        			accOneEnd ++;
		        			nextBuf.add(pair);
		        		} else {
		        			// doesn't overlap current window...
		        		}
		        	}
		        	
		        	
	        		int wps = accSpan-accOneEnd;
	        		
		        	if (bedGraph) {
			        	if (accSpan + accOneEnd > 0) {
			        		if (wps != lastWPS || lastChrom == null || !lastChrom.equals(curChrom)) {
			        			if (lastChrom != null) {
				        			writer.write(lastChrom);
				        			writer.write(lastStart);
				        			writer.write(lastEnd+1);
				        			writer.write(lastWPS);
						        	writer.eol();
			        			}
			        			lastChrom = curChrom;
			        			lastStart = curPos;
			        			lastEnd = curPos;
			        			lastWPS = wps;
			        		} else {
			        			lastEnd = curPos;
			        		}
			        	} else {
		        			if (lastChrom != null) {
			        			writer.write(lastChrom);
			        			writer.write(lastStart);
			        			writer.write(lastEnd+1);
			        			writer.write(lastWPS);
					        	writer.eol();
					        	lastChrom = null;
		        			}
			        	}
		        	} else {
			        	writer.write(curChrom);
			        	writer.write(curPos+1); // output 1-based coordinates
			        	writer.write(wps);
			        	writer.write();
			        	writer.eol();
		        	}
		
		        	windowBuffer = nextBuf;
		        	nextBuf = new HashSet<PairSpan>();
		        	
		        	curPos ++;
		        }
//            }
//		} catch (InterruptedException e) {
//		}
    
		if (bedGraph && lastChrom != null) {
			writer.write(lastChrom);
			writer.write(lastStart);
			writer.write(lastEnd+1);
			writer.write(lastWPS);
        	writer.eol();
		}

        writer.close();
        reader.close();
//        if (cancelled.get()) {
//        	System.err.println("Cancelled...");
//        } else {
        	System.err.println("Successfully read: "+i+" records.");
//        }
    }

	private SAMRecord findMate(Iterator<SAMRecord> it, SAMRecord read) {
		if (readBuffer2.containsKey(read.getReadName())) {
			SAMRecord read2 = readBuffer2.remove(read.getReadName());
			return read2;
			
		}
		
		int lastPos = read.getAlignmentEnd() + this.readBufferLength;
		while (it.hasNext()) {
			SAMRecord read2 = it.next();
			if (read2.getReadName().equals(read.getReadName())) {
				return read2;
			}
			
			if (readBuffer1.containsKey(read2.getReadName())) {
				if (!readBuffer2.containsKey(read2.getReadName())) {
					readBuffer2.put(read2.getReadName(), read2);
				}
			} else {
				readBufferList.add(read2.getReadName());
				readBuffer1.put(read2.getReadName(), read2);
			}
			
			if (!read2.getReferenceIndex().equals(read.getReferenceIndex()) || (read2.getAlignmentStart() > lastPos)) {
				// if we're past the window, we're done. 
				return null;
			}
		}
		
		return null;
	}
    
	private PairSpan getNextRead(Iterator<SAMRecord>it, SamReader reader2) {
		while (it.hasNext()) {
	        SAMRecord read = null;
			if (readBuffer1.size() == 0) {
				read = it.next();
			} else {				
				read = readBuffer1.remove(readBufferList.get(0));
				readBufferList.remove(0);
			}
			if (read == null) {
				continue;
			}
	        
	        SAMRecord pair = null;
	
	        if (read.getReadUnmappedFlag() || read.getMateUnmappedFlag()) {
	            continue;
	        }
	        
	        if (read.isSecondaryOrSupplementary()) {
	        	continue;
	        }
	        
	        if (read.getReadPairedFlag()) {
	        	pair = findMate(it, read);
	        }

	        if (this.paired && pair == null) {
				continue;
			}
			
	        if (this.noDiscord && pair != null) {
	        	if (!pair.getReferenceName().equals(read.getReferenceName())) {
	        		continue;
	        	}
	        	
	        	if(read.getAlignmentEnd() < pair.getAlignmentStart()) {
	        		if (pair.getAlignmentStart() - read.getAlignmentEnd() > this.discordantDistance) {
	        			continue;
	        		}
	        	} else if(pair.getAlignmentEnd() < read.getAlignmentStart()) {
	        		if (read.getAlignmentStart() - pair.getAlignmentEnd() > this.discordantDistance) {
	        			continue;
	        		} 
	        	} else {
	        		// reads overlap
	        	}
	        }
	        
	        if (this.overlap && pair != null) {
	        	if(read.getAlignmentStart() < pair.getAlignmentStart()) {
	        		// R1 is first
	        		if (pair.getAlignmentStart() > read.getAlignmentEnd()) {
	        			// R2 starts after R1 ends, so not overlapping
	        			continue;
	        		}
	        	} else {
	        		// R2 is first (or start the same)
	        		if (read.getAlignmentStart() > pair.getAlignmentEnd()) {
	        			// R1 starts after R2 ends, so not overlapping
	        			continue;
	        		}
	        	}
	        }	         

			int start = read.getAlignmentStart();
			int end = read.getAlignmentEnd();
			
			if (pair!=null && pair.getReferenceName().equals(read.getReferenceName()) && pair.getAlignmentStart() < start) {
				// pair exists and is on the same chrom.
				start = pair.getAlignmentStart();
			}
			if (pair!=null && pair.getReferenceName().equals(read.getReferenceName())&& pair.getAlignmentEnd() > end) {
				// pair exists and is on the same chrom.
				end = pair.getAlignmentEnd();
			}
	
			return new PairSpan(read.getReadName(), read.getReferenceName(), start, end);
		}
		return null;
	}

}
