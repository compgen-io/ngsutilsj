package io.compgen.ngsutils.cli.fasta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;
import io.compgen.common.TallyValues;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.support.SeqUtils;

@Command(name="fasta-tri", desc="Determine the trinucleotide counts for a genome", category="fasta")
public class FastaTri extends AbstractOutputCommand {
    private String filename = null;
    private String bedFilename = null;
    private String include = null;
    private String exclude = null;
    
    @Option(desc="BED file containing regions to count", name="bed")
    public void setBEDFile(String bedFilename) {
        this.bedFilename = bedFilename;
    }    
    
    @Option(desc="Only count these sequences (comma-delimited)", name="include")
    public void setInclude(String include) {
        this.include = include;
    }    
    
    @Option(desc="Don't count these sequences (comma-delimited)", name="exclude")
    public void setExclude(String exclude) {
        this.exclude = exclude;
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
        
        if (exclude != null && include != null) {
            throw new CommandArgumentException("You can't use both --include and --exclude at the same time!");
        }

        if (exclude != null && bedFilename != null) {
            throw new CommandArgumentException("You can't use both --exclude and --bed at the same time!");
        }

        if (include != null && bedFilename != null) {
            throw new CommandArgumentException("You can't use both --include and --bed at the same time!");
        }

        FastaReader fasta = FastaReader.open(filename);

        TallyValues<String> tally = new TallyValues<String>();
       

        if (bedFilename != null) {
            StringLineReader bedReader = new StringLineReader(bedFilename);
            for (String line: IterUtils.wrap(bedReader.iterator())) {
                if (line.trim().length()>0){
                    String[] spl = line.split("\t");
                    String ref = spl[0];
                    int start = Integer.parseInt(spl[1]);
                    int end = Integer.parseInt(spl[2]);
                    
                    String seq = fasta.fetchSequence(ref, start, end);
                    for (int i=0; i < seq.length()-2; i++) {
                        String tri = seq.substring(i, i+3);
                        tally.incr(tri);
                    }
                }
            }
            
            bedReader.close();

        } else {
            
            StringLineReader reader = new StringLineReader(filename);
            String buf = null;
            boolean includeThisSeq = false;
            final String seqName[] = new String[1];
            
            Set<String> includeSeqs = null;
            Set<String> excludeSeqs = null;

            if (include != null) {
                includeSeqs = new HashSet<String>();
                for (String s: include.split(",")) {
                    includeSeqs.add(s);
                }
            }
            if (exclude != null) {
                excludeSeqs = new HashSet<String>();
                for (String s: exclude.split(",")) {
                    excludeSeqs.add(s);
                }
            }
            
            
            for (String line: IterUtils.wrap(reader.progress(new ProgressMessage<String>(){

                @Override
                public String msg(String current) {
                    return seqName[0];
                }}))) {
                if (line.charAt(0) == '>') {
                    buf = null;
                    String name = line.trim().split(" ")[0].substring(1);
                    
                    if (includeSeqs == null && excludeSeqs == null) {
                        includeThisSeq = true;
                    } else if (includeSeqs != null) {
                        if (includeSeqs.contains(name)) {
                            includeThisSeq = true;
                        } else {
                            includeThisSeq = false;
                        }
                    } else if (excludeSeqs != null) {
                        if (excludeSeqs.contains(name)) {
                            includeThisSeq = false;
                        } else {
                            includeThisSeq = true;
                        }
                    }

                    if (includeThisSeq) {
                        seqName[0] = name + " *";
                    } else {
                        seqName[0] = name + " -";
                    }
                    
                } else if (includeThisSeq) {
                    line = line.toUpperCase();
                    if (buf != null) {
                        buf += line.trim();
                    } else { 
                        buf = line;
                    }

                    int i=0;
                    for (i=0; i < buf.length()-2; i++) {
                        String tri = buf.substring(i, i+3);
//                        System.err.println(tri);
                        tally.incr(tri);
                    }
                    buf = buf.substring(i);
                }
            }

            reader.close();
        }
        
        List<String> validTri = new ArrayList<String>();
        validTri.add("ACA");
        validTri.add("ACC");
        validTri.add("ACG");
        validTri.add("ACT");
        validTri.add("ATA");
        validTri.add("ATC");
        validTri.add("ATG");
        validTri.add("ATT");
        validTri.add("CCA");
        validTri.add("CCC");
        validTri.add("CCG");
        validTri.add("CCT");
        validTri.add("CTA");
        validTri.add("CTC");
        validTri.add("CTG");
        validTri.add("CTT");
        validTri.add("GCA");
        validTri.add("GCC");
        validTri.add("GCG");
        validTri.add("GCT");
        validTri.add("GTA");
        validTri.add("GTC");
        validTri.add("GTG");
        validTri.add("GTT");
        validTri.add("TCA");
        validTri.add("TCC");
        validTri.add("TCG");
        validTri.add("TCT");
        validTri.add("TTA");
        validTri.add("TTC");
        validTri.add("TTG");
        validTri.add("TTT");
        
        for (String tri: validTri) {
            long count = tally.getCount(tri) + tally.getCount(SeqUtils.revcomp(tri));
            System.out.println(tri+"\t"+count);
        }
    }
}
