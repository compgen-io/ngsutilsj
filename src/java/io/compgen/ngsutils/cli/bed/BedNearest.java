package io.compgen.ngsutils.cli.bed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.BadReferenceException;
import io.compgen.ngsutils.annotation.BedAnnotationSource;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;

@Command(name="bed-nearest", desc="Given reference and query BED files, for each query region, find the nearest reference region", category="bed")
public class BedNearest extends AbstractOutputCommand {
    
    private String refFilename = null;
    private String queryFilename = null;
    
    private int extendUp = 0;
    private int extendDown = 0;

    private boolean ignoreStrand = false;
    
    @UnnamedArg(name = "REF QUERY")
    public void setFilenames(String[] filenames) throws CommandArgumentException {
        if (filenames.length != 2) {
            throw new CommandArgumentException("Invalid target/query");
        }
        this.refFilename = filenames[0];
        this.queryFilename = filenames[1];
    }
    
    @Option(name="ns", desc="Ignore strand in the query BED file")
    public void setIgnoreStrand(boolean val) {
        this.ignoreStrand = val;
    }

    @Option(name="up", desc="Allow at most this distance upstream of a reference region (strand-specific)")
    public void setExtendUp(int extendUp) throws CommandArgumentException {
        if (extendUp < 0) {
            throw new CommandArgumentException("Invalid extend value!");
        }
        this.extendUp = extendUp;
    }

    @Option(name="down", desc="Allow at most this distance downstream of a reference region (strand-specific)")
    public void setExtendDown(int extendDown) throws CommandArgumentException {
        if (extendDown < 0) {
            throw new CommandArgumentException("Invalid extend value!");
        }
        this.extendDown = extendDown;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (refFilename == null || queryFilename == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }

        BedAnnotationSource ann = new BedAnnotationSource(refFilename);
        int extend = Math.max(extendUp, extendDown);
        
        for (BedRecord record: IterUtils.wrap(BedReader.readFile(queryFilename))) {
            GenomeSpan coord = record.getCoord();
            if (ignoreStrand) {
                coord = coord.clone(Strand.NONE);
            }
            
            GenomeSpan queryCoord = new GenomeSpan(coord.ref, coord.start - extend, coord.end + extend, coord.strand);
            BedRecord  best = null;
            List<String> bestNames = new ArrayList<String>();
            int bestDist = 0;
            
            for (BedRecord rec: ann.findAnnotation(queryCoord)) {
                try {
                    int dist = rec.getCoord().distanceTo(coord);
                    
                    if (rec.getCoord().strand == Strand.PLUS || rec.getCoord().strand == Strand.NONE) {
                        if (dist < 0) {
                            if (dist < -extendUp) {
                                continue;
                            }
                        } else {
                            if (dist > extendDown) {
                                continue;
                            }
                        }
                    } else {
                        if (dist > 0) {
                            if (dist > extendUp) {
                                continue;
                            }
                        } else {
                            if (dist < -extendDown) {
                                continue;
                            }
                        }
                    }
                    
                    dist = Math.abs(dist);
                    if (best == null || dist < bestDist) {
                        bestDist = dist;
                        best = rec;
                        bestNames.clear();
                        bestNames.add(best.getName());
                    } else if (dist == bestDist) {
                    	if (!bestNames.contains(best.getName())) {
                    		bestNames.add(best.getName());
                    	}
                    }
                    
                } catch (BadReferenceException e) {
                }
            }
            
            List<String> cols = new ArrayList<String>();
            cols.add(record.getCoord().ref);
            cols.add(""+record.getCoord().start);
            cols.add(""+record.getCoord().end);
            cols.add(""+record.getName());
            cols.add(""+record.getScore());
            cols.add(record.getCoord().strand.toString());
            if (best != null) {
                cols.add(StringUtils.join(",", bestNames));
                if (best.getCoord().strand == Strand.PLUS || best.getCoord().strand == Strand.NONE) {
                    try {
                        cols.add(""+best.getCoord().distanceTo(coord));
                    } catch (BadReferenceException e) {
                    }
                    cols.add("+");
                } else {
                    try {
                        cols.add(""+(-best.getCoord().distanceTo(coord)));
                    } catch (BadReferenceException e) {
                    }
                    cols.add("-");
                }
            } else {
                cols.add("*");
                cols.add("");
                cols.add("");
            }
            out.write((StringUtils.join("\t", cols)+"\n").getBytes());
        }
    }
}
