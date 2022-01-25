package io.compgen.ngsutils.cli.tdf;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.ListBuilder;
import io.compgen.common.Pair;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.support.FileUtils;
import io.compgen.ngsutils.tdf.TabDelimitedFile;
import io.compgen.ngsutils.tdf.annotate.InFileAnnotator;
import io.compgen.ngsutils.tdf.annotate.TDFAnnotationException;
import io.compgen.ngsutils.tdf.annotate.TDFAnnotator;
import io.compgen.ngsutils.tdf.annotate.TDFTabAnnotator;


@Command(name="tdf-join", desc="Annotate a tab-delimited file (not-tabix indexed)", category="annotation")
public class TabJoin extends AbstractOutputCommand {
	
	private String filename = "-";
	private boolean noHeader = false;
	private boolean headerComment = false;

	
	// one = name, two = annotator
	List<Pair<String, TDFAnnotator>> chain = new ArrayList<Pair<String, TDFAnnotator>>();
    
    @Option(desc="Add annotations from a different tab-delimited file (if not set, X=1, Y=1, Z=2). Columns can be numbers (1-based) or names (with header). Flags (optional): noheader - file has no header, headercomment - the header line is the last commented line, csv - input key column could be comma separated, collapse - only write unique values (if more than one), first - only write the first value (if more than one match)", name="tdf", helpValue="FILENAME{,key1=X,key2=Y,col=Z,name=V,noheader,headercomment,csv,collapse,first}", allowMultiple=true)
    public void setTDF(String tab) throws CommandArgumentException {
        String[] spl = tab.split(",");
        
            
            String fname = null;
			String key1 = "1";
			String key2 = "1";
			String val1 = "2";
			String name = "";
			boolean collapse = false;
			boolean first = false;
			boolean csv = false;
			boolean noheader = false;
			boolean headerComment = false;
			
			for (String t:spl) {
			    if (fname == null) {
			        fname = FileUtils.expandUserPath(t);
			    } else {
			        if (t.equals("collapse")) {
			            collapse = true;
			        } else if (t.equals("first")) {
			            first = true;
			        } else if (t.equals("csv")) {
			        	csv = true;
			        } else if (t.equals("headercomment")) {
			        	headerComment = true;
			        } else if (t.equals("noheader")) {
			        	noheader = true;
			        } else if (t.startsWith("key1=")) {
			        	key1 = t.split("=")[1];
			        } else if (t.startsWith("key2=")) {
			        	key2 = t.split("=")[1];
			        } else if (t.startsWith("col=")) {
			        	val1 = t.split("=")[1];
			        } else if (t.startsWith("name=")) {
			        	name = t.split("=")[1];
			        }
			    }
			}
			
			if (fname == null || !new File(fname).exists()) {
	            throw new CommandArgumentException("Unable to parse argument for --tdf, missing file: "+tab);
			}
			
			TDFTabAnnotator tta = new TDFTabAnnotator(fname, key2, val1, name);

			if (collapse) {
				tta.setCollapse();
			}
			if (first) {
				tta.setFirst();
			}
			if (csv) {
				tta.setCSV();
			}
			if (noheader) {
				tta.setNoHeader();
			}
			if (headerComment) {
				tta.setHeaderComment();
			}

			try {
				tta.validate();
			} catch (TDFAnnotationException e) {
	            throw new CommandArgumentException("Unable to parse argument for --tdf: "+tab, e);
			}
			
			chain.add(new Pair<String, TDFAnnotator>(key1, tta));
    }
    @Option(desc="Add a flag column if a value appears in a file (ex: gene lists). Flags (optional): csv - input key column could be comma separated, collapse - only write unique values (if more than one), first - only write the first value (if more than one match)", name="in-file", helpValue="FILENAME{,key=X,name=Y,csv,collapse,first}", allowMultiple=true)
    public void setInFile(String tab) throws CommandArgumentException {
        String[] spl = tab.split(",");
        
            
            String fname = null;
			String key1 = "1";
			String name = "";
			boolean collapse = false;
			boolean first = false;
			boolean csv = false;
			
			for (String t:spl) {
			    if (fname == null) {
			        fname = FileUtils.expandUserPath(t);
			    } else {
			        if (t.equals("collapse")) {
			            collapse = true;
			        } else if (t.equals("first")) {
			            first = true;
			        } else if (t.equals("csv")) {
			        	csv = true;
			        } else if (t.startsWith("key=")) {
			        	key1 = t.split("=")[1];
			        } else if (t.startsWith("name=")) {
			        	name = t.split("=")[1];
			        }
			    }
			}
			
			if (fname == null || !new File(fname).exists()) {
	            throw new CommandArgumentException("Unable to parse argument for --infile, missing file: "+tab);
			}
			
			InFileAnnotator tta = new InFileAnnotator(fname, name);

			if (collapse) {
				tta.setCollapse();
			}
			if (first) {
				tta.setFirst();
			}
			if (csv) {
				tta.setCSV();
			}

			try {
				tta.validate();
			} catch (TDFAnnotationException e) {
	            throw new CommandArgumentException("Unable to parse argument for --infile: "+tab, e);
			}
			
			chain.add(new Pair<String, TDFAnnotator>(key1, tta));
    }
    
    @Option(desc="Input file has no header line", name="noheader")
    public void setHeader(boolean val) {
        this.noHeader = val;
    }

    @Option(desc="Input header line is the last commented line", name="headercomment")
    public void setHeaderComment(boolean val) {
        this.headerComment = val;
    }

    @UnnamedArg(name = "input.tab", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

	@Exec
	public void exec() throws Exception {		
		final TabDelimitedFile inFile = new TabDelimitedFile(filename, !noHeader, headerComment);

		List<String> colNames = new ArrayList<String>();
		Map<String, Integer> colIdx = new HashMap<String, Integer>();

		if (!noHeader) {
			// existing columns
			for (String h:inFile.getHeaderNames()) {
				colNames.add(h);
			}
			// new columns
			for (Pair<String, TDFAnnotator> tup: chain) {
				colNames.add(tup.two.getName());
			}
	
			// add integer indexes
			for (int i=0; i<colNames.size(); i++) {
				colIdx.put(colNames.get(i), i);
				colIdx.put(""+(i+1), i);
			}
		}
		
		TabWriter writer = new TabWriter(out);
		String comments = inFile.readComments();
		
		if (StringUtils.strip(comments).length()>0) {
			out.write((comments+"\n").getBytes(Charset.defaultCharset()));
		}

		
		if (!noHeader) {
			boolean first = true;
			for (String col: colNames) {
				if (headerComment && first) {
					first = false;
					col = "#" + col;
				}
				writer.write(col);
			}
			writer.eol();
		}
		
		
		for (String[] line: IterUtils.wrap(inFile.lines())) {
			List<String> ll = ListBuilder.build(line);

			for (Pair<String, TDFAnnotator> tup: chain) {
				String keyval = null;
				if (!noHeader) {

					if (!colIdx.containsKey(tup.one)) {
						throw new TDFAnnotationException("Missing key column: " + tup.one);
					}
					
					keyval = line[colIdx.get(tup.one)];
				} else {
					keyval = line[Integer.parseInt(tup.one)-1];
				}
				
				String v = tup.two.getValue(keyval);
				if (v == null) {
					v = "";
				}
				ll.add(v);
			}
			writer.write(ll);
			writer.eol();
		}
		writer.close();
	}

}
