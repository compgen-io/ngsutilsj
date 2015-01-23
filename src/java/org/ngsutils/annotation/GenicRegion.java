package org.ngsutils.annotation;

public enum GenicRegion {
    // Note: the order is the priority for assigning annotations!
    
    // In sense orientation
    CODING ("Coding",                       true, true, true, true),
    JUNCTION ("Junction",                   true, true, true, true),
    UTR5 ("5'UTR",                          true, true, false, true),
    UTR3 ("3'UTR",                          true, true, false, true),
    
    NC_EXON ("Non-coding exon",             true, true, false, true),
    NC_JUNCTION ("Non-coding junction",     true, true, false, true),

    CODING_INTRON ("Coding intron",         true, false, true, true),
    UTR5_INTRON ("5'UTR intron",            true, false, false, true),
    UTR3_INTRON ("3'UTR intron",            true, false, false, true),
    NC_INTRON ("Non-coding intron",         true, false, false, true),

    INTERGENIC ("Intergenic",               false, false, false, false),
    MITOCHONDRIAL ("Mitochondrial",         false, false, false, false),

    // In reverse/anti-sense orientation
    CODING_ANTI ("Coding",                  true, true, true, false),
    JUNCTION_ANTI ("Junction",              true, true, true, false),
    UTR5_ANTI ("5'UTR",                     true, true, false, false),
    UTR3_ANTI ("3'UTR",                     true, true, false, false),
    NC_EXON_ANTI ("Non-coding exon",        true, true, false, false),
    NC_JUNCTION_ANTI ("Non-coding junction",true, true, false, false),
    CODING_INTRON_ANTI ("Coding intron",    true, false, true, false),
    UTR5_INTRON_ANTI ("5'UTR intron",       true, false, false, false),
    UTR3_INTRON_ANTI ("3'UTR intron",       true, false, false, false),
    NC_INTRON_ANTI ("Non-coding intron",    true, false, false, false);

    public final String name;
    public final boolean isGene;
    public final boolean isExon;
    public final boolean isCoding;
    public final boolean isSense;
    
    GenicRegion(String name, boolean isGene, boolean isExon, boolean isCoding, boolean isSense) {
        this.name = name;
        this.isGene = isGene;
        this.isExon = isExon;
        this.isCoding = isCoding;
        this.isSense = isSense;
    }
    
    public String getDescription() {
        return name + ((!isGene || isSense)?"":" (anti-sense)");
    }
}
