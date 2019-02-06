package io.compgen.ngsutils.support;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.codec.Hex;

@Command(name="digest-stream", desc="Calculate a hash digest (MD5, SHA1, SHA256) on a stream, writing the contents back to stdout", category="utils", experimental=true)
public class DigestCmd extends AbstractCommand {
    
    private boolean md5 = false;
    private boolean sha1 = false;
    private boolean sha256 = false;
    
    private String digestFilename = null;
    private String outputFilename = null;

    private String[] filenames = null;

    @Option(desc="Calculate MD5 hash (automatically set if -o ends in '.md5')", name="md5")
    public void setMD5(boolean val) {
        this.md5=val;
    }

    @Option(desc="Calculate SHA1 hash (automatically set if -o ends in '.sha1')", name="sha1")
    public void setSHA1(boolean val) {
        this.sha1=val;
    }

    @Option(desc="Calculate SHA256 hash (automatically set if -o ends in '.sha256')", name="sha256")
    public void setSHA256(boolean val) {
        this.sha256=val;
    }
    
    @Option(desc="Write hash to this file (required)", charName="o", name="output")
    public void setDigestFile(String fname) {
        this.digestFilename = fname;
        if (fname.endsWith(".md5")) {
            setMD5(true);
            setOutputFilename(fname.substring(0, fname.length()-4));
        } else if (fname.endsWith(".sha1")) {
            setSHA1(true);
            setOutputFilename(fname.substring(0, fname.length()-5));
        } else if (fname.endsWith(".sha256")) {
            setSHA256(true);
            setOutputFilename(fname.substring(0, fname.length()-7));
        }
    }
    
    @Option(desc="Filename (used if the output is to a pipe, automatically set if -o ends in .md5/.sha1/.sha256)", name="outname", charName="f")
    public void setOutputFilename(String fname) {
        this.outputFilename = fname;
    }
    
    @UnnamedArg(name = "input output (defaults to \"- -\", stdin, stdout)")
    public void setFilenames(String[] filenames) throws CommandArgumentException {
        this.filenames = filenames;
    }

    @Exec
    public void exec() throws Exception {
        if (!md5 && !sha1 && !sha256) {
            throw new CommandArgumentException("You must specify a hash algorithm (md5, sha1, sha256)");
        }
        
        if (digestFilename == null) {
            throw new CommandArgumentException("You must specify an output filename");
        }
        
        MessageDigest md = null;
        if (md5) {
            md = MessageDigest.getInstance("MD5");
        } else if (sha1) {
            md = MessageDigest.getInstance("SHA1");
        } else if (sha1) {
            md = MessageDigest.getInstance("SHA256");
        } else {
            // this shouldn't happen
        }
        
        byte[] buffer = new byte[16*1024];
        int read = 0;
        
        InputStream in;
        OutputStream out;
        
        if (this.filenames == null) {
           in = System.in;
           out = System.out;
        } else {
            if (this.filenames[0].equals("-")) {
                in = System.in;
            } else if (new File(this.filenames[0]).exists()) {
                in = new FileInputStream(this.filenames[0]);
            } else {
                throw new CommandArgumentException("Can't find input file: "+this.filenames[0]);
            }
            
            if (this.filenames.length == 1 || this.filenames[1].equals("-")) {
                out = System.out;
            } else {
                out = new FileOutputStream(this.filenames[1]);
                if (this.outputFilename == null) {
                    this.outputFilename = this.filenames[1];
                }
            }
        }
        
        while (read > -1) {
            read = in.read(buffer, 0, buffer.length);
            if (read > -1) {
                md.update(buffer,0,read);
                out.write(buffer, 0, read);
            }
        }
        
        in.close();
        out.flush();
        out.close();
        
        byte[] digest = md.digest();
        
        String myHash = Hex.toHexString(digest).toLowerCase();
        
        OutputStream digestOS = new FileOutputStream(this.digestFilename);
        
        if (this.outputFilename != null) {
            myHash += "  " + this.outputFilename;
        }
        
        myHash += "\n";
        
        digestOS.write(myHash.getBytes());
        digestOS.flush();
        digestOS.close();
        
        
        
        
    }

}
