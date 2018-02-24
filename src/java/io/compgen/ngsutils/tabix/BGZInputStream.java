package io.compgen.ngsutils.tabix;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class BGZInputStream extends InputStream {
	protected BGZFile bgzf;
	protected ByteArrayInputStream buf=null;
	
	public BGZInputStream(String filename) throws IOException {
		bgzf = new BGZFile(filename);
	}
	
	public void close() throws IOException {
		bgzf.close();
	}

	@Override
	public int read() throws IOException {
		if (buf == null || buf.available() == 0) {
		    byte[] cur = bgzf.readCurrentBlock();
		    if (cur == null ) {
		        return -1;
		    }
            buf = new ByteArrayInputStream(cur);
		}		
		return buf.read();
	}
}
