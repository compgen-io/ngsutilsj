#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DATA_DIR="$ROOT_DIR/test-data/data"

mkdir -p "$DATA_DIR"

# Reference FASTA
cat > "$DATA_DIR/ref.fa" <<'FASTA'
>chr1
ACGTACGTACGTACGTACGTACGTACGTACGT
>chr2
TTGGAATTCCGGAA
FASTA

# FASTQ (simple)
cat > "$DATA_DIR/reads.fastq" <<'FASTQ'
@read1
ACGTACGTACGT
+
FFFFFFFFFFFF
@read2
TTGGAATTCCGG
+
FFFFFFFFFFFF
FASTQ

# FASTQ with Illumina 1.8+ style headers
cat > "$DATA_DIR/reads_illumina.fastq" <<'FASTQ'
@INST:1:FCID:1:1101:1000:1000 1:N:0:ACGT
ACGTACGT
+
FFFFFFFF
@INST:1:FCID:1:1101:1000:1001 1:N:0:ACGT
TTGGAATT
+
FFFFFFFF
FASTQ

# FASTA (reads)
cat > "$DATA_DIR/reads.fa" <<'FASTA'
>r1
ACGTACGTACGT
>r2
TTGGAATTCCGG
FASTA

# BED (with names)
cat > "$DATA_DIR/regions.bed" <<'BED'
chr1	0	4	region1
chr1	4	8	region2
chr2	0	4	region3
BED

# BED3
cat > "$DATA_DIR/regions3.bed" <<'BED'
chr1	0	4
chr1	4	8
chr2	0	4
BED

# BED query
cat > "$DATA_DIR/regions2.bed" <<'BED'
chr1	2	6	query1
chr1	6	10	query2
chr2	1	5	query3
BED

# BEDPE
cat > "$DATA_DIR/regions.bedpe" <<'BEDPE'
chr1	0	2	chr1	4	6	name1
chr2	1	3	chr2	4	6	name2
BEDPE

# GTF
cat > "$DATA_DIR/genes.gtf" <<'GTF'
chr1	src	gene	1	10	.	+	.	gene_id "g1"; gene_name "g1";
chr1	src	exon	1	5	.	+	.	gene_id "g1"; transcript_id "t1";
chr1	src	exon	6	10	.	+	.	gene_id "g1"; transcript_id "t1";
GTF

# VCF (no samples)
cat > "$DATA_DIR/variants.vcf" <<'VCF'
##fileformat=VCFv4.2
##contig=<ID=chr1,length=32>
#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO
chr1	3	.	G	A	.	PASS	.
chr1	8	.	T	C	.	PASS	.
VCF

# VCF (non-overlapping for concat)
cat > "$DATA_DIR/variants2.vcf" <<'VCF'
##fileformat=VCFv4.2
##contig=<ID=chr1,length=32>
#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO
chr1	12	.	A	G	.	PASS	.
VCF

# VCF with sample + AD
cat > "$DATA_DIR/variants_samples.vcf" <<'VCF'
##fileformat=VCFv4.2
##contig=<ID=chr1,length=32>
##INFO=<ID=FLAG1,Number=0,Type=Flag,Description="test flag">
##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">
##FORMAT=<ID=AD,Number=R,Type=Integer,Description="Allelic depths">
#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO	FORMAT	sample1
chr1	3	.	G	A	.	PASS	FLAG1	GT:AD	0/1:5,3
chr1	8	.	T	C	.	PASS	.	GT:AD	0/1:7,2
VCF

# VCF with simple SV
cat > "$DATA_DIR/variants_sv.vcf" <<'VCF'
##fileformat=VCFv4.2
##contig=<ID=chr1,length=32>
##INFO=<ID=END,Number=1,Type=Integer,Description="End position">
##INFO=<ID=SVTYPE,Number=1,Type=String,Description="Type">
#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO
chr1	5	.	A	<DEL>	.	PASS	END=10;SVTYPE=DEL
VCF

# Tab-delimited file
cat > "$DATA_DIR/tab.tsv" <<'TAB'
chrom	start	end	name
chr1	2	6	row1
chr2	1	5	row2
TAB

# Minimal RepeatMasker-style output (3 header lines + 1 record)
cat > "$DATA_DIR/repeat.out" <<'TXT'
header line 1
header line 2
header line 3
  500  2.0  0.0  0.0  chr1  2  6  (0)  +  Alu  SINE/Alu  1  100  (0)  1
TXT

# Build BAMs using HTSJDK (single-end and paired-end)
cat > /tmp/MakeBam.java <<'JAVA'
import htsjdk.samtools.*;
import java.io.File;

