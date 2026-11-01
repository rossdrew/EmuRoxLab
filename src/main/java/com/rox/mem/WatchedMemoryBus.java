package com.rox.mem;

/**
 * A {@link MemoryBus} that reports every read and write made through it - how watchpoints see the CPU's accesses.
 * Anything reaching the wrapped bus another way (debugger peeks, OAM DMA's page copy) isn't reported.
 */
public final class WatchedMemoryBus implements MemoryBus {
    @FunctionalInterface
    public interface AccessListener {
        void accessed(int address, boolean write, int value);
    }

    private final MemoryBus memoryBus;
    private final AccessListener listener;

    public WatchedMemoryBus(final MemoryBus memoryBus, final AccessListener listener){
        this.memoryBus = memoryBus;
        this.listener = listener;
    }

    @Override
    public int read(final int address){
        final int value = memoryBus.read(address);
        listener.accessed(address, false, value);
        return value;
    }

    @Override
    public void write(final int address, final int value){
        memoryBus.write(address, value);
        listener.accessed(address, true, value);
    }
}
