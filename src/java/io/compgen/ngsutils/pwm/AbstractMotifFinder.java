package io.compgen.ngsutils.pwm;

import java.util.Arrays;
import java.util.Random;

import io.compgen.ngsutils.support.SeqUtils;

public abstract class AbstractMotifFinder {

	private static final int MAX_PERMUTATION_COUNT = 1000000;
	protected static final double[] backgroundRates = new double[] {0.3, 0.2, 0.2, 0.3};
	private double[] probPermutations = null;
	protected int total;
	private final double LOG2_FACTOR = Math.log(2);

	public AbstractMotifFinder() {
		super();
	}

	public double log2(double val) {
		return Math.log(val) / LOG2_FACTOR;
	}

	public abstract int getLength();

	public abstract double calcScore(String seq) throws Exception;
	
	public double calcPvalue(double score) {
		if (probPermutations == null) {
			buildPermutations();
		}
		
		int i=0;
		while (score > probPermutations[i] && i < probPermutations.length) {
			i++;
		}
		
		return ((double)(probPermutations.length - i)) / probPermutations.length ;
		
	}

	private void buildPermutations() {
		// Because we are generating this for p-values, let's try to be consistent
		Random rand = new Random(123);
		probPermutations = new double[MAX_PERMUTATION_COUNT];
		for (int i=0; i< MAX_PERMUTATION_COUNT; i++) {
			try {
				probPermutations[i] = calcScore(SeqUtils.generateRandomSeq(getLength(), rand));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		Arrays.sort(probPermutations);
	}

	
}