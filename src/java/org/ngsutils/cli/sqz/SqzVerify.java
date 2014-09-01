package org.ngsutils.cli.sqz;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Iterator;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.cli.AbstractCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.fastq.FastqRead;
import org.ngsutils.sqz.SQZReader;
import org.ngsutils.support.StringUtils;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj sqz-verify")
@Command(name="sqz-verify", desc="Verify that the SQZ file is valid.", cat="sqz", experimental=true)
public class SqzVerify extends AbstractCommand {
    
    private String filename=null;
    private String password = null;

    @Unparsed(name = "INFILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "Decryption password", longName = "pass", defaultToNull=true)
    public void setPassword(String password) {
        this.password = password;
    }
    


    @Override
    public void exec() throws NGSUtilsException, IOException, GeneralSecurityException {        
        if (filename == null) {
            throw new ArgumentValidationException("You must specify an input FQA file!");
        }

        try {
            SQZReader reader;
            if (filename.equals("-")) {
                reader = SQZReader.open(System.in, false, password);
                if (verbose) {
                    System.err.println("Input: stdin");
                }
            } else {
                reader = SQZReader.open(filename, false, password);
                if (verbose) {
                    System.err.println("Input: " + filename);
                }
            }
            
            if (verbose) {
                System.err.println("SQZ version: "+reader.getHeader().major+"."+reader.getHeader().minor);
                System.err.println("Compressed: "+(reader.getHeader().deflate ? "deflate" : "none"));
                System.err.println("Encrypted: "+(reader.getHeader().encryption == null ? "no" : reader.getHeader().encryption));
                System.err.println("Includes comments: "+(reader.getHeader().hasComments ? "yes" : "no"));
                System.err.println("Colorspace: "+(reader.getHeader().colorspace ? "yes" : "no"));
                if (reader.getHeader().colorspace) {
                    System.err.println("Colorspace includes prefix: "+(reader.getHeader().colorspacePrefix ? "yes" : "no"));
                }
                System.err.println("Reads per fragment: " + reader.getHeader().seqCount);
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

            reader.close(); // this throws an IOException if the SHA1 hashes don't match

            if (verbose) {
                System.err.println("Fragments: "+i);
                System.err.println("Calculated SHA1: "+ StringUtils.digestToString(reader.getCalcDigest()));
                System.err.println("Expected   SHA1: "+ StringUtils.digestToString(reader.getExpectedDigest()));
            }

            if (!Arrays.equals(reader.getCalcDigest(), reader.getExpectedDigest())) {
                throw new IOException("SHA1 hash mismatch!");
            }
            
        } catch (IOException e) {
            if (verbose) {
                System.err.println(e.getMessage());
            }
            System.err.println((filename.equals("-") ? "stdin": filename) + " is not valid");
            System.exit(1);
        }

        System.err.println((filename.equals("-") ? "stdin": filename) + " is valid");
    }    
}
