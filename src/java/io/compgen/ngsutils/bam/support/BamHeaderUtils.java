package io.compgen.ngsutils.bam.support;

import java.util.List;
import java.util.Map.Entry;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMProgramRecord;
import htsjdk.samtools.SAMReadGroupRecord;
import io.compgen.ngsutils.NGSUtils;

public class BamHeaderUtils {

    public static SAMProgramRecord buildSAMProgramRecord(String prog) {
        return BamHeaderUtils.buildSAMProgramRecord(prog, (List<SAMProgramRecord>) null);
    }

    public static SAMProgramRecord buildSAMProgramRecord(String prog, SAMFileHeader header) {
        return buildSAMProgramRecord(prog, header.getProgramRecords());
    }

    public static SAMProgramRecord buildSAMProgramRecord(String prog, List<SAMProgramRecord> records) {
        String pgTemplate = "ngsutilsj:" + prog + "-";
        String pgID = pgTemplate;
        boolean found = true;
        int i = 0;
        
        SAMProgramRecord mostRecent = null;
        
        while (found) {
            found = false;
            i++;
            pgID = pgTemplate + i;
            if (records!=null) {
                for (SAMProgramRecord record: records) {
                    if (mostRecent == null && (record.getPreviousProgramGroupId() == null || record.getPreviousProgramGroupId().equals(""))) {
                       mostRecent = record;
                    }
                    
                    if (record.getId().equals(pgID)) {
                        found = true;
                    }
                }
            }
        }

        SAMProgramRecord programRecord = new SAMProgramRecord(pgID);
        programRecord.setProgramName("ngsutilsj:"+prog);
        programRecord.setProgramVersion(NGSUtils.getVersion());
        programRecord.setCommandLine("ngsutilsj " + NGSUtils.getArgs());
        if (mostRecent!=null) {
            programRecord.setPreviousProgramGroupId(mostRecent.getId());
        }
        return programRecord;
    }

    public static SAMProgramRecord suffixAddSAMProgramRecord(SAMProgramRecord existing, String suffix) {
        SAMProgramRecord pg = new SAMProgramRecord(existing.getId()+suffix);
        for (Entry<String, String> k: existing.getAttributes()) {
            if (k.getKey().equals(SAMProgramRecord.PREVIOUS_PROGRAM_GROUP_ID_TAG)) {
                pg.setAttribute(k.getKey(), k.getValue()+suffix);
            }
            pg.setAttribute(k.getKey(), k.getValue());
        }
        return pg;
    }
    
    public static SAMReadGroupRecord suffixAddSAMReadGroupRecord(SAMReadGroupRecord existing, String suffix) {
        SAMReadGroupRecord rg = new SAMReadGroupRecord(existing.getId()+suffix);
        for (Entry<String, String> k: existing.getAttributes()) {
            rg.setAttribute(k.getKey(), k.getValue());
        }
        return rg;
    }
     
    public static SAMReadGroupRecord SAMReadGroupRecordNewID(SAMReadGroupRecord existing, String newID) {
        SAMReadGroupRecord rg = new SAMReadGroupRecord(newID);
        for (Entry<String, String> k: existing.getAttributes()) {
            if (!k.getKey().equals(SAMReadGroupRecord.READ_GROUP_ID_TAG)) {
                rg.setAttribute(k.getKey(), k.getValue());
            }
        }
        return rg;
    }
     
}
