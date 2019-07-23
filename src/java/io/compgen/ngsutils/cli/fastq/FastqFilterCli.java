package io.compgen.ngsutils.cli.fastq;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;
import io.compgen.ngsutils.fastq.filter.BlacklistFilter;
import io.compgen.ngsutils.fastq.filter.FastqFilter;
import io.compgen.ngsutils.fastq.filter.FilteringException;
import io.compgen.ngsutils.fastq.filter.NameSubstring;
import io.compgen.ngsutils.fastq.filter.PairedFilter;
import io.compgen.ngsutils.fastq.filter.PrefixQualFilter;
import io.compgen.ngsutils.fastq.filter.PrefixTrimFilter;
import io.compgen.ngsutils.fastq.filter.SeqTrimFilter;
import io.compgen.ngsutils.fastq.filter.SizeFilter;
import io.compgen.ngsutils.fastq.filter.SuffixQualFilter;
import io.compgen.ngsutils.fastq.filter.SuffixTrimFilter;
import io.compgen.ngsutils.fastq.filter.WhitelistFilter;
import io.compgen.ngsutils.fastq.filter.WildcardFilter;

@Command(name = "fastq-filter", desc = "Filters reads from a FASTQ file.", category="fastq")
public class FastqFilterCli extends AbstractOutputCommand {
    private boolean paired = false;
    private int suffixQuality = -1;
    private int prefixQuality = -1;
    private int prefixTrimLength = -1;
    private int suffixTrimLength = -1;
    private int minimumSize = -1;
    private int maxWildcard = -1;
    
    private String trimSeq = null;
    private String trimSeq1 = null;
    private String trimSeq2 = null;
    private int trimMinOverlap = 6;
    private double trimMinPctMatch = 0.9;
    
    private String whitelist = null;
    private String blacklist = null;
    
    private String nameSubstr1 = null;
    private String nameSubstr2 = null;

    private String filename;
    private String summaryFilename=null;
    
    public FastqFilterCli() {
    }

