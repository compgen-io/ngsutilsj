package org.ngsutils.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;
import org.ngsutils.support.io.DataInput;
import org.ngsutils.support.io.DataOutput;

public class DataInputStreamTest {

    @Test
    public void testReadByte() {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xEF});
        DataInput dis = new DataInput(bais);
        try {
            byte b = dis.readByte();
            assertEquals((byte)0xFF, b);
            b = dis.readByte();
            assertEquals((byte)0xFE, b);
            b = dis.readByte();
            assertEquals((byte)0xEF, b);
            
        } catch (IOException e) {
            assertTrue(false);
        }
    }


    @Test
    public void testReadUint() {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[]{(byte) 0, (byte) 0, (byte)0, (byte)1, (byte) 0xAB, (byte) 0xAD, (byte)0xC0, (byte)0xDA});
        DataInput dis = new DataInput(bais);
        try {
            long i = dis.readUint32();
            System.err.println(Long.toHexString(i));
            assertEquals(1, i);

            i = dis.readUint32();
            System.err.println(Long.toHexString(i));
            assertEquals(2880291034L, i);
            
        } catch (IOException e) {
            assertTrue(false);
        }
    }

    @Test
    public void testWriteVarInt() {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[]{(byte) 0x2A, (byte) 0xAC, (byte)0x02});
        DataInput dis = new DataInput(bais);
        try {
            long i = dis.readVarInt();
            System.err.println(Long.toHexString(i));
            assertEquals(42, i);

            i = dis.readVarInt();
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
            DataOutput dos;
            ByteArrayInputStream bais;
            DataInput dis;

//            System.err.println("=========");
//            
            for (int i=0; i<1000000; i++) {
                baos = new ByteArrayOutputStream();
                dos = new DataOutput(baos);
                dos.writeVarInt(i);
                dos.close();
                
                byte[] bytes = baos.toByteArray();
                bais = new ByteArrayInputStream(bytes);
                dis = new DataInput(bais);
                
                assertEquals(i, dis.readVarInt());
                dis.close();
            }            
            for (long i=Integer.MAX_VALUE; i > 0 && i<Long.MAX_VALUE; i*=10) {
                baos = new ByteArrayOutputStream();
                dos = new DataOutput(baos);
                dos.writeVarInt(i);
                dos.close();
                
                byte[] bytes = baos.toByteArray();
                bais = new ByteArrayInputStream(bytes);
                dis = new DataInput(bais);
                
                assertEquals(i, dis.readVarInt());
                dis.close();
            }            
        } catch (IOException e) {
            assertTrue(false);
        }
    }

}

