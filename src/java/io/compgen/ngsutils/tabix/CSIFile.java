package io.compgen.ngsutils.tabix;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import io.compgen.common.StringUtils;
import io.compgen.common.io.DataIO;
import io.compgen.ngsutils.support.LogUtils;

public class CSIFile implements TabixIndex {

    protected class Ref {
        final protected Bin[] bins;

        public Ref(Bin[] bins) {
            this.bins = bins;
        }
    }

    protected class Bin {
        final protected long bin;
        final protected long lOffset;
        final protected Chunk[] chunks;

        public Bin(long bin, long lOffset, Chunk[] chunks) {
            this.bin = bin;
            this.lOffset = lOffset;
            this.chunks = chunks;
        }
    }

    protected GZIPInputStream in;

    final protected byte[] magic;
    final protected int minShift;
    final protected int depth;
    final protected int auxLength;
    final protected byte[] aux;

    // TABIX header (in AUX field)
    final protected int format;
    final protected int colSeq;
    final protected int colBegin;
    final protected int colEnd;
    final protected char meta;
    final protected int skipLines;
    final protected String[] seqNames;

    final protected long nNoCoor;

    final protected Ref[] refs;

    protected CSIFile(InputStream is) throws IOException {
        in = new GZIPInputStream(is);

        magic = DataIO.readRawBytes(in, 4);
        assert magic[0] == 'C';
        assert magic[1] == 'S';
        assert magic[2] == 'I';
        assert magic[3] == 1;

        minShift = DataIO.readInt32(in);
        depth = DataIO.readInt32(in);
        auxLength = DataIO.readInt32(in);

        if (auxLength < 28) {
            throw new IOException("Missing aux data in CSI header -- is this a tabix-compatible index?");
        }

        aux = DataIO.readRawBytes(in, auxLength);
        
        // read aux data...
        final ByteArrayInputStream auxIS = new ByteArrayInputStream(aux);
        format = DataIO.readInt32(auxIS);
        colSeq = DataIO.readInt32(auxIS);
        colBegin = DataIO.readInt32(auxIS);
        colEnd = DataIO.readInt32(auxIS);
        meta = (char) DataIO.readInt32(auxIS);
        skipLines = DataIO.readInt32(auxIS);

        final int seqNameLength = DataIO.readInt32(auxIS);

        final List<String> seqNames = new ArrayList<String>();

        final byte[] seqNameBytes = DataIO.readRawBytes(auxIS, seqNameLength);
        String buf = "";
        for (final byte b : seqNameBytes) {
            if (b == 0) {
                seqNames.add(buf);
                buf = "";
            } else {
                buf += new String(new byte[] { b });
            }
        }

        auxIS.close();

        this.seqNames = new String[seqNames.size()];
        for (int i = 0; i < seqNames.size(); i++) {
            this.seqNames[i] = seqNames.get(i);
        }

        final int nRef = DataIO.readInt32(in);

        this.refs = new Ref[nRef];

        for (int i = 0; i < refs.length; i++) {
            final int nBin = DataIO.readInt32(in);
            final Bin[] bins = new Bin[nBin];

            for (int j = 0; j < bins.length; j++) {
                final long bin = DataIO.readUint32(in);
                final long offset = DataIO.readUint64(in);
                final int nChunk = DataIO.readInt32(in);

                final Chunk[] chunks = new Chunk[nChunk];

                for (int k = 0; k < chunks.length; k++) {
                    final long cBegin = DataIO.readUint64(in);
                    final long cEnd = DataIO.readUint64(in);

                    chunks[k] = new Chunk(cBegin, cEnd);
                }

                bins[j] = new Bin(bin, offset, chunks);
            }

            refs[i] = new Ref(bins);
        }

        nNoCoor = DataIO.readUint64(in);

    }

    public CSIFile(String filename) throws IOException {
        this(new FileInputStream(filename));
    }

