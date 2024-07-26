package io.compgen.ngsutils.cli.vcf;

import java.util.Iterator;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.fasta.IndexedFastaFile;
import io.compgen.ngsutils.support.CloseableFinalizer;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-refbuild", desc="Verify the reference/build for a VCF file", category="vcf")
public class VCFBuild extends AbstractOutputCommand {
	private String vcfFilename = null;
	private String fastaFilename = null;
	private static boolean quiet = false;
    
    @UnnamedArg(name = "input.vcf{.gz} input.fa", required=true)
    public void setFilename(String[] filenames) throws CommandArgumentException {
    	if (filenames == null || filenames.length != 2) {
    		throw new CommandArgumentException("Invalid input!");
    	}
    	vcfFilename = filenames[0];
    	fastaFilename = filenames[1];
    }
	@Exec
	public void exec() throws Exception {

		VCFReader reader = new VCFReader(vcfFilename);
		IndexedFastaFile fasta = new IndexedFastaFile(fastaFilename);

		final int[] total = {0};
		final int[] matches = {0};

		Iterator<VCFRecord> it = null;
		if (VCFBuild.isQuiet()) {
			it = reader.iterator();
		} else {
	        it  = ProgressUtils.getIterator(reader.getFilename(), 
	        		reader.iterator(), 
	        		(reader.getChannel() == null)? null : new FileChannelStats(reader.getChannel()), 
					new ProgressMessage<VCFRecord>() {
			            public String msg(VCFRecord current) {
			                return current.getChrom()+":"+current.getPos() + " " + matches[0] +"/"+ total[0];
			            }}, 
					new CloseableFinalizer<VCFRecord>());
		}
		
		boolean ucsc = false;
		boolean ucscCheck = false;
				
		
		while (it.hasNext()) {
			VCFRecord rec = it.next();
			
			String chrom = rec.getChrom();
			int pos = rec.getPos();
			String ref = rec.getRef().toUpperCase();
			
			if (ref.length()!=1) {
				// skip deletions
				continue;
			}
			
			if (!ucscCheck) {			
				if (!fasta.getReferenceNames().contains(chrom) && !ref.startsWith("chr") && fasta.getReferenceNames().contains("chr" + chrom)) {
					ucsc = true;
					System.err.println("Adding 'chr' to chromosome names to match FASTA file");
				}
				ucscCheck = true;
			}
			
			if (ucsc) {
				chrom = "chr" + chrom;
			}
			
			if (fasta.fetchSequence(chrom, pos-1, pos).toUpperCase().equals(ref)) {
				matches[0]++;
			}
			total[0]++;
		}

		reader.close();
		
		System.out.println("Positions scanned: "+total[0]);
		System.out.println("Positions matched: "+matches[0] + " (" + String.format("%.4f", ((double)matches[0] / total[0]))+")");
		
	}
	public static boolean isQuiet() {
		return quiet;
	}
}