package io.compgen.ngsutils.support.stats;

/**
 * See: http://www.biostathandbook.com/fishers.html for details on how to calculate this
 * @author mbreese
 *
 */
public class FisherExact {
	private double[] logVals = null;
	
	public FisherExact() {
	}

	private void recalculateCache(int n) {
		if (logVals != null && logVals.length > n) {
			return;
		}

		int start;
		if (logVals == null) {
			start = 1;

			logVals = new double[n+1];
			logVals[0] = 0.0;
		} else {
			start = logVals.length;

			double[] tmp = new double[n+1];
			for (int i=0; i<logVals.length; i++) {
				tmp[i] = logVals[i];
			}
			logVals = tmp;
		}

		for (int i=start; i<=n; i++) {
			logVals[i] = logVals[i-1]+Math.log(i);
		}
	}
	
	/**
	 * Calculate the exact p-value for this 2x2 contingency table
	 *
	 *           Y         N
	 * 	    +-------------------+
	 * 	Y   |    A    |    B    |
	 *      +-------------------+
	 * 	N   |    C    |    D    |
	 * 	    +-------------------+
	 * 
	 * @param a
	 * @param b
	 * @param c
	 * @param d
	 */
	public final double calcPvalue(int a, int b, int c, int d) {
		int n = a + b + c + d;
		recalculateCache(n);
		return Math.exp((logVals[a+b] + logVals[c+d] + logVals[a+c] + logVals[b+d]) - (logVals[a]+logVals[b]+logVals[c]+logVals[d]+logVals[n]));
	}


	public final double calcTwoTailedPvalue(int a, int b, int c, int d) {
		double exact = calcPvalue(a, b, c, d);
		double p = exact;
		
		p += innerLeftTail(a, b, c, d, exact);
		p += innerRightTail(a, b, c, d, exact);
		
		return p;
	}

	/**
	 * Calculate the left-single tailed p-value for this 2x2 contingency table
	 * (move one value from A to B and C to D) (effectively a--, b++, c++, d--)
	 * 
	 * The left-tail looks for values "less" extreme than the given table.
	 *  
	 *          cond2 Y         N
	 * 	         +-------------------+
	 * cond1 Y   |    A    |    B    |
	 *           +-------------------+
	 * cond1 N   |    C    |    D    |
	 * 	         +-------------------+
	 * 
	 * @param a
	 * @param b
	 * @param c
	 * @param d
	 * @returns Cumulative sum of pvalues for tables less than or equal to this one
	 */

	public final double calcLeftTailedPvalue(int a, int b, int c, int d) {
		double p = calcPvalue(a, b, c, d);
		return p + innerLeftTail(a, b, c, d, p);
	}
	
	/**
	 * Calculate the right-single tailed p-value for this 2x2 contingency table
	 * (move one value from B to A and D to C) (effectively a++, b--, c--, d++)
	 * 
	 * The right-tail looks for values "more" extreme than the given table.
	 *  
	 *          cond2 Y         N
	 * 	         +-------------------+
	 * cond1 Y   |    A    |    B    |
	 *           +-------------------+
	 * cond1 N   |    C    |    D    |
	 * 	         +-------------------+
	 * 
	 * @param a
	 * @param b
	 * @param c
	 * @param d
	 * @returns Cumulative sum of pvalues for more-extreme tables than this one (but only with a p-value less than the exact p-value)
	 */
	public final double calcRightTailedPvalue(int a, int b, int c, int d) {
		double p = calcPvalue(a, b, c, d);
		return innerRightTail(a, b, c, d, p);
	}
	
	protected final double innerLeftTail(int a, int b, int c, int d, double thres) {
		double acc = 0.0;
		
		while (a > 0 && d > 0) {
			double p = calcPvalue(--a, ++b, ++c, --d);
//			System.err.println("left: a="+a+", b="+b+", c="+c+", d="+d+", p="+p);
			if (p < thres) {
				acc += p;
			}
		}
		
		return acc;
	}

	protected final double innerRightTail(int a, int b, int c, int d, double thres) {
		double acc = 0.0;
		
		while (b > 0 && c > 0) {
			double p = calcPvalue(++a, --b, --c, ++d);
//			System.err.println("right: a="+a+", b="+b+", c="+c+", d="+d+", p="+p);
			if (p < thres) {
				acc += p;
			}
		}
		
		return acc;
	}

	/**
	 * Calculate p-value based on an int[], where the values are:
	 * 
	 * int[] vals = int[] { a, b, c, d }
	 * 
	 * @param contingencyTable
	 * @return
	 */
	public final double calcPvalue(int[] contingencyTable) {
		return calcPvalue(contingencyTable[0], contingencyTable[1], contingencyTable[2], contingencyTable[3]);
	}

	public final double calcTwoTailedPvalue(int[] contingencyTable) {
		return calcTwoTailedPvalue(contingencyTable[0], contingencyTable[1], contingencyTable[2], contingencyTable[3]);
	}
	public final double calcRightTailedPvalue(int[] contingencyTable) {
		return calcRightTailedPvalue(contingencyTable[0], contingencyTable[1], contingencyTable[2], contingencyTable[3]);
	}
	public final double calcLeftTailedPvalue(int[] contingencyTable) {
		return calcLeftTailedPvalue(contingencyTable[0], contingencyTable[1], contingencyTable[2], contingencyTable[3]);
	}

}
