#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="$ROOT_DIR/test-data"
OUT_DIR="$DATA_DIR/outputs"

mkdir -p "$OUT_DIR"

EXE="$ROOT_DIR/ngsutilsj"

if [[ ! -x "$EXE" ]]; then
  echo "Missing native binary at $EXE" >&2
  exit 1
fi

# Ensure data exists
if [[ ! -f "$DATA_DIR/ref.fa" ]]; then
  echo "Missing test data. Run test-data/setup_data.sh first." >&2
  exit 1
fi

run_cmd() {
  local name="$1"; shift
  "$EXE" "$@" > "$OUT_DIR/${name}.txt" 2>&1 || true
}

# Core
run_cmd version version
run_cmd help help
run_cmd license license

# annotate
run_cmd annotate-gtf annotate-gtf --gtf "$DATA_DIR/genes.gtf" --bed3 "$DATA_DIR/regions3.bed"
run_cmd annotate-repeat annotate-repeat --repeat "$DATA_DIR/repeat.out" --bed3 --noheader "$DATA_DIR/regions3.bed"

# bam
run_cmd bam-basecall bam-basecall "$DATA_DIR/test.bam"
run_cmd bam-best bam-best "$DATA_DIR/test.bam" "$DATA_DIR/test2.bam" -- /tmp/bam_best1.bam /tmp/bam_best2.bam
run_cmd bam-bins bam-bins "$DATA_DIR/test.bam"
run_cmd bam-check bam-check "$DATA_DIR/test.bam"
run_cmd bam-clean bam-clean "$DATA_DIR/test.bam" /tmp/bam_clean.bam
run_cmd bam-concat bam-concat /tmp/bam_concat.bam "$DATA_DIR/test.bam" "$DATA_DIR/test2.bam"
run_cmd bam-count bam-count --bed "$DATA_DIR/regions3.bed" "$DATA_DIR/test.bam"
run_cmd bam-coverage bam-coverage "$DATA_DIR/test.bam"
run_cmd bam-discord bam-discord --discord /tmp/bam_discord.bam "$DATA_DIR/test_paired.bam"
run_cmd bam-dups bam-dups "$DATA_DIR/test_paired.bam" /tmp/bam_dups.bam
run_cmd bam-expressed bam-expressed "$DATA_DIR/test.bam"
run_cmd bam-extract bam-extract --bed "$DATA_DIR/regions3.bed" "$DATA_DIR/test.bam" /tmp/bam_extract.bam
run_cmd bam-filter bam-filter "$DATA_DIR/test.bam" /tmp/bam_filter.bam
run_cmd bam-phase bam-phase --bam "$DATA_DIR/test.bam" --fasta "$DATA_DIR/ref.fa" --vcf "$DATA_DIR/variants_samples.vcf" --out-bam /tmp/bam_phase
run_cmd bam-pir bam-pir --vcf "$DATA_DIR/variants_samples.vcf" --ref "$DATA_DIR/ref.fa" "$DATA_DIR/test.bam"
run_cmd bam-readgroup bam-readgroup --rg-id RG1 "$DATA_DIR/test.bam" /tmp/bam_readgroup.bam
run_cmd bam-refcount bam-refcount "$DATA_DIR/test.bam"
run_cmd bam-removeclipping bam-removeclipping -f "$DATA_DIR/test.bam" /tmp/bam_removeclipping.bam
run_cmd bam-sample bam-sample --output /tmp/bam_sample --reads 1 "$DATA_DIR/test.bam"
run_cmd bam-softclip bam-softclip "$DATA_DIR/test.bam"
run_cmd bam-sort bam-sort "$DATA_DIR/test.bam" /tmp/bam_sort.bam
run_cmd bam-split bam-split --read-count 1 --out /tmp/bam_split "$DATA_DIR/test.bam"
run_cmd bam-stats bam-stats "$DATA_DIR/test.bam"
run_cmd bam-tobed bam-tobed "$DATA_DIR/test.bam"
run_cmd bam-tobedgraph bam-tobedgraph "$DATA_DIR/test.bam"
run_cmd bam-tobedpe bam-tobedpe "$DATA_DIR/test_paired.bam"
run_cmd bam-tofasta bam-tofasta --ref "$DATA_DIR/ref.fa" "$DATA_DIR/test.bam"
run_cmd bam-tofastq bam-tofastq --mapped "$DATA_DIR/test.bam"
run_cmd bam-varcall bam-varcall --ref "$DATA_DIR/ref.fa" "$DATA_DIR/test.bam"
run_cmd bam-wps bam-wps "$DATA_DIR/test_paired.bam"

