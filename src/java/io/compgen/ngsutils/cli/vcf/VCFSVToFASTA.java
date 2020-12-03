package io.compgen.ngsutils.cli.vcf;

import java.io.File;
import java.io.PrintStream;
import java.util.Iterator;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.support.SeqUtils;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-svtofasta", desc="Extract SV flanking sequences and write them to a FASTA file.", category="vcf")
public class VCFSVToFASTA extends AbstractOutputCommand {
	private String fastaFilename = null;
	private String vcfFilename = "-";
	
	private boolean includeReference = false;
	private boolean onlyOutputPass = false;
	private int flankingLength = 1000;
    private String svType = null;
    private String svCT = null;
    private String altChrom = null;
    private String altPos = null;
    
    
    @Option(desc="Use an alternate INFO field for the chromosome (ex: SV). If missing, skip annotation.", name="alt-chrom", defaultValue="CHR2")
    public void setAltChrom(String key) throws CommandArgumentException {
        this.altChrom = key;
    }
    
    @Option(desc="Use an alternate INFO field for the position (ex: SV). If missing, skip annotation.", name="alt-pos", defaultValue="END")
    public void setAltPos(String key) throws CommandArgumentException {
        this.altPos = key;
    }

    @Option(desc="Use an alternate INFO field for the connection type (valid: 5to5,5to3,3to3,3to5). If missing, skip annotation.", name="ct", defaultValue="CT")
    public void setCT(String key) throws CommandArgumentException {
        this.svCT = key;
    }

    @Option(desc="Use an alternate INFO field for the SV type (valid: BND,DEL,INS,INV). If missing, skip annotation.", name="svtype", defaultValue="SVTYPE")
    public void setSVType(String key) throws CommandArgumentException {
        this.svType = key;
    }

    @Option(desc="Amount of flanking basees to include", name="flanking", defaultValue="1000")
    public void setFlankingLength(int flankingLength) {
        this.flankingLength = flankingLength;
    }
    
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
        this.onlyOutputPass = onlyOutputPass;
    }
    
    @Option(desc="Also write wild-type reference sequences", name="include-ref")
    public void setIncludeReference(boolean includeReference) {
        this.includeReference = includeReference;
    }
    
    @UnnamedArg(name = "genome.fa input.vcf", required=true)
    public void setFilename(String[] filenames) throws CommandArgumentException {
    	this.fastaFilename = filenames[0];
    	this.vcfFilename = filenames[1];
    	
    	if (!new File(this.fastaFilename+".fai").exists()) { 
    		throw new CommandArgumentException("Missing FAI index for: "+this.fastaFilename);
    	}
    }

	@Exec
	public void exec() throws Exception {		
		VCFReader reader;
		if (vcfFilename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(vcfFilename);
		}
		
		FastaReader fasta = FastaReader.open(fastaFilename);
         
		Iterator<VCFRecord> it = reader.iterator();

		PrintStream ps = new PrintStream(out);
		
		for (VCFRecord rec: IterUtils.wrap(it)) {
			if (onlyOutputPass && rec.isFiltered()) {
				continue;
			}

            String chrom = rec.getChrom();
            int pos = rec.getPos() - 1;

            if (rec.getInfo().get(altPos) == null) {
            	continue;
            }
            
            String chrom2 = rec.getInfo().get(altChrom).toString();
            int pos2 = rec.getInfo().get(altPos).asInt()-1;

            String svTypeValue = rec.getInfo().get(svType).toString();
            String ctValue = rec.getInfo().get(svCT).toString();
            
            if (chrom2 == null || svTypeValue == null || ctValue == null) {
            	continue;
            }
            
            if (svTypeValue.equals("BND") || svTypeValue.equals("TRA")) {
            	String flankA;
            	String flankB;
            	String seq = "";
            	
            	if (ctValue.equals("3to5")) {
            		flankA = fasta.fetchSequence(chrom, pos - this.flankingLength, pos);
            		flankB = fasta.fetchSequence(chrom2, pos2, pos2 + this.flankingLength);
            		seq = flankA + flankB;

            	} else if (ctValue.equals("5to3")) {
            		flankA = fasta.fetchSequence(chrom, pos, pos + this.flankingLength);
            		flankB = fasta.fetchSequence(chrom2, pos2 - this.flankingLength, pos2);
            		seq = flankB + flankA;

            	} else if (ctValue.equals("5to5")) {
            		flankA = fasta.fetchSequence(chrom, pos, pos + this.flankingLength);
            		flankB = fasta.fetchSequence(chrom2, pos2, pos2 + this.flankingLength);
            		seq = SeqUtils.revcomp(flankB) + flankA;

            	} else if (ctValue.equals("3to3")) {
            		flankA = fasta.fetchSequence(chrom, pos - this.flankingLength, pos);
            		flankB = fasta.fetchSequence(chrom2, pos2 - this.flankingLength, pos2);
            		seq = flankA + SeqUtils.revcomp(flankB);
            		
            	} else {
            		continue;
            	}
            	
            	ps.println(">sv|" + chrom + "|" + pos + "|" + chrom2 + "|" + pos2 + "|" + rec.getDbSNPID() + "|" + svTypeValue + "|" + ctValue);
            	ps.println(seq);
            	
            	if (includeReference) {
                	ps.println(">ref|" + chrom + "|" + (pos - this.flankingLength) + "|" + (pos + this.flankingLength) + "|" + rec.getDbSNPID() + "|" + svTypeValue + "|" + ctValue + "|A");
                	ps.println(fasta.fetchSequence(chrom, pos - this.flankingLength, pos + this.flankingLength));
                	if (ctValue.equals("3to5") || ctValue.equals("5to3")) {
	                	ps.println(">ref|" + chrom2 + "|" + (pos2 - this.flankingLength) + "|" + (pos2 + this.flankingLength) + "|" + rec.getDbSNPID() + "|" + svTypeValue + "|" + ctValue + "|B");
	                	ps.println(fasta.fetchSequence(chrom, pos2 - this.flankingLength, pos2 + this.flankingLength));
                	} else {
	                	ps.println(">ref|" + chrom2 + "|" + (pos2 + this.flankingLength) + "|" + (pos2 - this.flankingLength) + "|" + rec.getDbSNPID() + "|" + svTypeValue + "|" + ctValue + "|B");
	                	ps.println(SeqUtils.revcomp(fasta.fetchSequence(chrom, pos2 - this.flankingLength, pos2 + this.flankingLength)));
	            	}
            	}

            }
            
		}
		
		reader.close();
		fasta.close();
		ps.close();
	}

}
