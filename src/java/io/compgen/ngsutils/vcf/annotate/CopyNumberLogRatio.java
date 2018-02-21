package io.compgen.ngsutils.vcf.annotate;

import io.compgen.ngsutils.support.stats.StatUtils;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class CopyNumberLogRatio extends AbstractBasicAnnotator {
	final protected String germlineSampleID;
	final protected String somaticSampleID; 
	
	final protected long germlineCount;
	final protected long somaticCount;
	
	final protected double germTotalLog;
	final protected double somTotalLog;
	
	protected int germlineIdx = -1;
	protected int somaticIdx = -1;
	
	public CopyNumberLogRatio(String germlineSampleID, String somaticSampleID) {
		this(germlineSampleID, somaticSampleID, -1, -1);
	}
	
	public CopyNumberLogRatio(String germlineSampleID, String somaticSampleID, long germlineCount, long somaticCount) {
		this.germlineSampleID = germlineSampleID;
		this.somaticSampleID = somaticSampleID;
		this.germlineCount = germlineCount;
		this.somaticCount = somaticCount;
		
		if (germlineCount < 0 || somaticCount < 0) {
			germTotalLog = Double.NaN;
			somTotalLog = Double.NaN;
		} else {
			germTotalLog = StatUtils.log2(germlineCount);
			somTotalLog = StatUtils.log2(somaticCount);
		}
		
	}
	
	public VCFAnnotationDef getAnnotationType() throws VCFAnnotatorException {
		try {
			if (germlineCount>0 && somaticCount>0) {
				return VCFAnnotationDef.info("CG_CNLR", "1", "Float", "Copy number (log2-ratio); Germline-total:"+germlineCount+", Somatic-total:"+somaticCount);
			} else {
				return VCFAnnotationDef.info("CG_CNLR", "1", "Float", "Copy number (log2-ratio)");
			}
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}

	@Override
	public void setHeader(VCFHeader header) throws VCFAnnotatorException {
		VCFAnnotationDef adDef = header.getFormatDef("AD");
		if (adDef == null || !adDef.number.equals("R") || !adDef.type.equals("Integer")) {
			throw new VCFAnnotatorException("\"AD\" FORMAT annotation missing!");
		}
		header.addInfo(getAnnotationType());
		
		germlineIdx = header.getSamplePosByName(germlineSampleID);
		somaticIdx = header.getSamplePosByName(somaticSampleID);

		if (germlineIdx == -1 ) {
			throw new VCFAnnotatorException("Can't find germline sample: "+germlineSampleID);
		}
		if (somaticIdx == -1 ) {
			throw new VCFAnnotatorException("Can't find somatic sample: "+somaticSampleID);
		}
	}

	@Override
	public void annotate(VCFRecord record) throws VCFAnnotatorException {		
		try { 
			double germAcc = record.getSampleAttributes().get(germlineIdx).get("AD").asDouble("sum");
			double somAcc = record.getSampleAttributes().get(somaticIdx).get("AD").asDouble("sum");
			
			double germLog = StatUtils.log2(germAcc);
			double somLog = StatUtils.log2(somAcc);
			
			if (!Double.isNaN(germTotalLog)) {
				record.getInfo().put("CG_CNLR", new VCFAttributeValue(""+FisherStrandBias.round((somLog-somTotalLog) - (germLog-germTotalLog), 6)));
			} else {
				record.getInfo().put("CG_CNLR", new VCFAttributeValue(""+FisherStrandBias.round(somLog - germLog, 6)));
			}
		} catch (NumberFormatException e) {
			throw new VCFAnnotatorException(e);
		} catch (VCFAttributeException e) {
			throw new VCFAnnotatorException(e);
		}
	}
}