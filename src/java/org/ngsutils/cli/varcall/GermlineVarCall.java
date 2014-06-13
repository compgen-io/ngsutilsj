package org.ngsutils.cli.varcall;

import java.io.IOException;
import java.util.List;

import org.ngsutils.NGSUtils;
import org.ngsutils.NGSUtilsException;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.support.StringLineReader;
import org.ngsutils.support.StringUtils;
import org.ngsutils.support.TabWriter;
import org.ngsutils.varcall.CallCount;
import org.ngsutils.varcall.VariantCall;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj varcall-germline")
@Command(name="varcall-germline", desc="Call germline variants", cat="variants")
public class GermlineVarCall extends AbstractOutputCommand {
    
    private String pileupFilename=null;
    
    private double minMinorPVal = 0.01;
    private double minStrandedPVal = 0.05;
    private int minBaseQual=13;
    private int minDepth=10;
    private int minCallDepth=5;

    private boolean backgroundCorrect=true;
    
    @Unparsed(name = "FILE")
    public void setFilename(String pileupFilename) {
        this.pileupFilename = pileupFilename;
    }

    @Option(description = "Don't perform background count correction (third minor count, default:true)", longName="nobg")
    public void setNoBackground(boolean val) {
        this.backgroundCorrect = false;
    }

    @Option(description = "Minimum basecall-quality score (default: 13)", longName="base-qual", defaultValue="13")
    public void setMinBaseQual(int minBaseQual) {
        this.minBaseQual = minBaseQual;
    }

    @Option(description = "Minimum coverage depth (default: 10)", longName="depth", defaultValue="10")
    public void setMinDepth(int minDepth) {
        this.minDepth = minDepth;
    }

    @Option(description = "Minimum basecall depth (default: 5)", longName="depth", defaultValue="5")
    public void setMinCallDepth(int minCallDepth) {
        this.minCallDepth = minCallDepth;
    }

    @Override
    public void exec() throws NGSUtilsException, IOException {
        TabWriter writer = new TabWriter();
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + pileupFilename);
        writer.write("#CHROM", "POS", "ID", "REF", "ALT", "ref-count", "alt-count", "alt-freq", "alt-strand-freq", "alt-pvalue", "strand-pvalue");
        writer.eol();


        for (String line: new StringLineReader(this.pileupFilename)) {
            if (line.charAt(0) == '#') {
                writer.write_line(line);
                continue;
            }
            
            String[] cols = StringUtils.strip(line).split("\t",-1);
            if (Integer.parseInt(cols[3]) < minDepth) {
                continue;
            }

            String ref = cols[2].toUpperCase();

            List<CallCount> counts = CallCount.parsePileupString(cols[4], cols[5], ref, minBaseQual, minCallDepth,false);
            VariantCall varcall = VariantCall.callVariants(counts, backgroundCorrect, false);
            
            if (varcall == null) {
                // no variant detectable (low coverage)
                continue;
            }

            boolean output = false;
//            if (cols[1].equals("834")) {
//                System.out.println(varcall);
//            }
            
            if (varcall.major!=null && !ref.equals(varcall.major.getCall())) {
                // if the major call isn't reference
                output = true;                
            }
            
            if (varcall.minor != null && varcall.pvalueCall >= minMinorPVal && varcall.pvalueStrand >= minStrandedPVal)  {
                // if there is a minor call
                //   and it's p-value supports the null hypothesis (heterozygosity)
                //   and there isn't strand-bias
                output = true;
            }

            if (output) {
                writer.write(cols[0], cols[1],".");
                if (varcall.major.getCall().equals(ref) && varcall.minor != null) {
                    // major call is reference, and we have a valid minor
                    
                    if (varcall.minor.isInsert()) {
                        writer.write(ref);
                        writer.write(ref+varcall.minor.getCall().substring(1));
                    } else if (varcall.minor.isDeletion()) {
                        writer.write(ref+varcall.minor.getCall().substring(1));
                        writer.write(ref);
                    } else {
                        writer.write(ref);
                        writer.write(varcall.minor.getCall());
                    }
                    
                    writer.write(varcall.major.getCount());
                    writer.write(varcall.minor.getCount());
                    
                } else if (!varcall.major.getCall().equals(ref) && varcall.minor == null) {
                    // major call isn't reference, and there's no minor call (homozygous alt)
                    writer.write(ref);
                    writer.write(varcall.major.getCall());
                    writer.write(0);
                    writer.write(varcall.major.getCount());
                        
                } else if (varcall.minor.getCall().equals(ref)) {
                    // minor call is reference
                    if (varcall.major.isInsert()) {
                        writer.write(ref);
                        writer.write(ref+varcall.major.getCall().substring(1));
                    } else if (varcall.major.isDeletion()) {
                        writer.write(ref+varcall.major.getCall().substring(1));
                        writer.write(ref);
                    } else {
                        writer.write(ref);
                        writer.write(varcall.major.getCall());
                    }
                    
                    writer.write(varcall.minor.getCount());
                    writer.write(varcall.major.getCount());
                } else {
                    // split this into two lines?
                    writer.write(ref);
                    writer.write(varcall.major.getCall()+","+varcall.minor.getCall());
                    writer.write(0);
                    writer.write(varcall.major.getCount()+","+varcall.minor.getCount());                    
                }
                writer.write(varcall.freqCall);
                writer.write(varcall.freqStrand);
                
                writer.write(varcall.pvalueCall);
                writer.write(varcall.pvalueStrand);
                writer.eol();
//            } else if (varcall!=null && varcall.major!=null) {
//                writer.write(cols[0], cols[1],".",ref,"",""+varcall.major.getCount(),"0","0","0","0","0");
//                writer.eol();
            }
        }
    }
}
