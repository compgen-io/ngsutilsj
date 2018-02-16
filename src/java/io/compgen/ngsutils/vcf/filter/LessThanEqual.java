package io.compgen.ngsutils.vcf.filter;

@MathFilter(id="lte", desc="less than or equal")
public class LessThanEqual extends LessThan {

	public LessThanEqual(String key, double thres, String sampleName, String alleleName) {
		super(key, thres, sampleName, alleleName, LessThanEqual.class);
	}

	protected boolean operation(double d) {
		return d <= thres;	
	}
}
