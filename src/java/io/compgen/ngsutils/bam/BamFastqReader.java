package io.compgen.ngsutils.bam;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import io.compgen.common.StringUtils;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;
import io.compgen.ngsutils.support.SeqUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class BamFastqReader implements FastqReader {
    
    private String filename = null;
    private SAMRecordIterator samIterator = null;
    private InputStream inputStream = null;
    private FileChannel channel = null;

    private SamReader reader = null;
    private String name = null;
    private boolean comments = true;
    
    private boolean first = true;
    private boolean second = true;
    
    private boolean lenient = false;
    private boolean silent = false;
    
    // Should we keep track of what we've exported so that
    // we only export one read/pair 
    // Note: this increase the memory required to keep track of all read names 
    private boolean deduplicate = true;

    // include mapped reads in export (default true)
    private boolean includeMapped = true;

    // include unmapped reads in export (default true)
    private boolean includeUnmapped = true;
    

    public BamFastqReader(String filename) throws FileNotFoundException {
        this.filename = filename;
    }

    public BamFastqReader(InputStream in, FileChannel channel, String name) {
        this.inputStream = in;
        this.channel = channel;
        this.name = name;
    }

    public void setDeduplicate(boolean val) {
        if (samIterator == null) {
            this.deduplicate = val;
        }
    }

    public void setIncludeMapped(boolean val) {
        if (samIterator == null) {
            this.includeMapped = val;
        }
    }
    
    public void setIncludeUnmapped(boolean val) {
        if (samIterator == null) {
            this.includeUnmapped = val;
        }
    }

    public void setFirst(boolean val) {
        if (samIterator == null) {
            this.first = val;
        }
    }

    public void setSecond(boolean val) {
        if (samIterator == null) {
            this.second = val;
        }
    }

    public void setLenient(boolean lenient) {
        if (samIterator == null) {
            this.lenient = lenient;
        }
    }

    public void setSilent(boolean silent) {
        if (samIterator == null) {
            this.silent = silent;
        }
    }
   
    public void setComments(boolean val) {
        if (samIterator == null) {
            this.comments = val;
        }
    }
    
    @Override
    public Iterator<FastqRead> iterator() {
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        reader = null;
        if (filename != null) {
            if (filename.equals("-")) {
                reader = readerFactory.open(SamInputResource.of(System.in));
            } else {
                reader = readerFactory.open(new File(filename));
            }
        } else if (inputStream != null) {
            reader = readerFactory.open(SamInputResource.of(inputStream));
        } else {
            throw new RuntimeException("Missing SAM resource!");
        }

        samIterator = reader.iterator();
        
        return ProgressUtils.getIterator((name == null) ? "BAM": name, new Iterator<FastqRead>(){
            Deque<FastqRead> buf = null;
            Map<String, FastqRead> firstReads = new HashMap<String, FastqRead>();
            Map<String, FastqRead> secondReads = new HashMap<String, FastqRead>();

            Set<String> exported = new HashSet<String>();

            private void populate() {
                if (buf == null) {
                    buf = new ArrayDeque<FastqRead>();
                }
                int len = buf.size();
                while (buf.size() == len && samIterator.hasNext()) {
                    SAMRecord read = samIterator.next();
                    
                    if (read.getReadFailsVendorQualityCheckFlag()) {
                        // Skip QC failed reads.
                        continue;
                    }
                    
                    if (read.getDuplicateReadFlag()) {
                        // Skip flagged PCR duplicates
                        continue;
                    }
                    
                    if (!includeUnmapped) {
                        if (read.getReadUnmappedFlag()) {
                            // read is unmapped, skip
                            continue;
                        } else if (read.getReadPairedFlag() && read.getMateUnmappedFlag()) {
                            // read is paired and mate isn't mapped - skip
                            continue;
                        }
                    }
                    
                    if (!includeMapped) {
                        if (!read.getReadUnmappedFlag()) {
                            // read is mapped - skip
                            continue;
                        } else if (read.getReadPairedFlag() && !read.getMateUnmappedFlag()) {
                            // read is paired and pair is mapped - skip
                            continue;
                        }
                    }

                    String name = read.getReadName();
                    String seq;
                    String qual;
                    if (read.getReadNegativeStrandFlag()) {
                        seq = SeqUtils.revcomp(read.getReadString());
                        qual = StringUtils.reverse(read.getBaseQualityString());
                    } else {
                        seq = read.getReadString();
                        qual = read.getBaseQualityString();
                    }
                      
                    String comment = null;
                    if (comments) {
                        comment = read.getStringAttribute("CO");
                    }
  
                    FastqRead fq = new FastqRead(name, seq, qual, comment);
                    
                    if (read.getReadPairedFlag()) {
                        if (first && !second && read.getFirstOfPairFlag() && (!deduplicate || !exported.contains(name))) {
                            buf.add(fq);
                            exported.add(name);
                        } else if (second && !first && read.getSecondOfPairFlag() && (!deduplicate || !exported.contains(name))) {
                            buf.add(fq);
                            exported.add(name);
                        } else if (first && second && (!deduplicate || !exported.contains(name))) {
                            if (firstReads.containsKey(name) && read.getSecondOfPairFlag()) {
                                buf.add(firstReads.remove(name));
                                buf.add(fq);
                                if (deduplicate) {
                                    exported.add(name);
                                }
                            } else if (secondReads.containsKey(name) && read.getFirstOfPairFlag()) {
                                buf.add(fq);
                                buf.add(secondReads.remove(name));
                                if (deduplicate) {
                                    exported.add(name);
                                }
                            } else if (read.getFirstOfPairFlag()) {
                                firstReads.put(name, fq);
                            } else if (read.getSecondOfPairFlag()) {
                                secondReads.put(name, fq);
                            }
                        }
                    } else {
                        buf.add(fq);
                    }
                }
            }

            @Override
            public boolean hasNext() {
                if (buf == null) {
                    populate();
                }
                return buf.size()>0;
            }

            @Override
            public FastqRead next() {
                if (buf == null) {
                    populate();
                }
                if (buf.size() > 0) {
                    FastqRead tmp = buf.pop();
                    if (buf.size()==0) {
                        populate();
                    }
                    return tmp;
                }
                return null;
            }

            @Override
            public void remove() {
                next();
            }}, new FileChannelStats(channel), new ProgressMessage<FastqRead>() {
                @Override
                public String msg(FastqRead current) {
                    return current.getName();
                }});
    }

    @Override
    public void close() throws IOException {
        if (samIterator != null) {
            samIterator.close();
        }
        reader.close();
    }    
}
