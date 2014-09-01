package org.ngsutils.sqz;

public class SQZ {
    final public static byte A = 0; 
    final public static byte C = 1; 
    final public static byte G = 2; 
    final public static byte T = 3; 

    public static final int DEFLATE_COMPRESSED  = 0x1;
    public static final int HAS_COMMENTS        = 0x2;
    public static final int COLORSPACE          = 0x4;
    public static final int COLORSPACE_PREFIX   = 0x8;
    
    public static final byte[] MAGIC = new byte[] { 'S', 'Q', 'Z', '1' };
    public static final byte[] DATA_MAGIC = new byte[] { 'S', 'Q', 'Z', 'B' };

    
    public static byte[] combineSeqQual(String seq, String qual) {
        byte[] out = new byte[seq.length()];
        
        for (int i=0; i<seq.length(); i++) {
            char base = seq.charAt(i);
            char qualbase = qual.charAt(i);
            int qualval = qualbase - 33;
            
            if (qualval > 62) {
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
            default:
                // an "N" is set to a qual of 63. However, the actual quality in the FASTQ
                // file might not be '0', (it's probably '2'). So we'll encode the quality in
                // the base bits.
                
                buf = (byte) 0xFC;
                buf |= (qualval & 0x3);

                break;
                    
            }

            out[i] = buf;            
        }
        
        return out;
    }

    public static String[] splitSeqQual(byte[] seqquals) {
        String seq = "";
        String qual = "";
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


}