# bed
run_cmd bed-clean bed-clean "$DATA_DIR/regions.bed"
run_cmd bed-count bed-count "$DATA_DIR/regions3.bed" "$DATA_DIR/regions2.bed"
run_cmd bed-merge bed-merge --bed "$DATA_DIR/regions3.bed" --bed "$DATA_DIR/regions2.bed"
run_cmd bed-nearest bed-nearest "$DATA_DIR/regions3.bed" "$DATA_DIR/regions2.bed"
run_cmd bed-reduce bed-reduce "$DATA_DIR/regions.bed"
run_cmd bed-resize bed-resize -5 1 -3 1 "$DATA_DIR/regions3.bed"
run_cmd bed-sort bed-sort "$DATA_DIR/regions.bed"
run_cmd bed-stats bed-stats "$DATA_DIR/regions.bed"
run_cmd bed-tobed3 bed-tobed3 "$DATA_DIR/regions.bed"
run_cmd bed-tobed6 bed-tobed6 "$DATA_DIR/regions.bed"
run_cmd bed-tobedgraph bed-tobedgraph "$DATA_DIR/regions.bed"
run_cmd bed-tobedpe bed-tobedpe "$DATA_DIR/regions3.bed" "$DATA_DIR/regions2.bed"
run_cmd bed-tofasta bed-tofasta "$DATA_DIR/regions3.bed" "$DATA_DIR/ref.fa"
run_cmd bedpe-tobed bedpe-tobed "$DATA_DIR/regions.bedpe"

# fasta
run_cmd fasta-bins fasta-bins --window 4 "$DATA_DIR/ref.fa"
run_cmd fasta-filter fasta-filter --include chr1 "$DATA_DIR/ref.fa"
run_cmd fasta-gc fasta-gc --bins 4 "$DATA_DIR/ref.fa"
run_cmd fasta-genreads fasta-genreads --fasta -o /tmp/genreads.fa -l 4 "$DATA_DIR/ref.fa"
run_cmd fasta-grep fasta-grep "$DATA_DIR/ref.fa" ACGT
run_cmd fasta-mask fasta-mask --bed "$DATA_DIR/regions3.bed" "$DATA_DIR/ref.fa"
run_cmd fasta-motif fasta-motif --motif ACG "$DATA_DIR/ref.fa"
run_cmd fasta-names fasta-names "$DATA_DIR/ref.fa"
run_cmd fasta-random fasta-random --count 1 --len 10
run_cmd fasta-revcomp fasta-revcomp --file "$DATA_DIR/ref.fa"
run_cmd fasta-split fasta-split --split-count 1 --template /tmp/fasta_split_ "$DATA_DIR/ref.fa"
run_cmd fasta-subseq fasta-subseq "$DATA_DIR/ref.fa" chr1:2-6
run_cmd fasta-tag fasta-tag --prefix X_ "$DATA_DIR/ref.fa"
run_cmd fasta-tri fasta-tri "$DATA_DIR/ref.fa"
run_cmd fasta-wrap fasta-wrap -w 8 "$DATA_DIR/ref.fa"

