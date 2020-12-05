package io.compgen.ngsutils.vcf.annotate;

import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public abstract class AbstractBasicAnnotator implements VCFAnnotator {

	private VCFAnnotator parent = null;
	protected VCFHeader header = null;
    protected String altChrom = null;
    protected String altPos = null;
    protected String endPosKey = null;
	
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

    public void setEndPos(String key) throws VCFAnnotatorException {
        this.endPosKey=key;
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
        	if (rec.getRef().length()==1) {
        		return rec.getPos();
        	} else {
        		// this is a deletion, so the variant is actually the next base
        		return rec.getPos()+1; 
        	}
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

    protected int getEndPos(VCFRecord rec) throws VCFAnnotatorMissingAltException {
        if (endPosKey == null) {
        	
        	if (rec.getRef().length()==1) {
        		return rec.getPos();
        	}
        	
        	// if this is a deletion, the "endpos" is further down the chrom.
        	//
        	// ref = CATCGA
        	// alt = C
        	// length of variant = 5
        	
            return rec.getPos() - 1 + rec.getRef().length();
        }
        
        VCFAttributeValue pos = rec.getInfo().get(endPosKey);
        
        if (pos == null) {
            throw new VCFAnnotatorMissingAltException("Missing key: "+endPosKey);
        }
        try {
            return pos.asInt();
        } catch (NumberFormatException e) {
            throw new VCFAnnotatorMissingAltException(e);
        }
    }

}
