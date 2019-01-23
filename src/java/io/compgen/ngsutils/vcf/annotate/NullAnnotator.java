package io.compgen.ngsutils.vcf.annotate;

import java.nio.channels.FileChannel;
import java.util.Iterator;

import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;

public class NullAnnotator implements VCFAnnotator {

    final private Iterator<VCFRecord> it;
    final private FileChannel channel;
	final private boolean onlyPassing;

	public NullAnnotator(VCFReader reader, boolean onlyPassing, boolean showProgress) {
		//this.it = reader.iterator();
		this.channel = reader.getChannel();
        this.onlyPassing = onlyPassing;

        if (showProgress) {
            this.it = ProgressUtils.getIterator(reader.getFilename(), reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<VCFRecord>() {
                public String msg(VCFRecord current) {
                    return current.getChrom()+":"+current.getPos();
                }}, new CloseableFinalizer<VCFRecord>());
        } else {
            this.it = reader.iterator();
        }

	}

	@Override
	public void setHeader(VCFHeader header) throws VCFAnnotatorException {
	}

	@Override
	public void setParent(VCFAnnotator parent) throws VCFAnnotatorException {
	}

	@Override
	public VCFRecord next() throws VCFAnnotatorException {
		while (it.hasNext()) {
			VCFRecord rec = it.next();
			if (!rec.isFiltered() || !onlyPassing) {
				return rec;
			}
		}
        return null;
	}

	@Override
	public void close() throws VCFAnnotatorException {
	}

    @Override
    public void setAltChrom(String key) throws VCFAnnotatorException {
    }

    @Override
    public void setAltPos(String key) throws VCFAnnotatorException {
    }
}