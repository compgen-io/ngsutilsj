package io.compgen.ngsutils.tabix;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import io.compgen.common.StringUtils;
import io.compgen.common.io.DataIO;

public class TBIFile implements TabixIndex {

    protected class Ref {
        final protected Bin[] bins;

        public Ref(Bin[] bins) {
            this.bins = bins;
        }
    }

    protected class Bin {
        final protected long bin;
        // final protected long lOffset;
        final protected Chunk[] chunks;

        public Bin(long bin, Chunk[] chunks) {
            // public Bin(long bin, long lOffset, Chunk[] chunks) {
            this.bin = bin;
            // this.lOffset = lOffset;
            this.chunks = chunks;
        }
    }

    protected GZIPInputStream in;

    final protected byte[] magic;
    final protected int nRef;

    final protected int format;
    final protected int colSeq;
    final protected int colBegin;
    final protected int colEnd;
    final protected char meta;
    final protected int skipLines;
    final protected String[] seqNames;

    final protected long nNoCoor;

    final protected Ref[] refs;
    
    protected int refIdx = 0;

    protected TBIFile(InputStream is) throws IOException {
        in = new GZIPInputStream(is);

        magic = DataIO.readRawBytes(in, 4);
        assert magic[0] == 'T';
        assert magic[1] == 'B';
        assert magic[2] == 'I';
        assert magic[3] == 1;

        nRef = DataIO.readInt32(in);
        format = DataIO.readInt32(in);
        colSeq = DataIO.readInt32(in);
        colBegin = DataIO.readInt32(in);
        colEnd = DataIO.readInt32(in);
        meta = (char) DataIO.readInt32(in);
        skipLines = DataIO.readInt32(in);
        final int seqNameLength = DataIO.readInt32(in);
        final List<String> seqNames = new ArrayList<String>();

        final byte[] seqNameBytes = DataIO.readRawBytes(in, seqNameLength);
        String buf = "";
        for (final byte b : seqNameBytes) {
            if (b == 0) {
                seqNames.add(buf);
                buf = "";
            } else {
                buf += new String(new byte[] { b });
            }
        }

        this.seqNames = new String[seqNames.size()];
        for (int i = 0; i < seqNames.size(); i++) {
            this.seqNames[i] = seqNames.get(i);
        }

        this.refs = new Ref[nRef];

        for (int i = 0; i < refs.length; i++) {
            final int nBin = DataIO.readInt32(in);
            final Bin[] bins = new Bin[nBin];

            for (int j = 0; j < bins.length; j++) {
                final long bin = DataIO.readUint32(in);
                // long offset = DataIO.readUint64(in);
                final int nChunk = DataIO.readInt32(in);

                final Chunk[] chunks = new Chunk[nChunk];

                for (int k = 0; k < chunks.length; k++) {
                    final long cBegin = DataIO.readUint64(in);
                    final long cEnd = DataIO.readUint64(in);

                    chunks[k] = new Chunk(cBegin, cEnd);
                }

                bins[j] = new Bin(bin, chunks);
            }

            final int nIntv = DataIO.readInt32(in);

            for (int j = 0; j < nIntv; j++) {
                DataIO.readUint64(in); // ioff, not used (for linear index)
            }

            refs[i] = new Ref(bins);
        }

        nNoCoor = DataIO.readUint64(in);

    }

    public TBIFile(String filename) throws IOException {
        this(new FileInputStream(filename));
    }