    public CSIFile(File file) throws IOException {
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
        System.out.println("min_shift: " + minShift);
        System.out.println("depth: " + depth);
        System.out.println("l_aux: " + auxLength);
        System.out.println("aux: " + StringUtils.byteArrayToString(aux));

        System.out.println("  format: " + format);
        System.out.println("  col_seq: " + colSeq);
        System.out.println("  col_beg: " + colBegin);
        System.out.println("  col_end: " + colEnd);
        System.out.println("  meta: " + meta);
        System.out.println("  skip: " + skipLines);
        System.out.println("  nseq: " + seqNames.length);

        for (int i = 0; i < seqNames.length; i++) {
            System.out.println("    seq[" + i + "]: " + seqNames[i]);
        }

        System.out.println("n_ref: " + refs.length);

        for (int i = 0; i < refs.length; i++) {
            System.out.println("    n_bin: " + refs[i].bins.length);

//             for (int j=0; j<refs[i].bins.length; j++) {
//             System.out.println("      bin: " + refs[i].bins[j].bin);
//             System.out.println("      loffset: " + refs[i].bins[j].lOffset + " => " + ((refs[i].bins[j].lOffset>> 16) & 0xFFFFFFFFFFFFl)+ " | " + (refs[i].bins[j].lOffset & 0xFFFF));
//            
//             System.out.println("      n_chunk: " + refs[i].bins[j].chunks.length);
//             for (int k=0; k<refs[i].bins[j].chunks.length; k++) {
//             System.out.println("          chunk_beg: " +
//             refs[i].bins[j].chunks[k].coffsetBegin+"|"+refs[i].bins[j].chunks[k].uoffsetBegin);
//             System.out.println("          chunk_end: " +
//             refs[i].bins[j].chunks[k].coffsetEnd+"|"+refs[i].bins[j].chunks[k].uoffsetEnd);
//             }
//             }
        }

        System.out.println("n_no_coor: " + nNoCoor);
    }

    // Below modifed from CSI SPEC doc
    /*
     * calculate bin given an alignment covering [beg,end) (zero-based,
     * half-close-half-open)
     */
    public static long reg2bin(long beg, long end, int min_shift, int depth) {
        int l;
        int s = min_shift;
        int t = ((1 << depth * 3) - 1) / 7;
        for (--end, l = depth; l > 0; --l, s += 3, t -= 1 << l * 3) {
            if (beg >> s == end >> s) {
                return t + (beg >> s);
            }
        }
        return 0L;
    }

    /*
     * calculate the list of bins that may overlap with region [beg,end)
     * (zero-based)
     */
    public static long[] reg2bins(long beg, long end, int min_shift, int depth) {
        /*
         * calculate the list of bins that may overlap with region [beg,end)
         * (zero-based)
         */
        // int reg2bins(int64_t beg, int64_t end, int min_shift, int depth, int
        // *bins)
        // {
        // int l, t, n, s = min_shift + depth*3;
        // for (--end, l = n = t = 0; l <= depth; s -= 3, t += 1<<l*3, ++l) {
        // int b = t + (beg>>s), e = t + (end>>s), i;
        // for (i = b; i <= e; ++i) bins[n++] = i;
        // }
        // return n;

        // minShift = 14, depth = 6
        final List<Long> bins = new ArrayList<Long>();

        int l = 0;
        int t = 0;
        int s = min_shift + depth * 3;

        end--;

        while (l <= depth) {
            // System.out.println("l="+l+", t="+t+", s="+s);
            final long b = t + (beg >> s);
            final long e = t + (end >> s);
            long i;

            // System.out.println("b="+b+", e="+e);

            for (i = b; i <= e; i++) {
                bins.add(i);
            }

            s -= 3;
            t += 1 << l * 3;
            l++;
        }

        final long[] lbins = new long[bins.size()];
        for (int i = 0; i < bins.size(); i++) {
            lbins[i] = bins.get(i);
        }

        return lbins;

        // int l;
        // int t;
        // int s = min_shift + depth*3;
        // for (--end, l = t = 0; l <= depth; s -= 3, t += 1<<l*3, ++l) {
        // long b = t + (beg>>s);
        // long e = t + (end>>s);
        // long i;
        // for (i = b; i <= e; ++i) {
        // bins.add(i);
        // }
        // }
        //
        // long[] lbins = new long[bins.size()];
        // for (int i=0;i<bins.size();i++) {
        // lbins[i] = bins.get(i);
        // }
        //
        // return lbins;

    }

    @Override
    public List<Chunk> find(String chrom, int start, int end) throws IOException {
//        System.out.println("Searching for: "+chrom+":"+start+","+end);
        int refIdx = 0;
        while (refIdx < seqNames.length && !seqNames[refIdx].equals(chrom)) {
            refIdx++;
        }

        final List<Chunk> chunks = new ArrayList<Chunk>();

        if (refIdx >= seqNames.length) {
            LogUtils.printOnce(System.err, "Can't find reference: " + chrom + " in index");
            return chunks;
        }

//        System.out.println("refIdx="+refIdx+" => " + seqNames[refIdx]);
        final Ref ref = refs[refIdx];

        final long[] possibleBins = reg2bins(start, end, minShift, depth);
        for (final long bin : possibleBins) {
//            System.out.println("POSSIBLE BIN: " +bin );
            chunks.addAll(findInRefBin(ref.bins, bin));
        }
        
//        for (Chunk c: chunks) {
//          System.out.println("          chunk_beg: " +
//          c.coffsetBegin+"|"+c.uoffsetBegin);
//          System.out.println("          chunk_end: " +
//          c.coffsetEnd+"|"+c.uoffsetEnd);
//        }

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

}
