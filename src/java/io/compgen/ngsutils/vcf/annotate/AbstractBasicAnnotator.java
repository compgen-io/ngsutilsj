package io.compgen.ngsutils.vcf.annotate;

import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public abstract class AbstractBasicAnnotator implements VCFAnnotator {

	private VCFAnnotator parent = null;
	protected VCFHeader header = null;
    protected String altChrom = null;
    protected String altPos = null;
	
	protected abstract void annotate(VCFRecord record) throws VCFAnnotatorException;
	public abstract void setHeaderInner(VCFHeader header) throws VCFAnnotatorException;

    public void setHeader(VCFHeader header) throws VCFAnnotatorException {
        this.header = header;
        setHeaderInner(header);
    }

	
	@Override
	public void setParent(VCFAnnotator parent) throws VCFAnnotatorException {
		this.parent = parent;
	}

	@Override
	public void close() throws VCFAnnotatorException {
	}

	@Override
	public VCFRecord next() throws VCFAnnotatorException {
		if (parent == null) {
			throw new VCFAnnotatorException("Missing parent in chain!");
		}
		VCFRecord next = parent.next();
		if (next != null) {
			annotate(next);
		}
		return next;
	}

    public void setAltChrom(String key) throws VCFAnnotatorException {
        this.altChrom=key;
        if (header != null) {
            if (header.getInfoDef(key) == null) {
                throw new VCFAnnotatorException("Missing INFO field: "+key);
            }
        }
    }
    public void setAltPos(String key) throws VCFAnnotatorException {
        this.altPos=key;
    }

    protected String getChrom(VCFRecord rec) throws VCFAnnotatorMissingAltException {
        if (altChrom == null) {
            return rec.getChrom();
        }

        VCFAttributeValue chrom = rec.getInfo().get(altChrom);
        
        if (chrom == null) {
            throw new VCFAnnotatorMissingAltException("Missing key: "+altChrom);
        }
        
        return chrom.toString();
    }

    protected int getPos(VCFRecord rec) throws VCFAnnotatorMissingAltException {
        if (altPos == null) {
            return rec.getPos();
        }
        VCFAttributeValue pos = rec.getInfo().get(altPos);
        
        if (pos == null) {
            throw new VCFAnnotatorMissingAltException("Missing key: "+altPos);
        }
        try {
            return pos.asInt();
        } catch (NumberFormatException e) {
            throw new VCFAnnotatorMissingAltException(e);
        }
    }
    
}
