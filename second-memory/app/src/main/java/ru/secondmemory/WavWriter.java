package ru.secondmemory;
import java.io.*;
final class WavWriter implements Closeable {
    private final RandomAccessFile file;
    private int bytes;
    WavWriter(File path) throws IOException {
        file = new RandomAccessFile(path, "rw");
        file.setLength(0);
        header();
    }
    private void le(int value) throws IOException { file.writeInt(Integer.reverseBytes(value)); }
    private void sh(int value) throws IOException { file.writeShort(Short.reverseBytes((short)value)); }
    private void header() throws IOException {
        file.seek(0); file.writeBytes("RIFF"); le(36+bytes); file.writeBytes("WAVEfmt ");
        le(16); sh(1); sh(1); le(16000); le(32000); sh(2); sh(16); file.writeBytes("data"); le(bytes);
    }
    void write(byte[] data, int length) throws IOException {
        file.seek(44L+bytes); file.write(data,0,length); bytes += length;
        // Keep an interrupted recording playable, even if Android kills the process.
        header();
    }
    int size() { return bytes; }
    public void close() throws IOException { try { header(); } finally { file.close(); } }
}