# fastq
run_cmd fastq-barcode fastq-barcode "$DATA_DIR/reads_illumina.fastq"
run_cmd fastq-check fastq-check "$DATA_DIR/reads.fastq"
run_cmd fastq-demux fastq-demux -f --barcode ACGT --rg-id RG1 --out /tmp/demux_%RGID.fastq --unmatched /tmp/demux_unmatched.fastq "$DATA_DIR/reads_illumina.fastq"
run_cmd fastq-filter fastq-filter "$DATA_DIR/reads.fastq"
run_cmd fastq-merge fastq-merge "$DATA_DIR/reads.fastq" "$DATA_DIR/reads2.fastq"
run_cmd fastq-overlap fastq-overlap -o /tmp/overlap.fastq "$DATA_DIR/reads.fastq" "$DATA_DIR/reads2.fastq"
run_cmd fastq-remix fastq-remix --total 1 -o /tmp/remix.fastq "$DATA_DIR/reads.fastq"
run_cmd fastq-separate fastq-separate --read1 /tmp/read1.fastq --read2 /tmp/read2.fastq "$DATA_DIR/reads.fastq"
run_cmd fastq-sort fastq-sort "$DATA_DIR/reads.fastq"
run_cmd fastq-split fastq-split --num 1 "$DATA_DIR/reads.fastq"
run_cmd fastq-stats fastq-stats "$DATA_DIR/reads.fastq"
run_cmd fastq-tobam fastq-tobam "$DATA_DIR/reads.fastq"
run_cmd fastq-tofasta fastq-tofasta "$DATA_DIR/reads.fastq"

# gtf
run_cmd gtf-export gtf-export --genes "$DATA_DIR/genes.gtf"
run_cmd gtf-geneinfo gtf-geneinfo --size-genomic "$DATA_DIR/genes.gtf"
run_cmd gtf-tofasta gtf-tofasta "$DATA_DIR/ref.fa" "$DATA_DIR/genes.gtf"

# annotation/tabix
run_cmd tabix tabix "$DATA_DIR/tab.tsv.gz" chr1:1-5
run_cmd tabix-cat tabix-cat "$DATA_DIR/tab.tsv.gz"
run_cmd tabix-concat tabix-concat "$DATA_DIR/tab.tsv.gz" "$DATA_DIR/tab.tsv.gz"
run_cmd tabix-split tabix-split "$DATA_DIR/tab.tsv.gz"
run_cmd tab-annotate tab-annotate --tab name:"$DATA_DIR/tab.tsv.gz",4 "$DATA_DIR/tab.tsv.gz"
run_cmd tdf-join tdf-join --tdf "$DATA_DIR/regions3.bed",1,2,3 "$DATA_DIR/tab.tsv"

# bgzip
run_cmd bgzip-cat bgzip-cat "$DATA_DIR/variants.vcf.gz"
run_cmd bgzip-stats bgzip-stats "$DATA_DIR/variants.vcf.gz"

# support
run_cmd digest-stream digest-stream --md5 -o /tmp/digest.md5 "$DATA_DIR/reads.fastq" /tmp/digest.out
run_cmd fisher-test fisher-test 1 2 3 4
run_cmd prop-test prop-test 1 2 3 4

