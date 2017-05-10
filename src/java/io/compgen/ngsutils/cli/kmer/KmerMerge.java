package io.compgen.ngsutils.cli.kmer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.kmer.KmerLineReader;
import io.compgen.ngsutils.kmer.KmerRecord;

@Command(name="kmer-merge", desc="Merges kmer count files", category="kmer", experimental=true)
public class KmerMerge extends AbstractOutputCommand {
    public static final int MAX_FILES = 50;
        
    private List<File> files =  null;
    private File tmpdir = null;

    @UnnamedArg(name="FILE1...")
    public void setFilenames(String[] filenames) throws IOException {
        this.files = new ArrayList<File>();
        for (String fname: filenames) {
            this.files.add(new File(fname));
        }
    }


    @Option(desc="Write temporary files to this directory", name="tmp")
    public void setTmpdir(String tmpdir) {
        this.tmpdir = new File(tmpdir);
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (files == null) {
            throw new CommandArgumentException("You must supply at least two Kmer files to merge.");
        }
        
        while (files.size() > KmerMerge.MAX_FILES) {
            File temp = newTempFile();
            OutputStream os = new FileOutputStream(temp);
            KmerMerge.mergeFiles(os, files.subList(0, KmerMerge.MAX_FILES));

            for (File f : files.subList(0, KmerMerge.MAX_FILES)) {
                f.delete();
            }
            
            files = files.subList(KmerMerge.MAX_FILES, files.size());
            files.add(temp);
        }
        
        KmerMerge.mergeFiles(out, files);

    }

    
    private File newTempFile() throws IOException {
        File temp;
        if (tmpdir == null) {
            temp = Files.createTempFile(".fastq-kmer-", ".tmp").toFile();
        } else {
            temp = Files.createTempFile(tmpdir.toPath(), ".fastq-kmer-", ".tmp").toFile();
        }
        temp.setReadable(true, true);
        temp.setWritable(true, true);
        temp.setExecutable(false, false);
        temp.deleteOnExit();
        return temp;
    }

    public static void mergeFiles(OutputStream out, List<File> files) throws IOException {
        List<KmerLineReader> readers = new ArrayList<KmerLineReader>();
        List<Iterator<KmerRecord>> its = new ArrayList<Iterator<KmerRecord>>();
        List<KmerRecord> buffer = new ArrayList<KmerRecord>();

        for (File tmp: files) {
            KmerLineReader klr = new KmerLineReader(tmp);
            readers.add(klr);
            its.add(klr.iterator());
            buffer.add(null);
        }
        
        boolean done = false;
        
        while (!done) {
            String bestKmer = null;
            done = true;
            for (int i=0; i<readers.size(); i++) {
                KmerRecord cur = null;
                if (buffer.get(i) == null) {
                    if (its.get(i).hasNext()) {
                        cur = its.get(i).next();
                        buffer.set(i, cur);
                    }
                }
                
                if (cur != null && (bestKmer == null || buffer.get(i).kmer.compareTo(bestKmer) < 0)) {
                    bestKmer = cur.kmer;
                }
            }

            if (bestKmer != null) {
                done = false;
                KmerRecord acc = new KmerRecord(bestKmer, 0);

                for (int i=0; i<buffer.size(); i++) {
                    if (buffer.get(i).kmer.equals(bestKmer)) {
                        acc.merge(buffer.get(i));
                        buffer.set(i, null);
                    }
                }
                
                acc.write(out);
            }
        }

        for (KmerLineReader r: readers) {
            r.close();            
        }
    }

    public static void mergeFilesByName(OutputStream out, List<String> filenames) throws IOException {
        List<File> files = new ArrayList<File>();
        for (String fname: filenames) {
            files.add(new File(fname));
        }
        mergeFiles(out, files);
    }

}
