package org.ngsutils.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;
import org.ngsutils.support.io.DataOutput;

public class DataOutputStreamTest {

    @Test
    public void testWriteByte() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutput dos = new DataOutput(baos);
        try {
            dos.writeRawByte((byte) 0xFF);
            dos.writeRawByte((byte) 0xFE);
            dos.writeRawByte((byte) 0xEF);
            dumpByteArray(baos.toByteArray());

            assertTrue(Arrays.equals(baos.toByteArray(), new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xEF}));
            
        } catch (IOException e) {
            assertTrue(false);
        }
    }


    @Test
    public void testWriteUint() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutput dos = new DataOutput(baos);
        try {
            dos.writeUInt32(1);
            dumpByteArray(baos.toByteArray());

            assertEquals(4, baos.toByteArray().length);
            assertTrue(Arrays.equals(baos.toByteArray(), new byte[]{(byte) 0, (byte) 0, (byte)0, (byte)1}));

            dos.writeUInt32(2880291034L);
            dumpByteArray(baos.toByteArray());

            assertEquals(8, baos.toByteArray().length);
            assertTrue(Arrays.equals(baos.toByteArray(), new byte[]{(byte) 0, (byte) 0, (byte)0, (byte)1, (byte) 0xAB, (byte) 0xAD, (byte)0xC0, (byte)0xDA}));

        } catch (IOException e) {
            assertTrue(false);
        }
    }

    @Test
    public void testWriteVarInt() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutput dos = new DataOutput(baos);

            dos.writeVarInt(42);
            dumpByteArray(baos.toByteArray());

            assertEquals(1, baos.toByteArray().length);
            assertTrue(Arrays.equals(baos.toByteArray(), new byte[]{(byte) 0x2A}));

            baos = new ByteArrayOutputStream();
            dos = new DataOutput(baos);
            
            dos.writeVarInt(300);
            dumpByteArray(baos.toByteArray());

            assertEquals(2, baos.toByteArray().length);
            assertTrue(Arrays.equals(baos.toByteArray(), new byte[]{(byte) 0xAC, (byte)0x02}));

        } catch (IOException e) {
            assertTrue(false);
        }
    }

    public static void dumpByteArray(byte[] b) {
        for (int i=0; i< b.length; i++) {
            if (i % 16 == 0) {
                System.err.println("");
            } else if (i % 4 == 0) {
                System.err.print(" ");
            }

            System.err.print(String.format("%02X ", b[i]));
            System.err.print(" ");
            
        }
        System.err.println("");
    }
    
}
