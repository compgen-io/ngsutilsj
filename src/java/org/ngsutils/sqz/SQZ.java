package org.ngsutils.sqz;

public class SQZ {
    final public static byte A = 0; 
    final public static byte C = 1; 
    final public static byte G = 2; 
    final public static byte T = 3; 

    public static final int HAS_COMMENTS        = 0x1;
    public static final int COLORSPACE          = 0x2;

    public static final int COMPRESS_NONE   = 0;
    public static final int COMPRESS_DEFLATE   = 1;
    public static final int COMPRESS_BZIP2     = 2;
    
    // used to mark a valid SQZ file
    public static final byte[] MAGIC = new byte[] { 'S', 'Q', 'Z', 0x1 };
    
    // used to mark the start of the SQZ data chunk
    public static final byte[] MAGIC_CHUNK = new byte[] { 'J', 'R', 0x20, 0x08 };

    // used to mark the start of the SQZ data validator - ensures the encryption password is good.
    public static final byte[] MAGIC_CHUNK_DATA = new byte[] { 'L', 'M', 0x20, 0x10 };

    // used to mark the start of a SQZ text chunk (comments)
    public static final byte[] MAGIC_TEXT_CHUNK = new byte[] { 'E', 'L', 'L', 0x1E };

    private static boolean qualErrorPrinted = false;
    private static boolean wildcardQualErrorPrinted = false;

    
    public static byte[] combineSeqQual(String seq, String qual) throws SQZException {
        if (seq.length() != qual.length()) {
            throw new SQZException("Seq and qual should be the same length!");
        };
        byte[] out = new byte[seq.length()];
        
        for (int i=0; i<seq.length(); i++) {
            char base = seq.charAt(i);
            char qualbase = qual.charAt(i);
            int qualval = qualbase - 33;
            
            if (qualval > 62) {
                if (!qualErrorPrinted) {
                    System.err.println("WARNING: Quality values will be truncated to 62.");
                    qualErrorPrinted = true;
                }
                
                qualval = 62;
            }
            
            byte buf = (byte) (qualval << 2);
            
            switch (base) {
            case 'A':
            case 'a':
                break;
            case 'C':
            case 'c':
                buf |= C;
                break;
            case 'G':
            case 'g':
                buf |= G;
                break;
            case 'T':
            case 't':
                buf |= T;
                break;
            case 'N':
            case 'n':
                // an "N" is set to a qual of 63. However, the actual quality in the FASTQ
                // file might not be '0', (it's probably '2'). So we'll encode the quality in
                // the base bits.
                
                buf = (byte) 0xFC;
                
                if (qualval > 3) {
                    if (!wildcardQualErrorPrinted) {
                        System.err.println("WARNING: Wildcard quality values will be set to 0.");
                        wildcardQualErrorPrinted = true;
                    }
                    qualval = 0;
                }

                buf |= (qualval & 0x3);

                break;
            default:
                throw new SQZException("Sequence not valid! Expected: A,C,G,T or N. Got: " + base);
            }

            out[i] = buf;            
        }
        
        return out;
    }

    public static String[] splitSeqQual(byte[] seqquals) {
        String seq = "";
        String qual = "";
        if (seqquals == null) {
            return new String[]{"",""};
        }
        for (int i=0; i<seqquals.length; i++) {
            byte base = (byte) (seqquals[i] & 0x03);
            byte qualval = (byte) (seqquals[i] >> 2 & 0x3F);
            
            if (qualval == 63) {
                seq += 'N';
                qual += (char)(base + 33);
            } else {
                switch (base) {
                case A:
                    seq += 'A';
                    break;
                case C:
                    seq += 'C';
                    break;
                case G:
                    seq += 'G';
                    break;
                case T:
                    seq += 'T';
                    break;
                }

                qual += (char)(qualval + 33);
            }
        }

        return new String[]{seq, qual};
    }

    public static byte[] combineSeqQualColorspace(String seq, String qual) throws SQZException {
        if (seq.length() != qual.length() + 1) {
            throw new SQZException("Colorspace seq should include one base prefix not in qual.");
        };
        
        byte[] out = new byte[seq.length()];
        
        // pull out the prefix base. prefix is stored w/o quality value.
        switch (seq.charAt(0)) {
        case 'A':
        case 'a':
            out[0] = A;
            break;
        case 'C':
        case 'c':
            out[0] = C;
            break;
        case 'G':
        case 'g':
            out[0] = G;
            break;
        case 'T':
        case 't':
            out[0] = T;
            break;
        default:
            // this must be an error...
            throw new SQZException("Colorspace reads must include a one base-space prefix");
        }
        
        
        for (int i=1; i<seq.length(); i++) {
            char base = seq.charAt(i);
            char qualbase = qual.charAt(i-1);
            int qualval = qualbase - 33;
            
            if (qualval > 62) {
                if (!qualErrorPrinted) {
                    System.err.println("Quality values will be truncated to 62.");
                    qualErrorPrinted = true;
                }
                qualval = 62;
            }
            
            byte buf = (byte) (qualval << 2);
            
            switch (base) {
            case '0':
                break;
            case '1':
                buf |= 0x1;
                break;
            case '2':
                buf |= 0x2;
                break;
            case '3':
                buf |= 0x3;
                break;
            case '4':
            case '5':
            case '6':
            case '.':
                // a 4, 5, 6 is set to a qual of 63. In colorspace files, the quality score for .,4,5,6
                // is always 0
                buf = (byte) 0xFF;
                break;
            default:
                throw new SQZException("Sequence not valid colorspace.");
            }

            out[i] = buf;            
        }
        
        return out;
    }

    public static String[] splitSeqQualColorspace(byte[] seqquals) {
        String seq = "";
        String qual = "";

        // This is the base-space prefix. No qual is stored here.
        switch (seqquals[0]) {
        case A:
            seq += 'A';
            break;
        case C:
            seq += 'C';
            break;
        case G:
            seq += 'G';
            break;
        case T:
            seq += 'T';
            break;
        }
        
        for (int i=1; i<seqquals.length; i++) {
            if (seqquals[i] == 0xFF) {
                seq += '.';
                qual += '!'; // Phred 0 (+33 in ASCII)
            } else {
                byte base = (byte) (seqquals[i] & 0x03);
                byte qualval = (byte) (seqquals[i] >> 2 & 0x3F);

                switch (base) {
                case 0:
                    seq += '0';
                    break;
                case 1:
                    seq += '1';
                    break;
                case 2:
                    seq += '2';
                    break;
                case 3:
                    seq += '3';
                    break;
                }

                qual += (char)(qualval + 33);
            }
        }

        return new String[]{seq, qual};
    }

}
