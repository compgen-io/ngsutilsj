package org.ngsutils.cli.bam;

import java.io.IOException;

import net.sf.samtools.CigarOperator;
import net.sf.samtools.SAMFileReader.ValidationStringency;

import org.ngsutils.NGSUtils;
import org.ngsutils.NGSUtilsException;
import org.ngsutils.bam.Pileup;
import org.ngsutils.bam.Pileup.PileupPos;
import org.ngsutils.bam.Pileup.PileupRead;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.support.TabWriter;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj pileup")
@Command(name="pileup", desc="Produces a pileup-like output", cat="bam")
public class PileupCli extends AbstractOutputCommand {
    
    private String samFilename=null;
    private String refFilename=null;
    
    private int minMappingQual=0;
    private int minBaseQual=13;
    
    private int requiredFlags = 0;
    private int filterFlags = 1796;
    
    private boolean lenient = false;
    private boolean silent = false;
    
    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        samFilename = filename;
    }

    @Option(description = "Reference FASTA file (indexed)", longName="ref", defaultToNull=true)
    public void setRefFilename(String filename) {
        this.refFilename = filename;
    }

    @Option(description = "Use lenient validation strategy", longName="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(description = "Use silent validation strategy", longName="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    @Option(description = "Minimum base-quality (default: 13)", longName="base-qual", defaultValue="13")
    public void setMinBaseQual(int minBaseQual) {
        this.minBaseQual = minBaseQual;
    }

    @Option(description = "Minimum read mapping-quality (default: 0)", longName="map-qual", defaultValue="0")
    public void setMinMappingQual(int minMappingQual) {
        this.minMappingQual = minMappingQual;
    }

    @Option(description = "Required flags", longName="flags-req", defaultValue="0")
    public void setRequiredFlags(int requiredFlags) {
        this.requiredFlags = requiredFlags;
    }

    @Option(description = "Filter flags (Default: 1796)", longName="flags-filter", defaultValue="1796")
    public void setFilterFlags(int filterFlags) {
        this.filterFlags = filterFlags;
    }

    @Override
    public void exec() throws NGSUtilsException, IOException {
        Pileup pileup = new Pileup(samFilename);
        if (lenient) {
            pileup.getReader().setValidationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            pileup.getReader().setValidationStringency(ValidationStringency.SILENT);
        }

        if (refFilename != null) {
            pileup.setFASTARef(refFilename);
        }

        pileup.setMinMappingQual(minMappingQual);
        pileup.setMinBaseQual(minBaseQual);
        pileup.setFlagFilter(filterFlags);
        pileup.setFlagRequired(requiredFlags);
        
        TabWriter writer = new TabWriter();
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + samFilename);
        
        for (PileupPos pileupPos: pileup.pileup()) {
            writer.write(pileup.getReader().getFileHeader().getSequence(pileupPos.refIndex).getSequenceName());
            writer.write(pileupPos.pos);
            writer.write(pileupPos.refBase);
            writer.write(pileupPos.getCoverage());
            String bases = "";
            String quals = "";
            for (PileupRead pr: pileupPos.getReads()) {
                if (pr.isStart()) {
                    bases += "^" + (char)(pr.read.getMappingQuality() + 33);
                }
                
                if (pr.cigarOp == CigarOperator.D) {
                    if (pr.length > 0) {
                        bases += "-" + Integer.toString(pr.length);
                    } else {
                        quals += pr.getBaseQual();
                    }
                } else if (pr.cigarOp == CigarOperator.I) {
                    bases += "+" + Integer.toString(pr.length);
                } else if (pr.cigarOp == CigarOperator.M) {
                    quals += pr.getBaseQual();
                }

                bases += pr.getStrandedBaseCall(pileupPos.refBase);

                if (pr.isEnd()) {
                    bases += "$";
                }
            }
            writer.write(bases);
            writer.write(quals);
            writer.eol();
        }
    }
}
