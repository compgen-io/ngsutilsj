package io.compgen.ngsutils.cli.annotate;

import java.io.IOException;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.annotation.AnnotationSource;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.annotation.RepeatMaskerAnnotationSource;
import io.compgen.ngsutils.annotation.RepeatMaskerAnnotationSource.RepeatAnnotation;
import io.compgen.ngsutils.bam.Strand;

@Command(name="annotate-repeat", desc="Calculates Repeat masker annotations", doc="Note: Column indexes start at 1.", category="annotation", experimental=true)
public class RepeatAnnotate extends AbstractOutputCommand {
    
    private String filename=null;
    private String repeatFilename=null;
    
    private int refCol = -1;
    private int startCol = -1;
    private int endCol = -1;
    private int strandCol = -1;
    private int within = 0;
    
    private boolean hasHeader = true;
    private boolean headerComment = false;
    
    private boolean zeroBased = true;
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc="RepeatMasker annotation filename", name="repeat")
    public void setRepeatFilename(String repeatFilename) {
        this.repeatFilename = repeatFilename;
    }


    @Option(desc="Column of chromosome (Default: 1)", name="col-chrom", defaultValue="1")
    public void setChromCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.refCol = val - 1;
        } else { 
            this.refCol = -1;
        }
    }

    @Option(desc="Column of start-position (1-based position) (Default: 2)", name="col-start", defaultValue="2")
    public void setStartCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.startCol = val - 1;
        } else { 
            this.startCol = -1;
        }
    }

    @Option(desc="Column of end-position (Default: -1, no end col)", name="col-end", defaultValue="-1")
    public void setEndCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.endCol = val - 1;
        } else { 
            this.endCol = -1;
        }
    }

    @Option(desc="Column of strand (Default: -1, not strand-specific)", name="col-strand", defaultValue="-1")
    public void setStrandCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.strandCol = val - 1;
        } else { 
            this.strandCol = -1;
        }
    }

    @Option(desc="Use BED3 format presets", name="bed3")
    public void setUseBED3(boolean val) {
        if (val) {
            this.refCol = 0;
            this.startCol = 1;
            this.endCol = 2;
            this.strandCol = -1;
        }
    }

    @Option(desc="Use BED6 format presets", name="bed6")
    public void setUseBED6(boolean val) {
        if (val) {
            this.refCol = 0;
            this.startCol = 1;
            this.endCol = 2;
            this.strandCol = 5;
        }
    }

    @Option(desc="Input file uses one-based coordinates (default is 0-based)", name="one")
    public void setOneBased(boolean val) {
        zeroBased = !val;
    }

    @Option(desc="Input file doesn't have a header row", name="noheader")
    public void setHasHeader(boolean val) {
        hasHeader = !val;
    }

    @Option(desc="The header is the last commented line", name="header-comment")
    public void setHeaderComment(boolean val) {
        headerComment = val;
    }


    @Option(desc="Repeat can be within [value] bp of the genomic range (requires start and end columns)", name="within", defaultValue="0")
    public void setWithin(int val) {
        this.within = val;
    }

    @Exec
    public void exec() throws CommandArgumentException, IOException {
        if (repeatFilename == null) {
            throw new CommandArgumentException("You must specify a repeatmasker annotation file!");
        }
        
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input file! (- for stdin)");
        }
        
        if (refCol == -1 || startCol == -1) {
            throw new CommandArgumentException("You must specify at least a chrom-column and start-column!");
        }


        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## repeat-annotations: " + repeatFilename);
        
        if (verbose) {
            System.err.print("Reading RepeatMasker annotation file: "+repeatFilename);
        }
        AnnotationSource<RepeatAnnotation> ann = new RepeatMaskerAnnotationSource(repeatFilename);
        if (verbose) {
            System.err.println(" [done]");
        }

        boolean first = true;
        String lastline = null;
        int colNum = -1;
        for (String line: new StringLineReader(filename)) {
            if (line.charAt(0) == '#') {
                if (lastline != null) {
                    writer.write_line(lastline);
                }
                lastline = line;
                continue;
            }
            
            if (lastline!=null) {
                if (headerComment && hasHeader) {
                    String[] cols = lastline.split("\\t", -1);
                    colNum = cols.length;
                    writer.write(cols);
                    writer.write(ann.getAnnotationNames());
                    writer.eol();
                    first = false;
                } else {
                    writer.write_line(lastline);
                }
                
                lastline = null;
            }
            
            String[] cols = line.split("\\t", -1);
            writer.write(cols);
            if (hasHeader && first) {
                first = false;
                colNum = cols.length;
                writer.write(ann.getAnnotationNames());
                writer.eol();
                continue;
            }
            
            for (int i=cols.length; i<colNum; i++) {
                writer.write("");
            }
            
            String ref = cols[refCol];
            int start = Integer.parseInt(cols[startCol])-within;
            int end = start+within;
            Strand strand = Strand.NONE;
            
            if (!zeroBased && start > 0) {
                start = start - 1;
            }
            
            if (endCol>-1) { 
                end = Integer.parseInt(cols[endCol])+within;
            }
            
            if (strandCol>-1) {
                strand = Strand.parse(cols[strandCol]);
            }
            
            List<RepeatAnnotation> annVals = ann.findAnnotation(new GenomeSpan(ref, start, end, strand)); 
           
            for (int i=0; i < ann.getAnnotationNames().length; i++) {
                String[] outvals = new String[annVals.size()];
                for (int j=0; j < annVals.size(); j++) {
                    outvals[j] = annVals.get(j).toStringArray()[i];
                }
                writer.write(StringUtils.join(",", outvals));
            }
            writer.eol();
        }

        writer.close();
    }
}
