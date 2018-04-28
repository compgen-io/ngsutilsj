package io.compgen.ngsutils.vcf.export;

import java.util.List;

import io.compgen.common.ListBuilder;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public class FilterExport implements VCFExport {
    protected String missingValue=".";
    
    @Override
    public List<String> getFieldNames() {
        return ListBuilder.build(new String[] {"FILTER"});
    }

    @Override
    public void export(VCFRecord rec, List<String> outs) throws VCFExportException {
        if (!rec.isFiltered()) {
            outs.add("PASS");
        } else {
            outs.add(StringUtils.join(",", rec.getFilters()));
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
