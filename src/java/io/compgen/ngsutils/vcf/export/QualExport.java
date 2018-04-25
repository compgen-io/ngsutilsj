package io.compgen.ngsutils.vcf.export;

import java.util.List;

import io.compgen.common.ListBuilder;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public class QualExport implements VCFExport {
    protected String missingValue=".";
    
    @Override
    public List<String> getFieldNames() {
        return ListBuilder.build(new String[] {"QUAL"});
    }

    @Override
    public void export(VCFRecord rec, List<String> outs) throws VCFExportException {
        if (rec.getQual() == -1) {
            outs.add(missingValue);
        } else {
            outs.add(""+rec.getQual());
        }
    }

    @Override
    public void setHeader(VCFHeader header) {
    }

    @Override
    public void setMissingValue(String missingValue) {
        this.missingValue = missingValue;
    }

}
