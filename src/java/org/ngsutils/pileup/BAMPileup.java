package org.ngsutils.pileup;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.support.StringUtils;

public class BAMPileup {
    private final String bamFilename;
    private String refFilename = null;
    private String bedFilename = null;

    private int minMappingQual = -1;
    private int minBaseQual = -1;
    private int filterFlags = -1;
    private int requiredFlags = -1;
    
    private boolean disableBAQ = true;
    private boolean extendedBAQ = false;
    
    public BAMPileup(String bamFilename) {
        this.bamFilename = bamFilename;
    }
   
    public Iterator<PileupRecord> pileup() {
        return pileup(null);
    }

    public Iterator<PileupRecord> pileup(GenomeRegion region) {
        List<String> cmd = new ArrayList<String>();
        cmd.add("samtools");
        cmd.add("mpileup");
        
        if (minMappingQual > -1) {
            cmd.add("-q");
            cmd.add(""+minMappingQual);
        }
        
        if (minBaseQual > -1) {
            cmd.add("-Q");
            cmd.add(""+minBaseQual);
        }
        
        if (filterFlags > -1) {
            cmd.add("--ff");
            cmd.add(""+filterFlags);
        }
        
        if (requiredFlags > -1) {
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

        cmd.add(bamFilename);

        final ProcessBuilder pb = new ProcessBuilder(cmd);
        final Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new NGSUtilsException("Cannot start samtools mpileup! " + e.getMessage());
        }
        InputStream bis = new BufferedInputStream(proc.getInputStream());
        PileupReader reader = new PileupReader(bis);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    proc.waitFor();
                    proc.getErrorStream().close();
                    proc.getInputStream().close();
                    proc.getOutputStream().close();
                    if (proc.exitValue()!=0) {
                        throw new NGSUtilsException("Error running: "+ StringUtils.join(" ", pb.command()));
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
