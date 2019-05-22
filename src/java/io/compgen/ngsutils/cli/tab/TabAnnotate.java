package io.compgen.ngsutils.cli.tab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;
import io.compgen.ngsutils.tabix.TabixFile;
import io.compgen.ngsutils.tabix.annotate.TabAnnotator;
import io.compgen.ngsutils.tabix.annotate.TabixTabAnnotator;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="tab-annotate", desc="Annotate a tab-delimited file", category="annotation")
public class TabAnnotate extends AbstractOutputCommand {
	private String filename = "-";
	private boolean hasHeader = false;

	
	List<TabAnnotator> chain = new ArrayList<TabAnnotator>();
    
    @Option(desc="Add annotations from a Tabix indexed file (If col is left out, this is treaded as a flag)", name="tab", helpValue="NAME:FILENAME{,col,collapse,first}", allowMultiple=true)
    public void setTabix(String tab) throws CommandArgumentException {
        String[] spl = tab.split(":");
        
        if (spl.length == 2 || spl.length == 3) {
            String[] spl2 = spl[1].split(",");
            
            try {
                String fname = null;
                int col = -1;
                boolean collapse = false;
                boolean first = false;
                
                for (String t:spl2) {
                    if (fname == null) {
                        fname = t;
                    } else {
                        if (t.equals("collapse")) {
                            collapse = true;
                        } else if (t.equals("first")) {
                            first = true;
                        } else if (col == -1) {
                            col = Integer.parseInt(t)-1;
                        }
                    }
                }
                if (col > -1) {
                    chain.add(new TabixTabAnnotator(spl[0], fname, col, collapse, first));
                } else {
                    chain.add(new TabixTabAnnotator(spl[0], fname));
                }
            } catch (NumberFormatException | IOException  e) {
                throw new CommandArgumentException(e);
            }
        } else {
            throw new CommandArgumentException("Unable to parse argument for --tab: "+tab);
        }       
    }
    
    @Option(desc="File has a header (only valid if input file has skip-lines set in tabix index)", name="header")
    public void setHeader(boolean val) {
        this.hasHeader = val;
    }


//    @Option(desc="Add INFO annotation from a VCF file (CSI indexed, add '!' for exact matches)", name="vcf", helpValue="NAME:FIELD:FILENAME{:!}", allowMultiple=true)
//    public void setVCF(String vcf) throws CommandArgumentException {
//    	String[] spl = vcf.split(":");
//    	if (spl.length == 3) {
//    		try {
//				chain.add(new VCFAnnotation(spl[0], spl[2], spl[1]));
//			} catch (IOException e) {
//	    		throw new CommandArgumentException(e);
//			}
//    	} else if (spl.length == 4) {
//    		try {
//				chain.add(new VCFAnnotation(spl[0], spl[2], spl[1], spl[3].equals("!")));
//			} catch (IOException e) {
//	    		throw new CommandArgumentException(e);
//			}
//    	} else {
//    		throw new CommandArgumentException("Unable to parse argument for --vcf: "+vcf);
//    	}    	
//    }    
//    
//    @Option(desc="Flag variants within a VCF file (INFO, CSI indexed, add '!' for exact matches)", name="vcf-flag", helpValue="NAME:FILENAME{:!}", allowMultiple=true)
//    public void setVCFFlag(String vcf) throws CommandArgumentException {
//        String[] spl = vcf.split(":");
//        if (spl.length == 2) {
//            try {
//                chain.add(new VCFAnnotation(spl[0], spl[1], null));
//            } catch (IOException e) {
//                throw new CommandArgumentException(e);
//            }
//        } else if (spl.length == 3) {
//                try {
//                    chain.add(new VCFAnnotation(spl[0], spl[1], null, spl[2].equals("!")));
//                } catch (IOException e) {
//                    throw new CommandArgumentException(e);
//                }
//        } else {
//            throw new CommandArgumentException("Unable to parse argument for --vcf-flag: "+vcf);
//        }
//    }
    
    @UnnamedArg(name = "input.tab", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

	@Exec
	public void exec() throws Exception {		
		final TabixFile tabix = new TabixFile(filename);
		int lineno = 0;
		boolean addedHeader = false;

	      Iterator<String> it = ProgressUtils.getIterator(filename, tabix.lines(),  new FileChannelStats(tabix.getChannel()), new ProgressMessage<String>() {
	            public String msg(String line) {
	                String[] cols = line.split("\t");
	                String chrom = cols[tabix.getColSeq()-1];
	                int start = Integer.parseInt(cols[tabix.getColBegin()-1]);
	                if (tabix.getColEnd() > -1) {
                        return chrom+":"+start+"-"+Integer.parseInt(cols[tabix.getColEnd()-1]);
	                } else {
                        return chrom+":"+start;
	                }

	            }});
		
		for (String line: IterUtils.wrap(it)) {
		    if (lineno < tabix.getSkipLines()) {
	            lineno++;
                System.err.println("Skipping line " +lineno );
	            
	            if (lineno == tabix.getSkipLines() && hasHeader && !addedHeader) {
	                addedHeader = true;
                    System.out.println(tabix.getMeta()+"#ngsutilsj_tab_annotateCommand="+NGSUtils.getArgs());
                    System.out.println(tabix.getMeta()+"#ngsutilsj_tab_annotateVersion="+NGSUtils.getVersion());

	                
	                System.out.print(line);
	                for (TabAnnotator ann: chain) {
	                    System.out.print("\t"+ann.getName());
	                }
	                System.out.println();
	            } else {
                    System.out.println(line);
	            }
	            
                continue;
		    }
		    
		    if (line.length() > 0 && line.charAt(0) == tabix.getMeta()) {
                System.out.println(line);
                continue;
		    }
		    
		    if (!addedHeader) {
		        addedHeader = true;
                System.out.println(tabix.getMeta()+"#ngsutilsj_tab_annotateCommand="+NGSUtils.getArgs());
                System.out.println(tabix.getMeta()+"#ngsutilsj_tab_annotateVersion="+NGSUtils.getVersion());
		    }
		    
		    String[] cols = line.split("\t");
		    List<String> out = new ArrayList<String>();
		    for (String s: cols) {
		        out.add(s);
		    }
		    
		    String chrom = cols[tabix.getColSeq()-1];
            int start = Integer.parseInt(cols[tabix.getColBegin()-1]);
            int end = -1;
            if (tabix.getColEnd() > -1) {
                end = Integer.parseInt(cols[tabix.getColEnd()-1]);
            }
            
            for (TabAnnotator ann: chain) {
                String val = ann.getValue(chrom, start, end);
                if (val == null) {
                    out.add("");
                } else {
                    out.add(val);
                }
            }
            
            System.out.println(StringUtils.join("\t", out));
		}
		
        for (TabAnnotator ann: chain) {
            ann.close();
		}
        tabix.close();
	}

}
