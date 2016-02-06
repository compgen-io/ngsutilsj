package io.compgen.ngsutils.cli.fasta;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Command(name="fasta-mask", desc="Mask regions of a FASTA reference", category="fasta")
public class FastaMask extends AbstractOutputCommand {
    
    class MaskRegion implements Comparable<MaskRegion>{
        public final String ref;
        public final int start;
        public final int end;

        public MaskRegion(String ref) {
            this(ref, -1, -1);
        }
        public MaskRegion(String ref, int start, int end) {
            this.ref = ref;
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return "mask("+ref+":"+start+"-"+end+")";
        }
        
        public boolean containsPos(int pos) {
            if (start == -1 && end == -1) {
                return true;
            }
            
            if (start <= pos && pos < end) {
                return true;
            }
            return false;
        }
        
        @Override
        public int compareTo(MaskRegion o) {
            if (o == null) {
                return 1;
            }
            return 0;
        }
    }
    
    private String filename = null;
    private String region = null;
    private String bedFilename = null;
    private boolean lowercase = false;
    
    @Option(desc="Region to mask (ex: ref:start-end, 1-based; or just 'ref' to mask entire sequence)", name="region")
    public void setRegion(String region) {
        this.region = region;
    }    

    @Option(desc="BED file containing regions to mask", name="bed")
    public void setBEDFile(String bedFilename) {
        this.bedFilename = bedFilename;
    }    
    
    @Option(desc="Mask with lowercase bases (default: mask as 'N')", name="lower")
    public void setLowercase(boolean lowercase) {
        this.lowercase = lowercase;
    }


    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) throws CommandArgumentException {
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }
        if (region == null && bedFilename == null) {
            throw new CommandArgumentException("Missing/invalid arguments! You must specify either --bed or --region!");
        }

        Map<String, List<MaskRegion>> masks = new HashMap<String, List<MaskRegion>>();
        
        if (region != null) {
            if (region.contains(":")) {
                String[] spl = region.split(":");
                String[] startend = spl[1].split("-");
                int start = Integer.parseInt(startend[0])-1;
                int end = Integer.parseInt(startend[1]);
                
                List<MaskRegion> list = new ArrayList<MaskRegion>();
                list.add(new MaskRegion(spl[0], start, end));
                masks.put(spl[0], list);
            } else {
                List<MaskRegion> list = new ArrayList<MaskRegion>();
                list.add(new MaskRegion(region));
                masks.put(region, list);
            }
        } else {
            StringLineReader bedReader = new StringLineReader(bedFilename);
            for (String line: IterUtils.wrap(bedReader.iterator())) {
                if (line.trim().length()>0){
                    String[] spl = line.split("\t");
                    String name = spl[0];
                    int start = Integer.parseInt(spl[1]);
                    int end = Integer.parseInt(spl[2]);
                    
                    if (!masks.containsKey(name)) {
                        masks.put(name,  new ArrayList<MaskRegion>());
                    }
                    
                    masks.get(name).add(new MaskRegion(name, start, end));
                }
            }
            
            bedReader.close();

            for (String k: masks.keySet()) {
                Collections.sort(masks.get(k));
            }
        }
        
        
        StringLineReader reader = new StringLineReader(filename);
        String currentName = null;
        int pos = 0;
        
        List<MaskRegion> workingMasks = null;
        MaskRegion currentMask = null;
        
        for (String line: IterUtils.wrap(reader.iterator())) {
            if (line.charAt(0) == '>') {
                currentName = line.substring(1).split("\\W",2)[0];
                pos = 0;

                if (masks.containsKey(currentName)) {
                    workingMasks = masks.get(currentName);
                    currentMask = workingMasks.remove(0);
                } else {
                    workingMasks = null;
                    currentMask = null;
                }

                out.write((line+"\n").getBytes());
                System.err.println(currentName);

            } else {
                if (currentMask == null) {
                    // we aren't looking for a mask, so just write the output and go.
                    // no need to keep track of the [pos] anymore for this sequence.
                    out.write((line+"\n").getBytes());

                } else {
                    String buf = "";
                    String lower = null;
                    boolean logged = false;

                    for (int i=0; i<line.length(); i++) {
                        if (currentMask != null && currentMask.containsPos(pos)) {
                            if (verbose && !logged) {
                                logged = true;
                                System.err.println("pos: "+currentName+":"+pos+" masked by: "+currentMask);
                            }
                            if (lowercase) {
                                if (lower == null) {
                                    lower = line.toLowerCase();
                                }
                                buf += lower.charAt(i);

                            } else {
                                buf += 'N';
                            }

                            pos++;

                            if (!currentMask.containsPos(pos)) {
                                // we just left a mask - pop the next one
                                if (workingMasks.size() > 0) {
                                    currentMask = workingMasks.remove(0);
                                } else {
                                    currentMask = null;
                                }    
                            }
                            
                        } else {
                            buf += line.charAt(i);
                            pos++;
                        }
                    }
                    out.write((buf+"\n").getBytes());
                }
            }
        }
        reader.close();
    }
}
