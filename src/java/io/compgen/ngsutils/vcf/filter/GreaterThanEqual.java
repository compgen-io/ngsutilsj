package io.compgen.ngsutils.vcf.filter;

@MathFilter(id="gte", desc="greater than or equal")
public class GreaterThanEqual extends LessThan {

	public GreaterThanEqual(String key, double thres, String sampleName, String alleleName) {
		super(key, thres, sampleName, alleleName, GreaterThanEqual.class);
	}
	
	protected boolean operation(double d) {
		return d >= thres;	
	}
}
