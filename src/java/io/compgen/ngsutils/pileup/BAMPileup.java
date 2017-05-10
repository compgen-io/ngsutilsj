package io.compgen.ngsutils.pileup;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import htsjdk.samtools.util.CloseableIterator;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;

public class BAMPileup {
    private final String[] filenames;
    private String refFilename = null;
    private String bedFilename = null;

    private int maxDepth = -1;
    private int minMappingQual = -1;
    private int minBaseQual = -1;
    private int filterFlags = -1;
    private int requiredFlags = -1;
    private boolean nogaps = false;
    
    private boolean disableBAQ = true;
    private boolean extendedBAQ = false;
    
    private String tmpPath = null;
    
    public BAMPileup(String... filenames) {
        this.filenames = filenames;
    }
   
    public void setTempPath(String tmpPath) {
        this.tmpPath = tmpPath;
    }
    
    public CloseableIterator<PileupRecord> pileup() throws IOException {
        return pileup(null);
    }

    public List<String> getCommand() {
        return getCommand(null);
    }

    public List<String> getCommand(GenomeSpan region) {
        List<String> cmd = new ArrayList<String>();
        cmd.add("samtools");
        cmd.add("mpileup");
        cmd.add("-O");
        if (minMappingQual > -1) {
            cmd.add("-q");
            cmd.add(""+minMappingQual);
        }

        // TODO: add mapping quality (-s) to output?? This would make it possible to use minBaseQual again
        //       as an argument to samtools

//      This needs to be done so that we get all the calls, qual, and pos info.
        cmd.add("-Q 0");
        
//        if (minBaseQual > -1) {
//            cmd.add("-Q");
//            cmd.add(""+minBaseQual);
//        }
        
        if (filterFlags > 0) {
            cmd.add("--ff");
            cmd.add(""+filterFlags);
        }
        
        if (requiredFlags > 0) {
            cmd.add("--rf");
            cmd.add(""+requiredFlags);
        }
        
        if (refFilename!=null) {
            cmd.add("-f");
            cmd.add(refFilename);
        }
        
        if (bedFilename!=null) {
            cmd.add("-l");
            cmd.add(bedFilename);
        }
        
        if (maxDepth > 0) {
            cmd.add("-d");
            cmd.add(""+maxDepth);
        }
        
        if (disableBAQ) {
            cmd.add("-B");
        }
        
        if (extendedBAQ) {
            cmd.add("-E");
        }
        
        if (region != null) {
            cmd.add("-r");
            cmd.add(region.ref+":"+(region.start+1)+"-"+region.end);
        }

        for (String f: filenames) {
            cmd.add(f);
        }
        return cmd;
    }
    
    public CloseableIterator<PileupRecord> pileup(GenomeSpan region) throws IOException {
        
        if (tmpPath != null) {
            return tmpPathPileup(region);
        } else {
        
        final ProcessBuilder pb = new ProcessBuilder(getCommand(region));
        final Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new RuntimeException("Cannot start samtools mpileup! " + e.getMessage());
        }
        InputStream bis = new BufferedInputStream(proc.getInputStream());
        final PileupReader reader = new PileupReader(bis, minBaseQual, nogaps);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    proc.waitFor();
                    proc.getErrorStream().close();
                    proc.getInputStream().close();
                    proc.getOutputStream().close();
                    if (proc.exitValue()!=0) {
                        throw new RuntimeException("Error running: "+ StringUtils.join(" ", pb.command()));
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                proc.destroy();
            }}).start();

        return new CloseableIterator<PileupRecord>(){
            Iterator<PileupRecord> it = reader.iterator();
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public PileupRecord next() {
                return it.next();
            }

            @Override
            public void remove() {
                it.remove();
            }

            @Override
            public void close() {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }};
        }
    }

    private CloseableIterator<PileupRecord> tmpPathPileup(GenomeSpan region) throws IOException {
        final ProcessBuilder pb = new ProcessBuilder(getCommand(region));

        final File tmp = File.createTempFile(".ngsutilsj-pileupreader-", ".txt", new File(tmpPath));
        tmp.deleteOnExit();
        pb.redirectOutput(tmp);
        
        final Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new RuntimeException("Cannot start samtools mpileup! " + e.getMessage());
        }

        try {
            proc.waitFor();
            proc.getErrorStream().close();
            proc.getInputStream().close();
            proc.getOutputStream().close();
            if (proc.exitValue()!=0) {
                throw new RuntimeException("Error running: "+ StringUtils.join(" ", pb.command()));
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        proc.destroy();
        
        InputStream bis = new BufferedInputStream(new FileInputStream(tmp));
        final PileupReader reader = new PileupReader(bis, minBaseQual, nogaps);

        return new CloseableIterator<PileupRecord>(){
            Iterator<PileupRecord> it = reader.iterator();
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public PileupRecord next() {
                return it.next();
            }

            @Override
            public void remove() {
                it.remove();
            }

            @Override
            public void close() {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                tmp.delete();
                
            }};

    }
    
    
    public void setMinMappingQual(int minMappingQual) {
        this.minMappingQual = minMappingQual;
    }

    public void setMinBaseQual(int minBaseQual) {
        this.minBaseQual = minBaseQual;
    }

    public void setFlagFilter(int filterFlags) {
        this.filterFlags = filterFlags;
    }

    public void setFlagRequired(int requiredFlags) {
        this.requiredFlags = requiredFlags;
    }

    public void setDisableBAQ(boolean val) {
        this.disableBAQ = val;
    }

    public void setExtendedBAQ(boolean val) {
        this.extendedBAQ = val;
    }

    public void setRefFilename(String refFilename) {
        this.refFilename = refFilename;
    }

    public void setBedFilename(String bedFilename) {
        this.bedFilename = bedFilename;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }
    
    public void setNoGaps(boolean nogaps) {
        this.nogaps = nogaps;        
    }
}
