package com.rox.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays resampled audio through the system's default speaker via {@code javax.sound.sampled}
 * (44100Hz, 16-bit signed PCM, mono, little-endian).
 *
 * {@link #write(double)} is called from the emulation thread and must never block on real line
 * I/O, so it only pushes into a small ring buffer; a dedicated writer thread drains that buffer
 * and does the actual (blocking) {@link SourceDataLine#write}.
 *
 * The {@link Resampler} produces samples in ~0.0-1.0 (an analog voltage, unipolar - 0.0 is true
 * silence, e.g. {@code Mixer.mix(0, 0, 0, 0, 0) == 0.0}, not a signal centered on some midpoint).
 * {@link #toPcm16} scales that range directly onto signed PCM16 without any DC-removal/centering
 * step; real NES hardware AC-couples this signal (a capacitor removes the DC bias) before it
 * reaches a speaker, which would need a proper stateful high-pass filter to replicate correctly -
 * out of scope here, not something a stateless per-sample conversion can do right.
 */
public class SpeakerAudioOutput implements AudioOutput {
    private static final float SAMPLE_RATE = 44_100f;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;

    static final int RING_BUFFER_CAPACITY = 8192;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final long WRITER_THREAD_JOIN_TIMEOUT_MS = 1000;

    private final SourceDataLine line;
    private final short[] ringBuffer = new short[RING_BUFFER_CAPACITY];
    private final Object bufferLock = new Object();
    private int writeIndex;
    private int readIndex;
    private int bufferedCount;

    private volatile boolean running;
    private Thread writerThread;

    public SpeakerAudioOutput() throws LineUnavailableException {
        this(openDefaultLine());
    }

    SpeakerAudioOutput(final SourceDataLine line){
        this.line = line;
    }

    private static SourceDataLine openDefaultLine() throws LineUnavailableException {
        //XXX Mutation coverage expected to have issues here since it deals with real hardware - just accepting it for now
        final AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
        final SourceDataLine line = AudioSystem.getSourceDataLine(format);
        line.open(format);
        return line;
    }

    @Override
    public synchronized void start(){
        if (running){
            return;
        }
        running = true;
        line.start();
        writerThread = new Thread(this::drainBufferToLine, "SpeakerAudioOutput-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    @Override
    public synchronized void stop(){
        if (!running){
            return;
        }
        running = false;
        synchronized (bufferLock){
            bufferLock.notifyAll(); //wake the writer thread so it notices `running` is now false
        }
        //stopping the line first also unblocks the writer thread if it's currently inside a
        //blocking line.write() call, which happens outside bufferLock so notifyAll() can't reach it
        line.stop();
        try {
            writerThread.join(WRITER_THREAD_JOIN_TIMEOUT_MS);
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
        line.close();
    }

    @Override
    public void write(final double sample){
        final short pcm = toPcm16(sample);
        synchronized (bufferLock){
            if (bufferedCount == ringBuffer.length){
                //buffer full: drop the oldest sample rather than block the emulation thread
                readIndex = (readIndex + 1) % ringBuffer.length;
                bufferedCount--;
            }
            ringBuffer[writeIndex] = pcm;
            writeIndex = (writeIndex + 1) % ringBuffer.length;
            bufferedCount++;
            bufferLock.notify();
        }
    }

    static short toPcm16(final double sample){
        final double scaled = sample * Short.MAX_VALUE;
        final double clamped = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
        return (short) Math.round(clamped);
    }

    private void drainBufferToLine(){
        //XXX Mutation coverage expected to have issues here since it deals with real hardware - just accepting it for now
        final byte[] frame = new byte[BYTES_PER_SAMPLE];
        while (running){
            final short sample;
            synchronized (bufferLock){
                while (bufferedCount == 0 && running){
                    try {
                        bufferLock.wait();
                    } catch (InterruptedException e){
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!running){
                    return;
                }
                sample = ringBuffer[readIndex];
                readIndex = (readIndex + 1) % ringBuffer.length;
                bufferedCount--;
            }
            frame[0] = (byte) (sample & 0xFF);
            frame[1] = (byte) ((sample >> 8) & 0xFF);
            line.write(frame, 0, frame.length);
        }
    }
}
