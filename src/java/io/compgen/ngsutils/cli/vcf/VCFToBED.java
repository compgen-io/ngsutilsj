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


@Command(name="vcf-tobed", desc="Export allele positions from a VCF file to BED format", category="vcf")
public class VCFToBED extends AbstractOutputCommand {
	private String filename = "-";
	
	private boolean onlyOutputPass = false;
	private boolean includePos = false;
	private int padding = 0;
	
    @Option(desc="Include the position as the name field (w/o padding)", name="include-pos")
    public void setIncludePos(boolean includePos) {
    	this.includePos = includePos;
    }
    
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
    	this.onlyOutputPass = onlyOutputPass;
    }
    
    @Option(desc="Add extra padding on either side", name="padding")
    public void setPadding(int padding) {
    	this.padding = padding;
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
		
		TabWriter writer = new TabWriter();
		
		writer.write_line("##ngsutilsj_vcf_tobedCommand="+NGSUtils.getArgs());
        writer.write_line("##ngsutilsj_vcf_tobedVersion="+NGSUtils.getVersion());

		Iterator<VCFRecord> it = reader.iterator();
		
		for (VCFRecord rec: IterUtils.wrap(it)) {
			if (onlyOutputPass && rec.isFiltered()) {
				continue;
			}

			writer.write(rec.getChrom());
			writer.write(((rec.getPos()-1)-padding));
			writer.write((rec.getPos()+padding));
			if (includePos) {
				writer.write(rec.getChrom()+"_"+rec.getPos());
			}
			writer.eol();
		}
		
		reader.close();
		writer.close();
	}

}
