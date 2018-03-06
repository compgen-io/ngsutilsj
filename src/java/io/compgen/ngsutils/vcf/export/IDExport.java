package io.compgen.ngsutils.vcf.export;

import java.util.List;

import io.compgen.common.ListBuilder;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public class IDExport implements VCFExport {
    protected String missingValue=".";
    
    @Override
    public List<String> getFieldNames() {
        return ListBuilder.build(new String[] {"ID"});
    }

    @Override
    public void export(VCFRecord rec, List<String> outs) throws VCFExportException {
        if (rec.getDbSNPID() == null || rec.getDbSNPID().equals("")) {
            outs.add(missingValue);
        } else {
            outs.add(rec.getDbSNPID());
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
