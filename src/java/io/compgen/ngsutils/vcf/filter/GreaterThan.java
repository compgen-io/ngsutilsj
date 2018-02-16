package io.compgen.ngsutils.vcf.filter;

@MathFilter(id="gt", desc="greater than")
public class GreaterThan extends LessThan {

	public GreaterThan(String key, double thres, String sampleName, String alleleName) {
		super(key, thres, sampleName, alleleName, GreaterThan.class);
	}

	protected boolean operation(double d) {
		return d > thres;	
	}
}
