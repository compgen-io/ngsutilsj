package org.ngsutils.cli.bam;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import net.sf.samtools.CigarOperator;
import net.sf.samtools.SAMFileReader.ValidationStringency;

import org.ngsutils.NGSUtils;
import org.ngsutils.NGSUtilsException;
import org.ngsutils.bam.DenovoPileup;
import org.ngsutils.bam.DenovoPileup.PileupPos;
import org.ngsutils.bam.DenovoPileup.PileupRead;
import org.ngsutils.bam.support.ReadUtils;
import org.ngsutils.support.IterUtils;
import org.ngsutils.support.TabWriter;
import org.ngsutils.support.cli.AbstractOutputCommand;
import org.ngsutils.support.cli.Command;
import org.ngsutils.support.progress.FileChannelStats;
import org.ngsutils.support.progress.ProgressMessage;
import org.ngsutils.support.progress.ProgressUtils;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj pileup")
@Command(name="pileup", desc="Produces a pileup-like output", cat="bam", doc="This command aims to produce an output that is similar to the samtools mpileup. However, due to some undocumented behavior in the output format, some small variation is likely.", experimental=true)
public class PileupCli extends AbstractOutputCommand {
    
    private String samFilename=null;
    private String refFilename=null;
    
    private int minMappingQual=0;
    private int minBaseQual=13;
    
    private int requiredFlags = 0;
    private int filterFlags = 1796;
    
    private boolean lenient = false;
    private boolean silent = false;
    
    private boolean paired = false;
    
    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        samFilename = filename;
    }

    @Option(description = "Reference FASTA file (indexed)", longName="ref", defaultToNull=true)
    public void setRefFilename(String filename) {
        this.refFilename = filename;
    }

    @Option(description = "Only count properly paired reads", longName="paired")
    public void setPaired(boolean val) {
        this.paired = val;
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
        File f = new File(samFilename);
        FileInputStream fis = new FileInputStream(f);
        FileChannel channel = fis.getChannel();
        
        final DenovoPileup denovoPileup = new DenovoPileup(fis);
        if (lenient) {
            denovoPileup.getReader().setValidationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            denovoPileup.getReader().setValidationStringency(ValidationStringency.SILENT);
        }

        if (refFilename != null) {
            denovoPileup.setFASTARef(refFilename);
        }
        if (paired) {
            requiredFlags |= ReadUtils.PROPER_PAIR_FLAG | ReadUtils.READ_PAIRED_FLAG;
            filterFlags |= ReadUtils.READ_UNMAPPED_FLAG | ReadUtils.MATE_UNMAPPED_FLAG; 
        }

        denovoPileup.setMinMappingQual(minMappingQual);
        denovoPileup.setMinBaseQual(minBaseQual);
        denovoPileup.setFlagFilter(filterFlags);
        denovoPileup.setFlagRequired(requiredFlags);
        
        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + samFilename);
        
        for (PileupPos pileupPos: IterUtils.wrapIterator(ProgressUtils.getIterator(f.getName(), denovoPileup.pileup(), new FileChannelStats(channel), new ProgressMessage<PileupPos>(){

            @Override
            public String msg(PileupPos current) {
                return denovoPileup.getReader().getFileHeader().getSequence(current.refIndex).getSequenceName()+":"+current.pos;
            }}))) {
            writer.write(denovoPileup.getReader().getFileHeader().getSequence(pileupPos.refIndex).getSequenceName());
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
