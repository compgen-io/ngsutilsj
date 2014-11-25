package org.ngsutils.support;


public class NaturalToken implements Comparable<NaturalToken> {
    public final boolean digits;
    public final int intVal;
    public final String strVal;

    public NaturalToken(int val) {
        digits = true;
        intVal = val;
        strVal = null;
    }

    public NaturalToken(String val) {
        digits = false;
        intVal = -1;
        strVal = val;
    }

    @Override
    public int compareTo(NaturalToken o) {
        if (o == null) {
            return 1;
        }
        if (digits && !o.digits) {
            return -1;
        }
        if (!digits && o.digits) {
            return 1;
        }

        if (digits) {
            return intVal - o.intVal;
        }

        return strVal.compareTo(o.strVal);
    }

    @Override
    public String toString() {
        if (digits) {
            return Integer.toString(intVal);
        }
        return strVal;
    }
}
