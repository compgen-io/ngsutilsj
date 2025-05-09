package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFFilterDef;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public abstract class VCFAbstractFilter implements VCFFilter {

	final protected String id;
	final protected String desc;
	protected VCFHeader header=null;
	protected VCFFilterDef def=null;
	
	public VCFAbstractFilter(String id, String desc) {
		this.id = id;
		this.desc = desc;
		
		def = VCFFilterDef.build(id, desc);
	}
	
	@Override
	public void setHeader(VCFHeader header) throws VCFFilterException {
		this.header=header;
		header.addFilter(getDefinition());

	}
	
	public VCFFilterDef getDefinition() {
		return def;
	}

	@Override
	public void filter(VCFRecord record) throws VCFFilterException {
		if (innerFilter(record)) {
			record.addFilter(getID());
		}
	}
	
	/**
	 * If this returns true, then the FILTER field is added
	 * @param record
	 * @return
	 * @throws VCFFilterException
	 */
	protected abstract boolean innerFilter(VCFRecord record) throws VCFFilterException;
	
	@Override
	public String getID() {
		return id;
	}

	@Override
	public String getDescription() {
		return desc;
	}

	public void close() {}
}
