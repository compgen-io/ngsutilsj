package io.compgen.ngsutils.annotation;

public enum GenicRegion {
    // Note: the order is the priority for assigning annotations!
    
    // In sense orientation
    JUNCTION ("Junction", "junction",                      true, true, true, true),
    CODING ("Coding", "coding_exon",                       true, true, true, true),
    UTR5 ("5'UTR", "5_utr",                                true, true, false, true),
    UTR3 ("3'UTR", "3_utr",                                true, true, false, true),
    
    NC_JUNCTION ("Non-coding junction",  "nc_junction",    true, true, false, true),
    NC_EXON ("Non-coding exon", "nc_exon",                 true, true, false, true),

    CODING_INTRON ("Coding intron", "coding_intron",       true, false, true, true),
    UTR5_INTRON ("5'UTR intron", "5_utr_intron",           true, false, false, true),
    UTR3_INTRON ("3'UTR intron", "3_utr_intron",           true, false, false, true),
    NC_INTRON ("Non-coding intron", "nc_intron",           true, false, false, true),

    INTERGENIC ("Intergenic", "intergenic",                false, false, false, false),
    MITOCHONDRIAL ("Mitochondrial", "mitochondrial",       false, false, false, false),

    // In reverse/anti-sense orientation
    JUNCTION_ANTI ("Junction", "anti_junction",            true, true, true, false),
    CODING_ANTI ("Coding", "anti_coding_exon",             true, true, true, false),
    UTR5_ANTI ("5'UTR", "anti_5_utr",                      true, true, false, false),
    UTR3_ANTI ("3'UTR", "anti_3_utr",                      true, true, false, false),
    NC_JUNCTION_ANTI ("Non-coding junction", "anti_nc_junction", true, true, false, false),
    NC_EXON_ANTI ("Non-coding exon", "anti_nc_exon",       true, true, false, false),
    CODING_INTRON_ANTI ("Coding intron", "anti_coding_intron",   true, false, true, false),
    UTR5_INTRON_ANTI ("5'UTR intron", "anti_5_utr_intron",      true, false, false, false),
    UTR3_INTRON_ANTI ("3'UTR intron", "anti_3_utr_intron",      true, false, false, false),
    NC_INTRON_ANTI ("Non-coding intron", "anti_nc_intron",   true, false, false, false);

    public final String name;
    public final String code;
    public final boolean isGene;
    public final boolean isExon;
    public final boolean isCoding;
    public final boolean isSense;
    
    GenicRegion(String name, String code, boolean isGene, boolean isExon, boolean isCoding, boolean isSense) {
        this.name = name;
        this.code = code;
        this.isGene = isGene;
        this.isExon = isExon;
        this.isCoding = isCoding;
        this.isSense = isSense;
    }
    
    public String getDescription() {
        return name + ((!isGene || isSense)?"":" (anti-sense)");
    }
}
