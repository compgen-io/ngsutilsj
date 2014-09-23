package org.ngsutils.cli.sqz;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.cli.AbstractCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.sqz.SQZReader;
import org.ngsutils.support.StringUtils;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj sqz-text")
@Command(name="sqz-text", desc="Extract text annotation from SQZ file.", cat="sqz", experimental=true)
public class SqzText extends AbstractCommand {
    
    private String filename = null;
    private String password = null;
    private String passwordFile = null;
    private boolean listOnly = false;

    private String textName = null;

    @Unparsed(name = "INFILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "Just list names of the annotation blocks", longName = "list")
    public void setList(boolean val) {
        this.listOnly = val;
    }
    
    @Option(description = "Name of the text annotation to fetch (default: all)", longName = "name", defaultToNull=true)
    public void setTextName(String textName) {
        this.textName = textName;
    }
    
    @Option(description = "Decryption password", longName = "pass", defaultToNull=true)
    public void setPassword(String password) {
        this.password = password;
    }
    
    @Option(description = "File containing decryption password", longName = "pass-file", defaultToNull=true)
    public void setPasswordFile(String passwordFile) {
        this.passwordFile = passwordFile;
    }

    @Override
    public void exec() throws NGSUtilsException, IOException, GeneralSecurityException  {        
        if (filename == null) {
            throw new ArgumentValidationException("You must specify an input FQA file!");
        }
        if (password == null && passwordFile != null) {
            password = StringUtils.strip(new BufferedReader(new FileReader(passwordFile)).readLine());
        }

        SQZReader reader;
        if (filename.equals("-")) {
            reader = SQZReader.open(System.in, false, password, verbose);
            if (verbose) {
                System.err.println("Input: stdin");
            }
        } else {
            reader = SQZReader.open(filename, false, password, verbose);
            if (verbose) {
                System.err.println("Input: " + filename);
            }
        }
        
        reader.fetchText();
        reader.close();
        
        if (reader.getException() != null) {
            System.err.println(reader.getException().getMessage());
            System.err.println((filename.equals("-") ? "stdin": filename) + " is not valid!");
            System.exit(1);
        }

        if (reader.getTextNames().size() > 0) {
            if (listOnly) {
                for (String name: reader.getTextNames()) {
                    System.out.println(name);
                }
            } else if (textName == null) {
                for (String name: reader.getTextNames()) {
                    System.out.println("["+name+"]");
                    System.out.println(reader.getText(name));
                }
            } else {
                System.out.println(reader.getText(textName));
            }
        }
    }    
}
