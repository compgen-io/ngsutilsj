package org.ngsutils.cli.sqz;

import java.io.IOException;
import java.util.Iterator;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.cli.AbstractCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.fastq.FastqRead;
import org.ngsutils.sqz.SQZReader;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj sqz-verify")
@Command(name="sqz-verify", desc="Verify that the SQZ file is valid.", cat="sqz", experimental=true)
public class SqzVerify extends AbstractCommand {
    
    private String filename=null;

    @Unparsed(name = "INFILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }


    @Override
    public void exec() throws NGSUtilsException, IOException {        
        if (filename == null) {
            throw new ArgumentValidationException("You must specify an input FQA file!");
        }

        try {
            SQZReader reader;
            if (filename.equals("-")) {
                reader = SQZReader.open(System.in, false);
                if (verbose) {
                    System.err.println("Input: stdin");
                }
            } else {
                reader = SQZReader.open(filename, false);
                if (verbose) {
                    System.err.println("Input: " + filename);
                }
            }
            
            
            if (verbose) {
                System.err.println("SQZ version: "+reader.getHeader().major+"."+reader.getHeader().minor);
                System.err.println("Compressed: "+(reader.getHeader().deflate ? "deflate" : "none"));
                System.err.println("Encrypted: "+(reader.getHeader().encryption == null ? "no" : reader.getHeader().encryption));
                System.err.println("Paired: "+(reader.getHeader().paired ? "yes" : "no"));
                System.err.println("Includes comments: "+(reader.getHeader().hasComments ? "yes" : "no"));
                System.err.println("Colorspace: "+(reader.getHeader().colorspace ? "yes" : "no"));
                if (reader.getHeader().colorspace) {
                    System.err.println("Colorspace includes prefix: "+(reader.getHeader().colorspacePrefix ? "yes" : "no"));
                }
            }
            
            long i=0;
    
            Iterator<FastqRead>it = reader.iterator();
            while (it.hasNext()) {
                if (verbose) {
                    i++;
                    if (i % 100000 == 0) {
                        System.err.println("Read: " + i);
                    }
                    
                }
                it.next();
            }
            reader.close();
            if (verbose) {
                System.err.println("Reads (first and second): "+i);
            }

        } catch (IOException e) {
            System.err.println((filename.equals("-") ? "stdin": filename) + " is not a valid SQZ file!");
            System.exit(1);
        }
        System.err.println((filename.equals("-") ? "stdin": filename) + " is valid.");
    }    
}
