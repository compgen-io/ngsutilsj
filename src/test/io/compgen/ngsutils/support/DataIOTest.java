package io.compgen.ngsutils.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.compgen.ngsutils.support.io.DataIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

public class DataIOTest {

    @Test
    public void testReadByte() {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xEF});
        try {
            byte b = DataIO.readByte(bais);
            assertEquals((byte)0xFF, b);
            b = DataIO.readByte(bais);
            assertEquals((byte)0xFE, b);
            b = DataIO.readByte(bais);
            assertEquals((byte)0xEF, b);
            
        } catch (IOException e) {
            assertTrue(false);
        }
    }

    @Test
    public void testEndianRead() {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[]{(byte) 0x0D, (byte) 0x0C, (byte) 0x0B, (byte) 0x0A});
        try {
            long val = DataIO.readUint32(bais);
            assertEquals(0x0A0B0C0D, val);
        } catch (IOException e) {
            assertTrue(false);
        }
    }

    @Test
    public void testEndianWrite() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4);
        try {
            DataIO.writeUInt32(baos, 0x0A0B0C0D);
            baos.close();
            assertTrue(Arrays.equals(baos.toByteArray(), new byte[]{(byte) 0x0D, (byte) 0x0C, (byte) 0x0B, (byte) 0x0A}));
        } catch (IOException e) {
            assertTrue(false);
        }
    }


    @Test
    public void testReadUint() {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[]{(byte) 1, (byte) 0, (byte)0, (byte)0, (byte) 0xDA, (byte) 0xC0, (byte)0xAD, (byte)0xAB});
        try {
            long i = DataIO.readUint32(bais);
            System.err.println(Long.toHexString(i));
            assertEquals(1, i);

            i = DataIO.readUint32(bais);
            System.err.println(Long.toHexString(i));
            assertEquals(2880291034L, i);
            
        } catch (IOException e) {
            assertTrue(false);
        }
    }

    @Test
    public void testReadVarInt() {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[]{(byte) 0x2A, (byte) 0xAC, (byte)0x02});
        try {
            long i = DataIO.readVarInt(bais);
            System.err.println(Long.toHexString(i));
            assertEquals(42, i);

            i = DataIO.readVarInt(bais);
            System.err.println(Long.toHexString(i));
            assertEquals(300, i);
            
        } catch (IOException e) {
            assertTrue(false);
        }
    }
    @Test
    public void testReadWriteVarInt() {
        try {
            ByteArrayOutputStream baos;
            ByteArrayInputStream bais;

//            System.err.println("=========");
//            
            for (int i=0; i<1000000; i++) {
                baos = new ByteArrayOutputStream();
                DataIO.writeVarInt(baos, i);
                baos.close();
                
                byte[] bytes = baos.toByteArray();
                bais = new ByteArrayInputStream(bytes);
                long j = DataIO.readVarInt(bais);
//                System.err.println("["+i+"] => " + StringUtils.byteArrayToString(bytes) +" == "+j);
                assertEquals(i, j);
                bais.close();
            }            
            for (long i=Integer.MAX_VALUE; i > 0 && i<Long.MAX_VALUE; i*=10) {
                baos = new ByteArrayOutputStream();
                DataIO.writeVarInt(baos, i);
                baos.close();
                
                byte[] bytes = baos.toByteArray();
                bais = new ByteArrayInputStream(bytes);
                
                assertEquals(i, DataIO.readVarInt(bais));
                bais.close();
            }            
        } catch (IOException e) {
            assertTrue(false);
        }
    }
    @Test
    public void testWriteByte() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataIO.writeRawByte(baos, (byte) 0xFF);
            DataIO.writeRawByte(baos, (byte) 0xFE);
            DataIO.writeRawByte(baos, (byte) 0xEF);
            dumpByteArray(baos.toByteArray());

            assertTrue(Arrays.equals(baos.toByteArray(), new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xEF}));
            
        } catch (IOException e) {
            assertTrue(false);
        }
    }


    @Test
    public void testWriteUint() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataIO.writeUInt32(baos, 1);
            dumpByteArray(baos.toByteArray());

            assertEquals(4, baos.toByteArray().length);
            assertTrue(Arrays.equals(baos.toByteArray(), new byte[]{(byte) 1, (byte) 0, (byte)0, (byte)0}));

            DataIO.writeUInt32(baos, 2880291034L);
            dumpByteArray(baos.toByteArray());

            assertEquals(8, baos.toByteArray().length);
            assertTrue(Arrays.equals(baos.toByteArray(), new byte[]{(byte) 1, (byte) 0, (byte)0, (byte)0, (byte) 0xDA, (byte) 0xC0, (byte)0xAD, (byte)0xAB}));

        } catch (IOException e) {
            assertTrue(false);
        }
    }

    @Test
    public void testWriteVarInt() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            DataIO.writeVarInt(baos, 42);
            dumpByteArray(baos.toByteArray());

            assertEquals(1, baos.toByteArray().length);
            assertTrue(Arrays.equals(baos.toByteArray(), new byte[]{(byte) 0x2A}));

            baos = new ByteArrayOutputStream();
            
            DataIO.writeVarInt(baos, 300);
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

