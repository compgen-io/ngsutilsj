package io.compgen.ngsutils.support;

import java.util.List;

public class ListUtils {
    public interface PairExec<T> {
        public T func(T one, T two);
    }
    public interface SingleExec<T> {
        public T func(T one);
    }

    public static int[] intListToArray(List<Integer> vals) {
        int[] out = new int[vals.size()];
        for (int i=0; i<vals.size(); i++) {
            out[i] = vals.get(i);
        }
        return out;
    }
    public static double[] doubleListToArray(List<Double> vals) {
        double[] out = new double[vals.size()];
        for (int i=0; i<vals.size(); i++) {
            out[i] = vals.get(i);
        }
        return out;
    }
    
    
    public static int[] intArrayPairMap(int[] one, int[] two, PairExec<Integer> func) {
        int[] out = new int[one.length];
        for (int i=0; i<one.length; i++) {
            out[i] = func.func(one[i], two[i]);
        }
        return out;
    }

    public static double[] doubleArrayPairMap(double[] one, double[] two, PairExec<Double> func) {
        double[] out = new double[one.length];
        for (int i=0; i<one.length; i++) {
            out[i] = func.func(one[i], two[i]);
        }
        return out;
    }
    public static double[] intToDoubleArray(int[] vals) {
        double[] out = new double[vals.length];
        for (int i=0; i<vals.length; i++) {
            out[i] = vals[i];
        }
        return out;
    }
}
