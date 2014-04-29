package org.ngsutils.bam;

public enum Orientation {
    UNSTRANDED ("unstranded"),
    FR ("FR"),
    RF ("RF");
    
    private final String val;
    
    Orientation(String val) {
        this.val = val;
    }
    
    public String toString() {
        return val;
    }

    public static Orientation parse(String val) {
        if (val.equals("FR")) {
            return FR;
        } else if (val.equals("RF")) {
            return RF;
        }
        return UNSTRANDED;
    }
}
