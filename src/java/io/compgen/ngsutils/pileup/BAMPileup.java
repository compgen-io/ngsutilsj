package io.compgen.ngsutils.pileup;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BAMPileup {
    private final String[] filenames;
    private String refFilename = null;
    private String bedFilename = null;

    private int minMappingQual = -1;
    private int minBaseQual = -1;
    private int filterFlags = -1;
    private int requiredFlags = -1;
    
    private boolean disableBAQ = true;
    private boolean extendedBAQ = false;
    
    public BAMPileup(String... filenames) {
        this.filenames = filenames;
    }
   
    public Iterator<PileupRecord> pileup() {
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
    
    public Iterator<PileupRecord> pileup(GenomeSpan region) {
        final ProcessBuilder pb = new ProcessBuilder(getCommand(region));
        final Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new RuntimeException("Cannot start samtools mpileup! " + e.getMessage());
        }
        InputStream bis = new BufferedInputStream(proc.getInputStream());
        PileupReader reader = new PileupReader(bis, minBaseQual);
        
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
        
        return reader.iterator();
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
}
