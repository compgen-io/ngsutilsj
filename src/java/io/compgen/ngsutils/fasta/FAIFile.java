package io.compgen.ngsutils.fasta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bed.BedRecord;

public class FAIFile {
    public class IndexRecord {
        public final String name;
        public final int length;
        public final long offset;
        public final int lineSeqLength;
        public final int lineOffsetLength;

        public IndexRecord(String name, int length, long offset, int lineSeqLength,
                int lineOffsetLength) {
            this.name = name;
            this.length = length;
            this.offset = offset;
            this.lineSeqLength = lineSeqLength;
            this.lineOffsetLength = lineOffsetLength;
        }
    }
    
	private Map<String, IndexRecord> indexMap = new HashMap<String, IndexRecord>();
	private List<String> refs = new ArrayList<String>();

	public FAIFile(String filename) throws NumberFormatException, IOException {
        for (String line: new StringLineReader(filename)) {
            String[] cols = StringUtils.strip(line).split("\t");
            indexMap.put(cols[0], new IndexRecord(cols[0], Integer.parseInt(cols[1]), Long.parseLong(cols[2]), Integer.parseInt(cols[3]), Integer.parseInt(cols[4])));
            refs.add(cols[0]);
        }
	}
	
	public boolean contains(String name) {
		return indexMap.containsKey(name);
	}
	
    public List<String> getNames() {
        return Collections.unmodifiableList(refs);
    }
    
    public long getLength(String name) {
        if (indexMap.containsKey(name)) {
            return indexMap.get(name).length;
        }
        return -1; 
    }
    public long getOffset(String name) {
        if (indexMap.containsKey(name)) {
            return indexMap.get(name).offset;
        }
        return -1; 
    }
    public int getLineSeqLength(String name) {
        if (indexMap.containsKey(name)) {
            return indexMap.get(name).lineSeqLength;
        }
        return -1; 
    }
    public int getLineOffsetLength(String name) {
        if (indexMap.containsKey(name)) {
            return indexMap.get(name).lineOffsetLength;
        }
        return -1; 
    }
    
    public BedRecord getBed(String name) {
		IndexRecord idxrec = indexMap.get(name);
		return new BedRecord(new GenomeSpan(idxrec.name, 0, idxrec.length));
    }
    
    public Iterator<BedRecord> asBed() {
        return new Iterator<BedRecord>() {
        	int curidx = 0;
			@Override
			public boolean hasNext() {
				return curidx < refs.size();
			}

			@Override
			public BedRecord next() {
				BedRecord rec = getBed(refs.get(curidx));
				curidx++;
				
				return rec;
			}
        };

    }

}