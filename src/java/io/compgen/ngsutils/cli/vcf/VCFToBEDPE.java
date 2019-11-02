package io.compgen.ngsutils.cli.vcf;

import java.util.Iterator;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-tobedpe", desc="Convert a SV VCF file to BEDPE format", category="vcf")
public class VCFToBEDPE extends AbstractOutputCommand {
	private String filename = "-";
	
	private boolean onlyOutputPass = false;
    private String altChrom = null;
    private String altPos = null;
    private String nameKey = null;
    private String scoreKey = null;
    
    @Option(desc="Use an alternate INFO field for the chromosome (ex: SV). If missing, skip annotation.", name="alt-chrom", defaultValue="CHR2")
    public void setAltChrom(String key) throws CommandArgumentException {
        this.altChrom = key;
    }
    
    @Option(desc="Use an alternate INFO field for the position (ex: SV). If missing, skip annotation.", name="alt-pos", defaultValue="END")
    public void setAltPos(String key) throws CommandArgumentException {
        this.altPos = key;
    }

    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
        this.onlyOutputPass = onlyOutputPass;
    }
    
    @Option(desc="Add this INFO field (or @ID for ID) as the BEDPE NAME", name="name")
    public void setName(String nameKey) {
        this.nameKey = nameKey;
    }
    
    @Option(desc="Add this INFO/FORMAT field as the BEDPE score (KEY{:SAMPLE:ALLELE}, if SAMPLE is given, a FORMAT field will be used. SAMPLE can also be a number 1,2...)", name="score")
    public void setScore(String scoreKey) {
        this.scoreKey = scoreKey;
    }
    
    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

	@Exec
	public void exec() throws Exception {		
		VCFReader reader;
		if (filename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(filename);
		}
		
         
		Iterator<VCFRecord> it = reader.iterator();
		
        String scoreKey2 = null;
        String scoreSample = null;
		String scoreAllele = null;
		int scoreSampleIdx = -1;
		
		if (scoreKey != null) {
		    String[] spl = scoreKey.split(":");
		    
		    scoreKey2 = spl[0];
		    
            if (spl.length > 1) {
                scoreSample = spl[1];
                scoreSampleIdx = reader.getHeader().getSamplePosByName(scoreSample);
                
                if (scoreSampleIdx == -1) {
                    throw new CommandArgumentException("Can't parse --score value: " + scoreKey);
                }
                
            }
            if (spl.length > 2) {
                scoreAllele = spl[2];
            }
		    
		}
		
        TabWriter writer = new TabWriter();
        
        writer.write_line("##ngsutilsj_vcf_tobedpeCommand="+NGSUtils.getArgs());
        writer.write_line("##ngsutilsj_vcf_tobedpeVersion="+NGSUtils.getVersion());

        writer.write("#chrom1");
        writer.write("start1");
        writer.write("stop1");
        writer.write("chrom2");
        writer.write("start2");
        writer.write("stop2");
        if (nameKey != null || scoreKey != null) {
            writer.write("name");
        }
        
        if (scoreKey != null) {
            writer.write("score");
        }
        
        writer.eol();
        
		for (VCFRecord rec: IterUtils.wrap(it)) {
			if (onlyOutputPass && rec.isFiltered()) {
				continue;
			}

            String chrom = rec.getChrom();
            int pos = rec.getPos();

            String chrom2 = rec.getInfo().get(altChrom).toString();
            int pos2 = rec.getInfo().get(altPos).asInt();
			
			
            writer.write(chrom);
            writer.write(((pos-1)));
            writer.write((pos));

            writer.write(chrom2);
            writer.write(((pos2-1)));
            writer.write((pos2));

			if (nameKey != null) {
			    if (nameKey.equals("@ID")) {
			        writer.write(rec.getDbSNPID());
			    } else {
			        writer.write(rec.getInfo().get(nameKey).toString());
			    }
			}
			
			if (scoreKey2 != null) {
			    if (nameKey == null) {
			        // pad the output if necessary
			        writer.write("");
			    }
			    
			    if (scoreSample == null || scoreSample.equals("INFO")) {

                    writer.write(rec.getInfo().get(scoreKey2).toString());
			    } else {
                    writer.write(rec.getSampleAttributes().get(scoreSampleIdx).get(scoreKey2).asString(scoreAllele));
			    }
			}
			
			writer.eol();
		}
		
		reader.close();
		writer.close();
	}

}