    @UnnamedArg(name = "FILE", defaultValue="-")
    public void setFilename(String filename) throws CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing file!");
        }
        if (!filename.equals("-")) {
            if (!new File(filename).exists()) {
                throw new CommandArgumentException("Missing file: "+filename);
            }
        }
        this.filename = filename;
    }

    @Option(desc="Write summary of filters to file", name="summary", helpValue="fname")
    public void setSummaryFilename(String summaryFilename) {
        this.summaryFilename = summaryFilename;
    }

    @Option(desc="Read name contains this substring (e.g. flowcell/lane ID)", name="substr", helpValue="val")
    public void setNameSubstr1(String nameSubstr1) {
        this.nameSubstr1 = nameSubstr1;
    }

    @Option(desc="Read name contains this secondary substring (e.g. barcode)", name="substr2", helpValue="val")
    public void setNameSubstr2(String nameSubstr2) {
        this.nameSubstr2 = nameSubstr2;
    }

    @Option(desc="Sequence trim filter using default Illumina R1/R2 adapters (see URL for more options: https://support.illumina.com/bulletins/2016/12/what-sequences-do-i-use-for-adapter-trimming.html)", name="trim-illumina")
    public void setTrimIllumina(boolean trimIllumina) {
        if (trimIllumina) {
            this.trimSeq1 = "AGATCGGAAGAGCACACGTC";
            this.trimSeq2 = "AGATCGGAAGAGCGTCGTGT";
            
            // Full sequences: https://support.illumina.com/bulletins/2016/12/what-sequences-do-i-use-for-adapter-trimming.html
            // this.trimSeq1 = "AGATCGGAAGAGCACACGTCTGAACTCCAGTCA";
            // this.trimSeq2 = "AGATCGGAAGAGCGTCGTGTAGGGAAAGAGTGT";

        }
    }

    @Option(desc="Sequence trim filter (adapters, all reads)", name="trim-seq")
    public void setTrimSeq(String trimSeq) {
        this.trimSeq = trimSeq;
    }

    @Option(desc="Sequence trim filter (adapters, read 1)", name="trim-seq1")
    public void setTrimSeq1(String trimSeq1) {
        this.trimSeq1 = trimSeq1;
    }

    @Option(desc="Sequence trim filter (adapters, read 2)", name="trim-seq2")
    public void setTrimSeq2(String trimSeq2) {
        this.trimSeq2 = trimSeq2;
    }

    @Option(desc="Sequence trim minimum overlap", name="trim-overlap", defaultValue="6")
    public void setTrimMinOverlap(int trimMinOverlap) {
        this.trimMinOverlap = trimMinOverlap;
    }

    @Option(desc="Sequence trim minimum percent match", name="trim-pct", defaultValue="0.9")
    public void setTrimMinPctMatch(double trimMinPctMatch) {
        this.trimMinPctMatch = trimMinPctMatch;
    }
    
    @Option(desc="Paired filter (for interleaved files) (default: not used)", name="paired")
    public void setPaired(boolean paired) {
        this.paired = paired;
    }

    @Option(desc="Suffix quality filter (minimum quality)", name="suffixqual")
    public void setSuffixQualityFilter(int suffixQuality) {
        this.suffixQuality = suffixQuality;
    }

    @Option(desc="Prefix quality filter (minimum quality)", name="prefixqual")
    public void setPrefixQualityFilter(int prefixQuality) {
        this.prefixQuality = prefixQuality;
    }

    @Option(desc="Prefix fixed-base trim length", name="prefixtrim")
    public void setPrefixTrimLength(int prefixTrimLength) {
        this.prefixTrimLength = prefixTrimLength;
    }

    @Option(desc="Suffix fixed-base trim length", name="suffixtrim")
    public void setSufffixTrimLength(int suffixTrimLength) {
        this.suffixTrimLength = suffixTrimLength;
    }

    @Option(desc="Minimum read length", name="size")
    public void setMinimumSize(int minimumSize) {
        this.minimumSize = minimumSize;
    }

    @Option(desc="Maximum wildcard calls (-1 to disable)", name="wildcard")
    public void setMaxWildcard(int maxWildcard) {
        this.maxWildcard = maxWildcard;
    }

    @Option(desc="Blacklist filename (text file, one read name per line)", name="blacklist")
    public void setBlacklist(String blacklist) {
        this.blacklist = blacklist;
    }

    @Option(desc="Whitelist filename (text file, one read name per line)", name="whitelist")
    public void setWhitelist(String whitelist) {
        this.whitelist = whitelist;
    }
    
    @Exec
    public void exec() throws IOException, CommandArgumentException, FilteringException {
        if (whitelist != null && blacklist != null) {
            throw new CommandArgumentException("You can not specify both a whitelist and a blacklist!");
        }
        
        FastqReader reader = Fastq.open(filename);

        if (verbose) {
            System.err.println("Filtering file:" + filename);
        }

        final List<FastqFilter> filters = new ArrayList<FastqFilter>();
        Iterable<FastqRead> parent = reader;

        if (nameSubstr1!=null) {
            if (nameSubstr2!=null) {
                parent = new NameSubstring(parent, verbose, nameSubstr1, nameSubstr2);
            } else {
                parent = new NameSubstring(parent, verbose, nameSubstr1);
            }
            filters.add((FastqFilter) parent);
        }

        if (whitelist!=null) {
            parent = new WhitelistFilter(parent, verbose, whitelist);
            filters.add((FastqFilter) parent);
        }

        if (blacklist!=null) {
            parent = new BlacklistFilter(parent, verbose, blacklist);
            filters.add((FastqFilter) parent);
        }

        if (prefixTrimLength > 0) {
            parent = new PrefixTrimFilter(parent, verbose, prefixTrimLength);
            filters.add((FastqFilter) parent);
        }

        if (suffixTrimLength > 0) {
            parent = new SuffixTrimFilter(parent, verbose, suffixTrimLength);
            filters.add((FastqFilter) parent);
        }

        if (suffixQuality > 0) {
            parent = new SuffixQualFilter(parent, verbose, suffixQuality);
            filters.add((FastqFilter) parent);
        }

        if (prefixQuality > 0) {
            parent = new PrefixQualFilter(parent, verbose, prefixQuality);
            filters.add((FastqFilter) parent);
        }

        if (trimSeq != null) {
            parent = new SeqTrimFilter(parent, verbose, trimSeq1, trimMinOverlap, trimMinPctMatch, -1);
            filters.add((FastqFilter) parent);            
        }
        
        if (trimSeq1 != null) {
            parent = new SeqTrimFilter(parent, verbose, trimSeq1, trimMinOverlap, trimMinPctMatch, 1 );
            filters.add((FastqFilter) parent);            
        }
        
        if (trimSeq2 != null) {
            parent = new SeqTrimFilter(parent, verbose, trimSeq2, trimMinOverlap, trimMinPctMatch, 2);
            filters.add((FastqFilter) parent);            
        }
        
        if (maxWildcard > -1) {
            parent = new WildcardFilter(parent, verbose, maxWildcard);
            filters.add((FastqFilter) parent);
        }
        
        if (minimumSize > 0) {
            parent = new SizeFilter(parent, verbose, minimumSize);
            filters.add((FastqFilter) parent);
        }

        if (paired) {
            parent = new PairedFilter(parent, verbose);
            filters.add((FastqFilter) parent);
        }

        int i = 0;
        for (final FastqRead read : parent) {
            if (verbose) {
                i++;
                if (i % 100000 == 0) {
                    System.err.println("Read: " + i);
                }
                
            }
            if (read != null) {
                read.write(out);
            }
        }

        reader.close();
        
        if (verbose) {
            System.err.println("Filter\tTotal\tAltered\tRemoved");
            for (final FastqFilter iter : filters) {
                System.err.println(iter.getClass().getSimpleName() + "\t" + iter.getTotal()
                        + "\t" + iter.getAltered() + "\t" + iter.getRemoved());
            }
        }

        if (summaryFilename != null) {
            PrintStream os = new PrintStream(new FileOutputStream(summaryFilename));
            os.println("Filter\tTotal\tAltered\tRemoved");
            for (final FastqFilter iter : filters) {
                os.println(iter.getClass().getSimpleName() + "\t" + iter.getTotal()
                        + "\t" + iter.getAltered() + "\t" + iter.getRemoved());
            }
            os.close();
        }
        
        close();
    }
}
