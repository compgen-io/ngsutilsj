package io.compgen.ngsutils.varcall;

import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.distribution.PoissonDistribution;

@Deprecated
public class VariantCall {
    public final CallCount major;
    public final CallCount minor;
    public final CallCount back;
    public final double pvalueCall;
    public final double pvalueStrand;
    public final double freqCall;
    public final double freqStrand;

    protected VariantCall(CallCount major, CallCount minor, CallCount back, double pvalueCall, double pvalueStrand, double freqCall, double freqStrand) {
        this.major = major;
        this.minor = minor;
        this.back = back;

        this.pvalueCall = pvalueCall;
        this.pvalueStrand = pvalueStrand;

        this.freqCall = freqCall;
        this.freqStrand = freqStrand;
    }

    protected VariantCall(CallCount major) {
        this.major = major;
        this.minor = null;
        this.back = null;

        this.pvalueCall = 0;
        this.pvalueStrand = 0;

        this.freqCall = 0;
        this.freqStrand = 0;
    }

    public String toString() {
        String out = "";
        if (major != null) {
            out += major;
        }
        if (minor != null) {
            out += "/" + minor;
        }
        if (back != null) {
            out += " [" + back +"]";
        }
        return out;
    }
    
    /**
     * Determine if the basecalls for a particular base represent a variant. This will only make one call for each position, so multi-variate calls will be missed.
     * Also, the p-value limits are the thresholds for rejecting the null-hypothesis. For this function, the null hypothesis is that the position is a heterozygous
     * variant (with an expected minor allele frequency) and that the strand-frequency is 0.5. If one of the statistical tests is *less* than these thresholds, the
     * position is assumed to be homozygous for the major allele.
     * 
     * The statistical test used for both variant detection and stranded-ness is the Poisson test (cdf).
     */
    public static VariantCall callVariants(List<CallCount> callCounts, boolean backgroundCorrect, double expectedMinorFreq, double expectedStrandFreq, boolean debug) {
        Collections.sort(callCounts);
        
        CallCount major = null;
        CallCount minor = null;
        CallCount back = null;
        
        for (CallCount cc: callCounts) {
            back = minor;
            minor = major;
            major = cc;
        }

        if (debug) {
            System.out.println(major);
            System.out.println(minor);
            System.out.println(back);
        }
        
        /*
         * First, the major and minor counts are found by sorting the list or prospective calls.
         * Basecalls as well as indels can be assigned as a major or minor call. 
         * 
         * A variant is called iff the Poisson cumulative Probability (minor, floor(minor+major/2)) value is greater than limitCall
         * Next, a strand-specificity score is calculated for the minor call,  
         */
        
        if (minor == null) {
            return new VariantCall(major);
        }
        
        int majorCount = major.getCount();
        int minorCount = minor.getCount();
        
        if (backgroundCorrect && back != null) {
            majorCount -= back.getCount();
            minorCount -= back.getCount();
        }
        
        if (majorCount <= 0) {
            return null;
        } else if (minorCount <=0 || (majorCount + minorCount) <=0) {
            return new VariantCall(major);
        }

        double probCall = new PoissonDistribution(Math.floor((minorCount+majorCount) * expectedMinorFreq)).cumulativeProbability(minorCount);
        double probStrand = new PoissonDistribution(Math.floor((minor.getCount() * expectedStrandFreq))).cumulativeProbability(minor.getMinorStrandCount());

        return new VariantCall(major, minor, back, probCall, probStrand, (double) minorCount / (majorCount+minorCount), (double) minor.getMinorStrandCount() / minor.getCount());
    }

    public static VariantCall callVariants(List<CallCount> callCounts) {
        return callVariants(callCounts, true, 0.5, 0.5, false);
    }
    public static VariantCall callVariants(List<CallCount> callCounts, boolean backgroundCorrect, boolean debug) {
        return callVariants(callCounts, backgroundCorrect, 0.5, 0.5, debug);
        
    }
    public static VariantCall callVariants(List<CallCount> callCounts, boolean backgroundCorrect) {
        return callVariants(callCounts, backgroundCorrect, 0.5, 0.5, false);
    }
}
