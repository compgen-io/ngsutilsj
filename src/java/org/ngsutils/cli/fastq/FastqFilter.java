package org.ngsutils.cli.fastq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.cli.fastq.filter.BlacklistFilter;
import org.ngsutils.cli.fastq.filter.FilterIterable;
import org.ngsutils.cli.fastq.filter.PairedFilter;
import org.ngsutils.cli.fastq.filter.PrefixFilter;
import org.ngsutils.cli.fastq.filter.SeqTrimFilter;
import org.ngsutils.cli.fastq.filter.SizeFilter;
import org.ngsutils.cli.fastq.filter.SuffixQualFilter;
import org.ngsutils.cli.fastq.filter.WhitelistFilter;
import org.ngsutils.cli.fastq.filter.WildcardFilter;
import org.ngsutils.fastq.FastqRead;
import org.ngsutils.fastq.FastqReader;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application = "ngsutilsj fastq-filter")
@Command(name = "fastq-filter", desc = "Filters reads from a FASTQ file.", cat = "fastq")
public class FastqFilter extends AbstractOutputCommand {
    private FastqReader reader;

    private boolean paired = false;
    private String suffixQuality = null;
    private int prefixTrimLength = -1;
    private int minimumSize = -1;
    private int maxWildcard = -1;
    
    private String trimSeq = null;
    private int trimMinOverlap = 4;
    private double trimMinPctMatch = 0.9;
    
    private String whitelist = null;
    private String blacklist = null;

    public FastqFilter() {
    }

    @Unparsed(name = "FILE")
    public void setFilename(String filename) throws IOException {
        reader = new FastqReader(filename);
    }

    @Option(description = "Sequence trim filter (adapters) (Default: not used)", longName = "trim-seq", defaultToNull=true)
    public void setTrimSeq(String trimSeq) {
        this.trimSeq = trimSeq;
    }

    @Option(description = "Sequence trim minimum overlap (default: 4)", longName = "trim-overlap", defaultValue="4")
    public void setTrimMinOverlap(int trimMinOverlap) {
        this.trimMinOverlap = trimMinOverlap;
    }

    @Option(description = "Sequence trim minimum percent match (default: 0.9)", longName = "trim-pct", defaultValue="0.9")
    public void setTrimMinPctMatch(double trimMinPctMatch) {
        this.trimMinPctMatch = trimMinPctMatch;
    }
    
    @Option(description = "Paired filter (for interleaved files) (Default: not used)", longName = "paired")
    public void setPaired(boolean paired) {
        this.paired = paired;
    }

    @Option(description = "Suffix quality filter (B-trim, use '#' for Illumina) (Default:'')", longName = "suffixqual", defaultToNull = true)
    public void setSuffixQualityFilter(String suffixQuality) {
        this.suffixQuality = suffixQuality;
    }

    @Option(description = "Prefix trim length (Default: 0)", longName = "prefixtrim", defaultValue = "0")
    public void setPrefixTrimLength(int prefixTrimLength) {
        this.prefixTrimLength = prefixTrimLength;
    }

    @Option(description = "Minimum read length (Default: 35)", longName = "size", defaultValue = "35")
    public void setMinimumSize(int minimumSize) {
        this.minimumSize = minimumSize;
    }

    @Option(description = "Maximum wildcard calls (Default: 2)", longName = "wildcard", defaultValue = "2")
    public void setMaxWildcard(int maxWildcard) {
        this.maxWildcard = maxWildcard;
    }

    @Option(description = "Blacklist (read names)", longName = "blacklist", defaultToNull=true)
    public void setBlacklist(String blacklist) {
        this.blacklist = blacklist;
    }

    @Option(description = "Whitelist (read names)", longName = "whitelist", defaultToNull=true)
    public void setWhitelist(String whitelist) {
        this.whitelist = whitelist;
    }
    
    @Override
    public void exec() throws IOException, NGSUtilsException {
        if (whitelist != null && blacklist != null) {
            throw new NGSUtilsException("You can not specify both a whitelist and a blacklist!");
        }
        
        if (verbose) {
            System.err.println("Filtering file:" + reader.getFilename());
        }
        final List<FilterIterable> iters = new ArrayList<FilterIterable>();
        Iterable<FastqRead> parent = reader;

        if (maxWildcard > 0) {
            parent = new FilterIterable(new WildcardFilter(parent, verbose, maxWildcard));
            iters.add((FilterIterable) parent);
        }
        
        if (prefixTrimLength > 0) {
            parent = new FilterIterable(new PrefixFilter(parent, verbose,
                    prefixTrimLength));
            iters.add((FilterIterable) parent);
        }

        if (suffixQuality != null) {
            parent = new FilterIterable(new SuffixQualFilter(parent, verbose,
                    suffixQuality.charAt(0)));
            iters.add((FilterIterable) parent);
        }

        if (trimSeq != null) {
            parent = new FilterIterable(new SeqTrimFilter(parent, verbose,
                    trimSeq, trimMinOverlap, trimMinPctMatch));
            iters.add((FilterIterable) parent);            
        }
        
        if (minimumSize > 0) {
            parent = new FilterIterable(new SizeFilter(parent, verbose,
                    minimumSize));
            iters.add((FilterIterable) parent);
        }

        if (paired) {
            parent = new FilterIterable(new PairedFilter(parent, verbose));
            iters.add((FilterIterable) parent);
        }

        if (whitelist!=null) {
            parent = new FilterIterable(new WhitelistFilter(parent, verbose, whitelist));
            iters.add((FilterIterable) parent);
        }

        if (blacklist!=null) {
            parent = new FilterIterable(new BlacklistFilter(parent, verbose, blacklist));
            iters.add((FilterIterable) parent);
        }

        for (final FastqRead read : parent) {
            if (read != null) {
                read.write(out);
            }
        }

        if (verbose) {
            System.err.println("Filter\tTotal\tAltered\tRemoved");
            for (final FilterIterable iter : iters) {
                System.err.println(iter.getName() + "\t" + iter.getTotal()
                        + "\t" + iter.getAltered() + "\t" + iter.getRemoved());
            }
        }
        close();
    }
}
