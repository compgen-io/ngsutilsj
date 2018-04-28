package io.compgen.ngsutils.vcf.annotate;

import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class VariantDistance implements VCFAnnotator {
	protected VCFAnnotator parent = null;
	protected VCFRecord last = null;
	protected VCFRecord cur = null;
	protected long lastDist = -1;
	
	private boolean done = false;
	
	public VCFAnnotationDef getAnnotationType() throws VCFAnnotatorException {
		try {
			return VCFAnnotationDef.info("CG_VARDIST", "1", "Integer", "Distance to the nearest variant (absolute value)");
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}

	@Override
	public void setHeader(VCFHeader header) throws VCFAnnotatorException {
		header.addFormat(getAnnotationType());
	}
	
	@Override
	public VCFRecord next() throws VCFAnnotatorException {
		if (done) {
			return null;
		}
		if (cur == null) {
			cur = parent.next();
			if (cur == null) {
				return null;
			}
			if (last != null) {
				if (!cur.getChrom().equals(last.getChrom())) {
					annotate(last, lastDist);
					lastDist = -1;
					return last;
				}
			}
		}

		if (last == null) {
			lastDist = -1;
		}
		
		last = cur;
		cur = parent.next();
		if (cur == null) {
			done = true;
		}
		if (cur == null || !cur.getChrom().equals(last.getChrom())) {
			annotate(last, lastDist);
			lastDist = -1;
			return last;
		}

		long dist = calcDist(last, cur);

		if (lastDist > -1) {
			annotate(last, Math.min(lastDist, dist));
		} else {
			annotate(last, dist);
		}

		lastDist = dist;
		return last;
	}

	private void annotate(VCFRecord record, long l) {
		record.getInfo().put("CG_VARDIST", new VCFAttributeValue(""+l));
	}

	private long calcDist(VCFRecord one, VCFRecord two) {
		return two.getPos() - one.getPos();
	}

	@Override
	public void setParent(VCFAnnotator parent) throws VCFAnnotatorException {
		this.parent = parent;		
	}
	@Override
	public void close() throws VCFAnnotatorException {
	}

    @Override
    public void setAltChrom(String key) throws VCFAnnotatorException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setAltPos(String key) throws VCFAnnotatorException {
        // TODO Auto-generated method stub
        
    }
}