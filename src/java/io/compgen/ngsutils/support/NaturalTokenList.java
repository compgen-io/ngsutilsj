package io.compgen.ngsutils.support;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NaturalTokenList implements Comparable<NaturalTokenList>, Iterable<NaturalToken> {

    private class CompareVal {
        private int val = 0;
    }

    private final List<NaturalToken> tokens = new ArrayList<NaturalToken>();

    public void addToken(NaturalToken token) {
        tokens.add(token);
    }

    @Override
    public int compareTo(NaturalTokenList o) {
        final CompareVal compareVal = new CompareVal();
        IterUtils.zip(this, o, new IterUtils.Each<NaturalToken, NaturalToken>() {
            @Override
            public void each(NaturalToken foo, NaturalToken bar) {
                if (compareVal.val == 0) {
                    compareVal.val = foo.compareTo(bar);
                }
            }
        }, true);
        return compareVal.val;
    }

    @Override
    public String toString() {
        String s = "";
        for (final NaturalToken token : tokens) {
            s += token.toString();
        }
        return s;
    }

    @Override
    public Iterator<NaturalToken> iterator() {
        return tokens.iterator();
    }

    public static NaturalTokenList parseString(String str) {
        NaturalTokenList tokens = new NaturalTokenList();
        String tmp = "";
        boolean digits = false;
        for (int i = 0; i < str.length(); i++) {
            if ("0123456789".indexOf(str.charAt(i)) > -1) {
                if (!digits) {
                    if (!tmp.equals("")) {
                        tokens.addToken(new NaturalToken(tmp));
                        tmp = "";
                    }
                }
                digits = true;
                tmp += str.charAt(i);
            } else {
                if (digits) {
                    tokens.addToken(new NaturalToken(Integer.parseInt(tmp)));
                    tmp = "";
                }
                digits = false;
                tmp += str.charAt(i);
            }
        }
        if (!tmp.equals("")) {
            if (digits) {
                tokens.addToken(new NaturalToken(Integer.parseInt(tmp)));
            } else {
                tokens.addToken(new NaturalToken(tmp));
            }
        }
        return tokens;
    }

}
