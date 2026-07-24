package com.rox.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Plays resampled audio through the system's default speaker via {@code javax.sound.sampled}
 * (44100Hz, 16-bit signed PCM, mono, little-endian).
 *
 * {@link #write(double)} is called from the emulation thread and must never block on real line
 * I/O, so it only pushes into a small ring buffer; a dedicated writer thread drains that buffer
 * and does the actual (blocking) {@link SourceDataLine#write}, in batches (see {@link #WRITE_CHUNK_SAMPLES})
 * rather than one sample at a time - at 44100Hz, one native call per sample is enough per-call
 * overhead and scheduling jitter to risk underrunning the hardware's own buffer, which is audible
 * as clicks/pops.
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
    static final int WRITE_CHUNK_SAMPLES = 1024;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final long WRITER_THREAD_JOIN_TIMEOUT_MS = 1000;
    //requests a generous hardware buffer so producer jitter (GC pauses, OS scheduling, the
    //emulation's bursty per-frame production) has room to absorb without underrunning and
    //clicking; the JVM's platform-default buffer size can be too small for that
    private static final float LINE_BUFFER_DURATION_SECONDS = 0.2f;

    private final SourceDataLine line;
    private final short[] ringBuffer = new short[RING_BUFFER_CAPACITY];
    private final Object bufferLock = new Object();
    private int writeIndex;
    private int readIndex;
    private int bufferedCount;

    private volatile boolean running;
    private Thread writerThread;

    //DIAGNOSTICS (temporary): tracking down a reported clicking artifact
    private final AtomicLong droppedSampleCount = new AtomicLong();
    private final AtomicLong underrunCount = new AtomicLong();

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
        final int bufferSizeBytes = Math.round(SAMPLE_RATE * LINE_BUFFER_DURATION_SECONDS) * BYTES_PER_SAMPLE;
        line.open(format, bufferSizeBytes);
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
        //real-time-sensitive: reduce the chance of the OS scheduler starving this thread in favour
        //of the CPU-bound emulation thread, which would otherwise show up as audible clicks
        writerThread.setPriority(Thread.MAX_PRIORITY);
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
        System.err.println("[SpeakerAudioOutput diagnostics] dropped samples: " + droppedSampleCount.get()
                + ", underrun events (line buffer ran completely dry): " + underrunCount.get());
    }

    @Override
    public void write(final double sample){
        final short pcm = toPcm16(sample);
        synchronized (bufferLock){
            if (bufferedCount == ringBuffer.length){
                //buffer full: drop the oldest sample rather than block the emulation thread
                readIndex = (readIndex + 1) % ringBuffer.length;
                bufferedCount--;
                droppedSampleCount.incrementAndGet();
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
        final short[] chunk = new short[WRITE_CHUNK_SAMPLES];
        boolean firstChunkWritten = false;
        while (running){
            final int chunkLength;
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
                chunkLength = Math.min(bufferedCount, WRITE_CHUNK_SAMPLES);
                for (int i = 0; i < chunkLength; i++){
                    chunk[i] = ringBuffer[readIndex];
                    readIndex = (readIndex + 1) % ringBuffer.length;
                }
                bufferedCount -= chunkLength;
            }
            //a fresh array each call (not the reused `chunk`) so the bytes handed to the line can't
            //be mutated by the next iteration before they're actually consumed
            final byte[] frame = new byte[chunkLength * BYTES_PER_SAMPLE];
            for (int i = 0; i < chunkLength; i++){
                frame[i * BYTES_PER_SAMPLE] = (byte) (chunk[i] & 0xFF);
                frame[i * BYTES_PER_SAMPLE + 1] = (byte) ((chunk[i] >> 8) & 0xFF);
            }
            //diagnostic: if the line's own buffer is completely empty right before we feed it more
            //data (and this isn't the very first chunk, when that's expected), playback has already
            //gone silent since the last write - a definite underrun, not just a risk of one
            if (firstChunkWritten && line.available() == line.getBufferSize()){
                underrunCount.incrementAndGet();
            }
            line.write(frame, 0, frame.length);
            firstChunkWritten = true;
        }
    }
}