# vcf
run_cmd vcf-annotate vcf-annotate --tag TEST:1 "$DATA_DIR/variants.vcf"
run_cmd vcf-bedcount vcf-bedcount "$DATA_DIR/regions3.bed" "$DATA_DIR/variants.vcf"
run_cmd vcf-check vcf-check "$DATA_DIR/variants.vcf"
run_cmd vcf-chrfix vcf-chrfix --ucsc "$DATA_DIR/variants.vcf"
run_cmd vcf-clearfilter vcf-clearfilter "$DATA_DIR/variants.vcf"
run_cmd vcf-concat vcf-concat "$DATA_DIR/variants.vcf" "$DATA_DIR/variants2.vcf"
run_cmd vcf-concat-n vcf-concat-n "$DATA_DIR/variants.vcf" "$DATA_DIR/variants.vcf"
run_cmd vcf-consensus vcf-consensus "$DATA_DIR/variants.vcf"
run_cmd vcf-count vcf-count --ref "$DATA_DIR/ref.fa" "$DATA_DIR/variants_samples.vcf" "$DATA_DIR/test.bam"
run_cmd vcf-effect vcf-effect "$DATA_DIR/ref.fa" "$DATA_DIR/genes.gtf" "$DATA_DIR/variants_samples.vcf"
run_cmd vcf-export vcf-export "$DATA_DIR/variants.vcf"
run_cmd vcf-filter vcf-filter "$DATA_DIR/variants.vcf"
run_cmd vcf-header-info vcf-header-info --info "$DATA_DIR/variants.vcf"
run_cmd vcf-merge vcf-merge "$DATA_DIR/variants_samples.vcf" "$DATA_DIR/variants_samples.vcf"
run_cmd vcf-peptide vcf-peptide "$DATA_DIR/ref.fa" "$DATA_DIR/genes.gtf" "$DATA_DIR/variants_samples.vcf"
run_cmd vcf-refbuild vcf-refbuild "$DATA_DIR/variants.vcf" "$DATA_DIR/ref.fa"
run_cmd vcf-remove-flags vcf-remove-flags "$DATA_DIR/variants_samples.vcf"
run_cmd vcf-rename vcf-rename --sample sample1:sampleX "$DATA_DIR/variants_samples.vcf"
run_cmd vcf-reorder vcf-reorder --sample sample1 "$DATA_DIR/variants_samples.vcf"
run_cmd vcf-sample-export vcf-sample-export --key GT "$DATA_DIR/variants_samples.vcf"
run_cmd vcf-samples vcf-samples "$DATA_DIR/variants.vcf"
run_cmd vcf-stats vcf-stats "$DATA_DIR/variants.vcf"
run_cmd vcf-strip vcf-strip "$DATA_DIR/variants.vcf"
run_cmd vcf-svtofasta vcf-svtofasta "$DATA_DIR/ref.fa" "$DATA_DIR/variants_sv.vcf"
run_cmd vcf-tobed vcf-tobed "$DATA_DIR/variants.vcf"
run_cmd vcf-tobedpe vcf-tobedpe "$DATA_DIR/variants.vcf"
run_cmd vcf-tocount vcf-tocount "$DATA_DIR/variants_samples.vcf"
run_cmd vcf-tstv vcf-tstv "$DATA_DIR/variants.vcf"
run_cmd vcf-split vcf-split --out /tmp/vcf_split --num 1 "$DATA_DIR/variants.vcf"
run_cmd vcf-svtofasta vcf-svtofasta "$DATA_DIR/ref.fa" "$DATA_DIR/variants_sv.vcf"

# tdf/other
run_cmd tdf-join tdf-join --tdf "$DATA_DIR/regions3.bed",1,2,3 "$DATA_DIR/tab.tsv"
run_cmd pileup pileup --ref "$DATA_DIR/ref.fa" "$DATA_DIR/test.bam"
run_cmd bai-explore bai-explore "$DATA_DIR/test.bam.bai"

# write summary
python3 - <<PY
from pathlib import Path
import re

outdir = Path(r"$OUT_DIR")
errs = []
for f in sorted(outdir.glob('*.txt')):
    txt = f.read_bytes().decode('utf-8', errors='replace')
    if 'ERROR:' in txt:
        line = [ln for ln in txt.splitlines() if ln.startswith('ERROR:')]
        errs.append((f.stem, line[0] if line else ''))

summary = outdir / "summary.txt"
with summary.open('w') as fh:
    fh.write(f"errors: {len(errs)}\n")
    for name, line in errs:
        fh.write(f"{name}: {line}\n")
print(summary)
PY

echo "Outputs written to $OUT_DIR"
