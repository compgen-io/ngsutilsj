package io.compgen.ngsutils.cli.bam.count;

import java.io.IOException;
import java.util.Iterator;

import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.tabix.TabixFile;

/**
 * A Tabix span includes only start/end coordinates. Strands aren't take into account.
 * @author mbreese
 *
 */
public class TabixSpans implements SpanSource {
	protected TabixFile tabix = null;
    
    public TabixSpans(String filename) throws IOException {
    	tabix = new TabixFile(filename);
    }
    
    public long position() {
        if (tabix.getChannel() != null) {
            try {
                return tabix.getChannel().position();
            } catch (IOException e) {
            }
        }
        return -1;
    }
    
    public long size() {
        if (tabix.getChannel()!=null) {
            try {
                return tabix.getChannel().size();
            } catch (IOException e) {
            }
        }
        return -1;
    }
    
//    public SpanGroup convertLine(String line) {
//        if (line == null || line.trim().equals("")) {
//            return null;
//        }
//        line = StringUtils.rstrip(line);
//        String[] cols = line.split("\t", -1);
//        SpanGroup span;
//        
//        if (cols.length < 3) {
//            return null;
//        }
//        
//        int start = Integer.parseInt(cols[1]);
//        int end = Integer.parseInt(cols[2]);
//        
//        start = start - extend;
//        end = end + extend;
//        
//        if (start < 0) {
//        	start = 0;
//        }
//        
//        if (cols.length > 5) {
//            span = new SpanGroup(cols[0], Strand.parse(cols[5]), cols, start, end);
//        } else {
//            span = new SpanGroup(cols[0], Strand.NONE, cols, start, end);
//        }
//        return span;
//    }


    @Override
    public String[] getHeader() {
    	try {
			return tabix.getHeaderNames();
		} catch (IOException e) {
			// this shouldn't happen -- the exception would get thrown on cstor (I think)
			return null;
		}
    }

	@Override
	public Iterator<SpanGroup> iterator() {
		Iterator<String> it;
		final int numcols;
		try {
			it = tabix.lines();
			numcols = tabix.getHeaderNames().length;
		} catch (IOException e) {
			System.err.println("Exception getting lines: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
		
		return new Iterator<SpanGroup> () {
			SpanGroup next = null;
			boolean first = true;
			int lineno = 0;
			
			private void populate() {
//				System.err.println("Populating spans from tabix file");
				first = false;
				while (it.hasNext() && next == null) {
					String line = it.next();
	                if (lineno < tabix.getSkipLines()) {
//	    				System.err.println("Skipping line: "+lineno + " "+line);
		                lineno ++;
	                	continue;
	                }
	                
	                if (line.length()>0 && line.charAt(0) == tabix.getMeta()) {
	                	continue;
	                }

//					System.err.println("Got line: " + line);
	                
	                // need to handle cases where the last value(s) is empty
					String[] cols = line.split("\t", numcols);
//					System.err.println("line: \"" + line+"\"");
//					System.err.println("cols: " + StringUtils.join(",", cols));

					String chrom = cols[tabix.getColSeq()-1];
	                int start = Integer.parseInt(cols[tabix.getColBegin()-1]);
	                int end = Integer.parseInt(cols[tabix.getColEnd()-1]);
	                
	                if (!tabix.isZeroBased()) {
	                	start = start - 1;
	                }

	                // Tabix files don't include a strand column
	                next = new SpanGroup(chrom, Strand.NONE, cols, start, end);


				}
			}

			@Override
			public boolean hasNext() {
				if (first) {
					populate();
				}
				return next != null;
			}

			@Override
			public SpanGroup next() {
				if (first) {
					populate();
				}
				SpanGroup old = next;
				next = null;
				populate();
				return old;
			}
		};
	}
}
