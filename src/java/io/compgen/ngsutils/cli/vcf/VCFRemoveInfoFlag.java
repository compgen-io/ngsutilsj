package io.compgen.ngsutils.cli.vcf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFWriter;


@Command(name="vcf-remove-flags", desc="Replace all INFO flags with a comma separated list", category="vcf")
public class VCFRemoveInfoFlag extends AbstractOutputCommand {
	private String filename = "-";

    private String keyName = "FLAGS";
    private boolean alwaysSet = false;
  
    @Option(desc="Name for the new key (FOO;BAR; => {key}=FOO,BAR", name="key", defaultValue="FLAGS")
    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }
    
    @Option(desc="Always include the new key. If no flags set, will use the 'missing' value (.)", name="always")
    public void setAlwaysSet(boolean val) {
        this.alwaysSet = val;
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
		

		VCFHeader header = reader.getHeader().clone();
		
		Set<String> validFlags = new HashSet<String>();
		
		List<VCFAnnotationDef> newinfo = new ArrayList<VCFAnnotationDef>();
		for (String id: header.getInfoIDs()) {
			VCFAnnotationDef infoDef = header.getInfoDef(id);
			if (!infoDef.type.toUpperCase().equals("FLAG")) {
				newinfo.add(infoDef);
			} else {
				validFlags.add(id);
			}
		}

		if (validFlags.size() == 0) {
			throw new CommandArgumentException("No INFO Flags defined in VCF!");
		}
		
		newinfo.add(VCFAnnotationDef.info(keyName, ".", "String", "INFO Flag values as CSV (" + StringUtils.join(",", validFlags)+")"));

		header.clearInfoDefs();
		for (VCFAnnotationDef def: newinfo) {
			header.addInfo(def);
		}
		
		header.addLine("##ngsutilsj_vcf_stripCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_stripVersion="+NGSUtils.getVersion())) {
		    header.addLine("##ngsutilsj_vcf_stripVersion="+NGSUtils.getVersion());
		}
	

		VCFWriter writer = new VCFWriter(out, header);
//		VCFWriter writer;
//		if (out.equals("-")) {
//			writer = new VCFWriter(System.out, header);
//		} else {
//			writer = new VCFWriter(out, header);
//		}

		for (VCFRecord rec: IterUtils.wrap(reader.iterator())) {
			List<String> setFlags = new ArrayList<String>();
			for (String id:rec.getInfo().getKeys()) {
				if (validFlags.contains(id)) {
					setFlags.add(id);
				}
			}

			if (setFlags.size() >0) {
				for (String id:setFlags) {
					rec.getInfo().remove(id);
				}
				
				rec.getInfo().put(keyName, new VCFAttributeValue(StringUtils.join(",", setFlags)));
			} else if (alwaysSet) {
				rec.getInfo().put(keyName, VCFAttributeValue.MISSING);
			}
            writer.write(rec);
		}		
		reader.close();
		writer.close();
	}

}