public class MakeBam {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: MakeBam <out.bam>");
            System.exit(2);
        }
        SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        SAMSequenceDictionary dict = new SAMSequenceDictionary();
        dict.addSequence(new SAMSequenceRecord("chr1", 32));
        dict.addSequence(new SAMSequenceRecord("chr2", 14));
        header.setSequenceDictionary(dict);

        SAMRecord rec = new SAMRecord(header);
        rec.setReadName("read1");
        rec.setReferenceName("chr1");
        rec.setAlignmentStart(1);
        rec.setReadString("ACGTACGTACGT");
        rec.setBaseQualityString("FFFFFFFFFFFF");
        rec.setCigarString("12M");
        rec.setMappingQuality(60);
        rec.setReadPairedFlag(false);

        SAMFileWriterFactory factory = new SAMFileWriterFactory();
        SAMFileWriter writer = factory.makeBAMWriter(header, true, new File(args[0]));
        writer.addAlignment(rec);
        writer.close();
    }
}
JAVA

cat > /tmp/MakePairedBam.java <<'JAVA'
import htsjdk.samtools.*;
import java.io.File;

public class MakePairedBam {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: MakePairedBam <out.bam>");
            System.exit(2);
        }
        SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        SAMSequenceDictionary dict = new SAMSequenceDictionary();
        dict.addSequence(new SAMSequenceRecord("chr1", 32));
        dict.addSequence(new SAMSequenceRecord("chr2", 14));
        header.setSequenceDictionary(dict);

        SAMRecord r1 = new SAMRecord(header);
        r1.setReadName("pair1");
        r1.setReferenceName("chr1");
        r1.setAlignmentStart(1);
        r1.setReadString("ACGTACGT");
        r1.setBaseQualityString("FFFFFFFF");
        r1.setCigarString("8M");
        r1.setMappingQuality(60);
        r1.setReadPairedFlag(true);
        r1.setFirstOfPairFlag(true);
        r1.setMateReferenceName("chr1");
        r1.setMateAlignmentStart(5);
        r1.setMateUnmappedFlag(false);
        r1.setInferredInsertSize(12);

        SAMRecord r2 = new SAMRecord(header);
        r2.setReadName("pair1");
        r2.setReferenceName("chr1");
        r2.setAlignmentStart(5);
        r2.setReadString("TGCATGCA");
        r2.setBaseQualityString("FFFFFFFF");
        r2.setCigarString("8M");
        r2.setMappingQuality(60);
        r2.setReadPairedFlag(true);
        r2.setSecondOfPairFlag(true);
        r2.setMateReferenceName("chr1");
        r2.setMateAlignmentStart(1);
        r2.setMateUnmappedFlag(false);
        r2.setInferredInsertSize(-12);

        SAMFileWriterFactory factory = new SAMFileWriterFactory();
        SAMFileWriter writer = factory.makeBAMWriter(header, true, new File(args[0]));
        writer.addAlignment(r1);
        writer.addAlignment(r2);
        writer.close();
    }
}
JAVA

javac -cp "$ROOT_DIR/lib/htsjdk-1.126.jar" /tmp/MakeBam.java /tmp/MakePairedBam.java
java -cp /tmp:"$ROOT_DIR/lib/htsjdk-1.126.jar" MakeBam "$DATA_DIR/test.bam"
java -cp /tmp:"$ROOT_DIR/lib/htsjdk-1.126.jar" MakePairedBam "$DATA_DIR/test_paired.bam"

# Index BAMs
samtools index -b "$DATA_DIR/test.bam"
samtools index -b "$DATA_DIR/test_paired.bam"

# Convenience duplicates
cp "$DATA_DIR/test.bam" "$DATA_DIR/test2.bam"
samtools index -b "$DATA_DIR/test2.bam"
cp "$DATA_DIR/reads.fastq" "$DATA_DIR/reads2.fastq"

# Compressed FASTQ
bgzip -c "$DATA_DIR/reads.fastq" > "$DATA_DIR/reads.fastq.gz"
bzip2 -c "$DATA_DIR/reads.fastq" > "$DATA_DIR/reads.fastq.bz2"

# FAI index
samtools faidx "$DATA_DIR/ref.fa"

# BGZF + tabix indexes
bgzip -c "$DATA_DIR/regions.bed" > "$DATA_DIR/regions.bed.gz"
tabix -p bed "$DATA_DIR/regions.bed.gz"

bgzip -c "$DATA_DIR/variants.vcf" > "$DATA_DIR/variants.vcf.gz"
tabix -p vcf "$DATA_DIR/variants.vcf.gz"

bgzip -c "$DATA_DIR/variants2.vcf" > "$DATA_DIR/variants2.vcf.gz"
tabix -p vcf "$DATA_DIR/variants2.vcf.gz"

bgzip -c "$DATA_DIR/variants_samples.vcf" > "$DATA_DIR/variants_samples.vcf.gz"
tabix -p vcf "$DATA_DIR/variants_samples.vcf.gz"

bgzip -c "$DATA_DIR/variants_sv.vcf" > "$DATA_DIR/variants_sv.vcf.gz"
tabix -p vcf "$DATA_DIR/variants_sv.vcf.gz"

bgzip -c "$DATA_DIR/tab.tsv" > "$DATA_DIR/tab.tsv.gz"
# Generic tabix with header
 tabix -f -S 1 -s 1 -b 2 -e 3 "$DATA_DIR/tab.tsv.gz"

echo "Synthetic test data created in $DATA_DIR"
