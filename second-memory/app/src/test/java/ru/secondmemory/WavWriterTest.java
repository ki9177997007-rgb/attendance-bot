package ru.secondmemory;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
public class WavWriterTest {
 @Test public void headerIsValidBeforeAndAfterClose() throws Exception {
  File f=File.createTempFile("memory", ".wav");
  try {
   try(WavWriter w=new WavWriter(f)) {
    w.write(new byte[320],320);
    try(RandomAccessFile r=new RandomAccessFile(f,"r")) {
     r.seek(40); assertEquals(320,Integer.reverseBytes(r.readInt()));
    }
   }
   assertEquals(364,f.length());
  } finally { f.delete(); }
 }
}
