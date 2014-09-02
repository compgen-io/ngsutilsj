package org.ngsutils.cli;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

import com.lexicalscope.jewel.cli.Option;

public abstract class AbstractOutputCommand extends AbstractCommand {
    protected OutputStream out = System.out;

    @Option(description="Output filename (optionally gzip/bzip2 compressed) (default: stdout)", shortName="o", defaultValue="-", longName="output")
    public void setOutputName(String outputName) throws IOException {
        if (outputName.equals("-")) {
            out = System.out;
        } else if (outputName.endsWith(".gz")) {
            out = new GZIPOutputStream(new FileOutputStream(outputName));
        } else if (outputName.endsWith(".bz") || outputName.endsWith(".bz2")) {
            out = new BZip2CompressorOutputStream(new FileOutputStream(outputName));
        } else {
            out = new BufferedOutputStream(new FileOutputStream(outputName));
        }
    }
    
    public void close() throws IOException {
        if (out != System.out) {
            out.close();
        }
    }
}
