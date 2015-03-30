package io.compgen.ngsutils.cli.bam;

import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.ValidationStringency;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.TabWriter;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.bam.DenovoPileup;
import io.compgen.ngsutils.bam.DenovoPileup.PileupPos;
import io.compgen.ngsutils.bam.DenovoPileup.PileupRead;
import io.compgen.ngsutils.bam.support.ReadUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * PileupCli - produces output similar to samtools mpileup, but using de DenovoPileup class.
 * @author mbreese
 *
 *  @See PileupReader - this class shouldn't be used, just fork out to samtools and read the mpileup output, pileup has too many undocumented edge cases for output
 *
 */
@Command(name="pileup", desc="Produces a pileup-like output", category="bam", doc="This command aims to produce an output that is similar to the samtools mpileup. However, due to some undocumented behavior in the output format, some small variation is likely.", deprecated=true)
@Deprecated
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
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        samFilename = filename;
    }

    @Option(desc="Reference FASTA file (indexed)", name="ref")
    public void setRefFilename(String filename) {
        this.refFilename = filename;
    }

    @Option(desc="Only count properly paired reads", name="paired")
    public void setPaired(boolean val) {
        this.paired = val;
    }

    @Option(desc="Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc="Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    @Option(desc="Minimum base-quality (default: 13)", name="base-qual", defaultValue="13")
    public void setMinBaseQual(int minBaseQual) {
        this.minBaseQual = minBaseQual;
    }

    @Option(desc="Minimum read mapping-quality (default: 0)", name="map-qual", defaultValue="0")
    public void setMinMappingQual(int minMappingQual) {
        this.minMappingQual = minMappingQual;
    }

    @Option(desc="Required flags", name="flags-req", defaultValue="0")
    public void setRequiredFlags(int requiredFlags) {
        this.requiredFlags = requiredFlags;
    }

    @Option(desc="Filter flags (Default: 1796)", name="flags-filter", defaultValue="1796")
    public void setFilterFlags(int filterFlags) {
        this.filterFlags = filterFlags;
    }

    @Exec
    public void exec() throws IOException {
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
        
        for (PileupPos pileupPos: IterUtils.wrap(ProgressUtils.getIterator(f.getName(), denovoPileup.pileup(), new FileChannelStats(channel), new ProgressMessage<PileupPos>(){

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