    public TBIFile(File file) throws IOException {
        this(new FileInputStream(file));
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public char getMeta() {
        return meta;
    }

    @Override
    public int getColSeq() {
        return colSeq;
    }

    @Override
    public int getColBegin() {
        return colBegin;
    }

    @Override
    public int getColEnd() {
        return colEnd;
    }

    @Override
    public int getFormat() {
        return format;
    }
    
    
    
    @Override
    public boolean containsSeq(String name) {
        for (String s: seqNames) {
            if (s.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void dump() throws IOException {
        System.out.println("magic: " + StringUtils.byteArrayToString(magic) + " => "
                + (char) magic[0] + (char) magic[1] + (char) magic[2] + "\\" + magic[3] + "");
        System.out.println("min_shift: " + nRef);

        System.out.println("format: " + format);
        System.out.println("col_seq: " + colSeq);
        System.out.println("col_beg: " + colBegin);
        System.out.println("col_end: " + colEnd);
        System.out.println("meta: " + meta);
        System.out.println("skip: " + skipLines);

        for (int i = 0; i < seqNames.length; i++) {
            System.out.println("seq[" + i + "]: " + seqNames[i]);
        }

        System.out.println("n_ref: " + refs.length);

        for (int i = 0; i < refs.length; i++) {
            System.out.println("    n_bin: " + refs[i].bins.length);

            // for (int j=0; j<refs[i].bins.length; j++) {
            // System.out.println(" bin: " + refs[i].bins[j].bin);
            // System.out.println(" loffset: " + refs[i].bins[j].lOffset);
            //
            // System.out.println(" n_chunk: " + refs[i].bins[j].chunks.length);
            // for (int k=0; k<refs[i].bins[j].chunks.length; k++) {
            // System.out.println(" chunk_beg: " +
            // refs[i].bins[j].chunks[k].begin);
            // System.out.println(" chunk_end: " +
            // refs[i].bins[j].chunks[k].end);
            // }
            // }
        }

        System.out.println("n_no_coor: " + nNoCoor);
    }

    // Below modifed from TBI SPEC doc
    /*
     * calculate bin given an alignment covering [beg,end) (zero-based,
     * half-close-half-open)
     */
    public static long reg2bin(long beg, long end) {
        --end;
        if (beg >> 14 == end >> 14) {
            return ((1 << 15) - 1) / 7 + (beg >> 14);
        }
        if (beg >> 17 == end >> 17) {
            return ((1 << 12) - 1) / 7 + (beg >> 17);
        }
        if (beg >> 20 == end >> 20) {
            return ((1 << 9) - 1) / 7 + (beg >> 20);
        }
        if (beg >> 23 == end >> 23) {
            return ((1 << 6) - 1) / 7 + (beg >> 23);
        }
        if (beg >> 26 == end >> 26) {
            return ((1 << 3) - 1) / 7 + (beg >> 26);
        }
        return 0L;
    }

    /*
     * calculate the list of bins that may overlap with region [beg,end)
     * (zero-based)
     */
    public static long[] reg2bins(long beg, long end) {
        /*
         * 
         * #define MAX_BIN (((1<<18)-1)/7) int reg2bins(int rbeg, int rend,
         * uint16_t list[MAX_BIN]) { int i = 0, k; --rend; list[i++] = 0; for (k
         * = 1 + (rbeg>>26); k <= 1 + (rend>>26); ++k) list[i++] = k; for (k = 9
         * + (rbeg>>23); k <= 9 + (rend>>23); ++k) list[i++] = k; for (k = 73 +
         * (rbeg>>20); k <= 73 + (rend>>20); ++k) list[i++] = k; for (k = 585 +
         * (rbeg>>17); k <= 585 + (rend>>17); ++k) list[i++] = k; for (k = 4681
         * + (rbeg>>14); k <= 4681 + (rend>>14); ++k) list[i++] = k; return i;
         * // #elements in list[] }
         */

        final List<Long> bins = new ArrayList<Long>();

        long k;
        end--;

        bins.add(0L);

        for (k = 1 + (beg >> 26); k <= 1 + (end >> 26); ++k) {
            bins.add(k);
        }
        for (k = 9 + (beg >> 23); k <= 9 + (end >> 23); ++k) {
            bins.add(k);
        }
        for (k = 73 + (beg >> 20); k <= 73 + (end >> 20); ++k) {
            bins.add(k);
        }
        for (k = 585 + (beg >> 17); k <= 585 + (end >> 17); ++k) {
            bins.add(k);
        }
        for (k = 4681 + (beg >> 14); k <= 4681 + (end >> 14); ++k) {
            bins.add(k);
        }

        final long[] lbins = new long[bins.size()];
        for (int i = 0; i < bins.size(); i++) {
            lbins[i] = bins.get(i);
        }

        return lbins;

    }

    @Override
    public List<Chunk> find(String chrom, int start, int end) throws IOException {
        //int refIdx = 0;
        
        final List<Chunk> chunks = new ArrayList<Chunk>();
        
        if (refIdx >= seqNames.length || refIdx < 0 || !seqNames[refIdx].equals(chrom)) {
            refIdx = 0;
        
            while (refIdx < seqNames.length && !seqNames[refIdx].equals(chrom)) {
                refIdx++;
            }
    
            if (refIdx >= seqNames.length) {
                //LogUtils.printOnce(System.err, "Can't find reference: " + chrom + " in index");
                return chunks;
            }
        }

        // System.out.println("refIdx="+refIdx+" => " + seqNames[refIdx]);
        final Ref ref = refs[refIdx];

        final long[] possibleBins = reg2bins(start, end);
        for (final long bin : possibleBins) {
            // System.out.println("bin: " +bin );
            chunks.addAll(findInRefBin(ref.bins, bin));
        }
        return chunks;
    }

    private List<Chunk> findInRefBin(Bin[] bins, long bin) {
        final List<Chunk> chunks = new ArrayList<Chunk>();
        for (int i = 0; i < bins.length; i++) {
            if (bins[i].bin == bin) {
                // System.out.println("bin: " +bin+"!!!!,
                // loffset="+bins[i].lOffset+" => "+(bins[i].lOffset>>16)+ ", "
                // + (bins[i].lOffset & 0xFFFF));
                for (int j = 0; j < bins[i].chunks.length; j++) {
                    chunks.add(bins[i].chunks[j]);
                }
            }
        }
        return chunks;
    }
    @Override
    public int getSkipLines() {
        return skipLines;
    }

    @Override
    public boolean isZeroBased() {
        return (format & 0x10000) == 0x10000;
    }

}



