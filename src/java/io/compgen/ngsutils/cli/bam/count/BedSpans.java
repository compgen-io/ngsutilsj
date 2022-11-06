package io.compgen.ngsutils.cli.bam.count;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import io.compgen.common.AbstractLineReader;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.bam.Strand;

public class BedSpans extends AbstractLineReader<SpanGroup> implements SpanSource {
    private int numCols;
    private int extend;
    
    public BedSpans(String filename) throws IOException {
        this(filename, 0);
    }
    public BedSpans(String filename, int extend) throws IOException {
        super(filename);

        this.extend = extend;
        
        FileInputStream peek = new FileInputStream(filename);
        byte[] magic = new byte[2];
        peek.read(magic);
        peek.close();

        FileInputStream fis = new FileInputStream(filename);
        BufferedReader reader;
        if (Arrays.equals(magic, new byte[] {0x1f, (byte) 0x8B})) {
            reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(fis)));
        } else {
            reader = new BufferedReader(new InputStreamReader(fis));
        }
        
        String line = reader.readLine();
        if (line == null) {
        	this.numCols = 0;
        } else {
	        String[] cols = line.split("\t");
	        this.numCols = cols.length;
        }
        reader.close();
    }
    
    public long position() {
        if (channel!=null) {
            try {
                return channel.position();
            } catch (IOException e) {
            }
        }
        return -1;
    }
    
    public long size() {
        if (channel!=null) {
            try {
                return channel.size();
            } catch (IOException e) {
            }
        }
        return -1;
    }
    
    public SpanGroup convertLine(String line) {
        if (line == null || line.trim().equals("")) {
            return null;
        }
        line = StringUtils.rstrip(line);
        String[] cols = line.split("\t", -1);
        SpanGroup span;
        
        if (cols.length < 3) {
            return null;
        }
        
        int start = Integer.parseInt(cols[1]);
        int end = Integer.parseInt(cols[2]);
        
        start = start - extend;
        end = end + extend;
        
        if (start < 0) {
        	start = 0;
        }
        
        if (cols.length > 5) {
            span = new SpanGroup(cols[0], Strand.parse(cols[5]), cols, start, end);
        } else {
            span = new SpanGroup(cols[0], Strand.NONE, cols, start, end);
        }
        return span;
    }


    @Override
    public String[] getHeader() {
        String[] tmpl = new String[]{"chrom", "start", "end", "name", "score", "strand"};

        String[] out = new String[numCols];

        for (int i=0; i< numCols; i++) {
            if (i < tmpl.length) {
                out[i]=tmpl[i];
            } else {
                out[i]="col"+(i+1);
            }
        }

        return out;
    }
}
