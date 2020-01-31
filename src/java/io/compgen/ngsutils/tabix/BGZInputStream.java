package io.compgen.ngsutils.tabix;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import io.compgen.ngsutils.tabix.BGZFile.BGZBlock;

public class BGZInputStream extends InputStream {
	protected BGZFile bgzf;
	protected ByteArrayInputStream buf=null;
	
    public BGZInputStream(String filename) throws IOException {
        bgzf = new BGZFile(filename);
    }
    
    public BGZInputStream(BGZFile bgzf) throws IOException {
        this.bgzf = bgzf;
    }
    
	public void close() throws IOException {
		bgzf.close();
	}

	@Override
	public int read() throws IOException {
		if (buf == null || buf.available() == 0) {
		    BGZBlock block = bgzf.readCurrentBlock();
		    byte[] cur = block.uBuf;
		    if (cur == null ) {
		        return -1;
		    }
            buf = new ByteArrayInputStream(cur);
		}		
		return buf.read();
	}
}
