package io.compgen.ngsutils.cli.fastq;

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
import io.compgen.ngsutils.fastq.filter.PairedFilter;
import io.compgen.ngsutils.fastq.filter.PrefixFilter;
import io.compgen.ngsutils.fastq.filter.PrefixQualFilter;
import io.compgen.ngsutils.fastq.filter.SeqTrimFilter;
import io.compgen.ngsutils.fastq.filter.SizeFilter;
import io.compgen.ngsutils.fastq.filter.SuffixQualFilter;
import io.compgen.ngsutils.fastq.filter.WhitelistFilter;
import io.compgen.ngsutils.fastq.filter.WildcardFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Command(name = "fastq-filter", desc = "Filters reads from a FASTQ file.", category="fastq")
public class FastqFilterCli extends AbstractOutputCommand {
    private boolean paired = false;
    private String suffixQuality = null;
    private String prefixQuality = null;
    private int prefixTrimLength = -1;
    private int minimumSize = -1;
    private int maxWildcard = -1;
    
    private String trimSeq = null;
    private int trimMinOverlap = 4;
    private double trimMinPctMatch = 0.9;
    
    private String whitelist = null;
    private String blacklist = null;

    private String filename;
    
    public FastqFilterCli() {
    }

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) throws IOException {
        this.filename = filename;
    }

    @Option(desc="Sequence trim filter (adapters) (Default: not used)", name="trim-seq")
    public void setTrimSeq(String trimSeq) {
        this.trimSeq = trimSeq;
    }

    @Option(desc="Sequence trim minimum overlap (default: 4)", name="trim-overlap", defaultValue="4")
    public void setTrimMinOverlap(int trimMinOverlap) {
        this.trimMinOverlap = trimMinOverlap;
    }

    @Option(desc="Sequence trim minimum percent match (default: 0.9)", name="trim-pct", defaultValue="0.9")
    public void setTrimMinPctMatch(double trimMinPctMatch) {
        this.trimMinPctMatch = trimMinPctMatch;
    }
    
    @Option(desc="Paired filter (for interleaved files) (Default: not used)", name="paired")
    public void setPaired(boolean paired) {
        this.paired = paired;
    }

    @Option(desc="Suffix quality filter (B-trim, use '#' for Illumina) (Default:'')", name="suffixqual")
    public void setSuffixQualityFilter(String suffixQuality) {
        this.suffixQuality = suffixQuality;
    }

    @Option(desc="Prefix quality filter (B-trim, use '#' for Illumina) (Default:'')", name="prefixqual")
    public void setPrefixQualityFilter(String prefixQuality) {
        this.prefixQuality = prefixQuality;
    }

    @Option(desc="Prefix trim length (Default: 0)", name="prefixtrim", defaultValue = "0")
    public void setPrefixTrimLength(int prefixTrimLength) {
        this.prefixTrimLength = prefixTrimLength;
    }

    @Option(desc="Minimum read length (Default: 35)", name="size", defaultValue = "35")
    public void setMinimumSize(int minimumSize) {
        this.minimumSize = minimumSize;
    }

    @Option(desc="Maximum wildcard calls (Default: 2)", name="wildcard", defaultValue = "2")
    public void setMaxWildcard(int maxWildcard) {
        this.maxWildcard = maxWildcard;
    }

    @Option(desc="Blacklist filename (read names)", name="blacklist")
    public void setBlacklist(String blacklist) {
        this.blacklist = blacklist;
    }

    @Option(desc="Whitelist filename (read names)", name="whitelist")
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

        if (maxWildcard > 0) {
            parent = new WildcardFilter(parent, verbose, maxWildcard);
            filters.add((FastqFilter) parent);
        }
        
        if (prefixTrimLength > 0) {
            parent = new PrefixFilter(parent, verbose, prefixTrimLength);
            filters.add((FastqFilter) parent);
        }

        if (suffixQuality != null) {
            parent = new SuffixQualFilter(parent, verbose, suffixQuality.charAt(0));
            filters.add((FastqFilter) parent);
        }

        if (prefixQuality != null) {
            parent = new PrefixQualFilter(parent, verbose, prefixQuality.charAt(0));
            filters.add((FastqFilter) parent);
        }

        if (trimSeq != null) {
            parent = new SeqTrimFilter(parent, verbose, trimSeq, trimMinOverlap, trimMinPctMatch);
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

        if (whitelist!=null) {
            parent = new WhitelistFilter(parent, verbose, whitelist);
            filters.add((FastqFilter) parent);
        }

        if (blacklist!=null) {
            parent = new BlacklistFilter(parent, verbose, blacklist);
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
        close();
    }
}
