package com.voice2text.audio;

import java.io.Closeable;
import java.io.IOException;

/**
 * Interface representing a source of audio byte stream.
 * Standard format: 16000 Hz, 16-bit, 1 channel (mono), signed PCM, little-endian.
 */
public interface AudioSource extends Closeable {

    /**
     * Opens and prepares the audio source for streaming.
     *
     * @throws IOException if audio line or hardware cannot be opened
     */
    void open() throws IOException;

    /**
     * Reads audio bytes into the destination buffer.
     *
     * @param buffer the buffer to fill
     * @param offset the start offset in the buffer
     * @param length the maximum number of bytes to read
     * @return the number of bytes read, or -1 if the stream has ended
     * @throws IOException if read fails
     */
    int read(byte[] buffer, int offset, int length) throws IOException;

    /**
     * Checks if the audio source is currently open and active.
     *
     * @return true if open, false otherwise
     */
    boolean isOpen();

    /**
     * Closes the audio source and releases all hardware or stream handles.
     */
    @Override
    void close();
}
