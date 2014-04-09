package org.ngsutils.fastq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.cli.Command;
import org.ngsutils.cli.NGSExec;
import org.ngsutils.fastq.filter.FilterIterable;
import org.ngsutils.fastq.filter.PrefixFilter;
import org.ngsutils.fastq.filter.SizeFilter;
import org.ngsutils.fastq.filter.SuffixQualFilter;
import org.ngsutils.fastq.filter.WildcardFilter;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application = "ngsutilsj fastq-filter")
@Command(name = "fastq-filter", desc = "Filters reads from a FASTQ file.", cat = "fastq")
public class FastqFilter implements NGSExec {
    private FastqReader reader;
    private boolean verbose = false;
    private boolean paired = false;
    private String suffixQuality = null;
    private int prefixTrimLength = -1;
    private int minimumSize = -1;
    private int maxWildcard = -1;

    public FastqFilter() {
    }

    @Option(helpRequest = true, description = "Display help", shortName = "h")
    public void setHelp(boolean help) {
    }

    @Unparsed(name = "FILE")
    public void setFilename(String filename) throws IOException {
        reader = new FastqReader(filename);
    }

    @Option(description = "Paired filter (Default: not used)", longName = "paired", defaultValue = "false")
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

    @Option(description = "Verbose output", shortName = "v")
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void filter() throws IOException, NGSUtilsException {
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

        if (minimumSize > 0) {
            parent = new FilterIterable(new SizeFilter(parent, verbose,
                    minimumSize));
            iters.add((FilterIterable) parent);
        }

        for (final FastqRead read : parent) {
            if (read != null) {
                read.write(System.out);
            }
        }

        if (verbose) {
            System.err.println("Filter\tTotal\tAltered\tRemoved");
            for (final FilterIterable iter : iters) {
                System.err.println(iter.getName() + "\t" + iter.getTotal()
                        + "\t" + iter.getAltered() + "\t" + iter.getRemoved());
            }
        }
    }

    @Override
    public void exec() throws Exception {
        filter();
    }
}
