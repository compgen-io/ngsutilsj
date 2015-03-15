package io.compgen.ngsutils.bam;

public enum Strand {
    NONE ("*"),
    PLUS ("+"),
    MINUS ("-");
    
    private final String val;
    
    Strand(String val) {
        this.val = val;
    }
    
    public String toString() {
        return val;
    }

    public static Strand parse(String val) {
        if (val.equals("+")) {
            return PLUS;
        } else if (val.equals("-")) {
            return MINUS;
        }
        return NONE;
    }
}
