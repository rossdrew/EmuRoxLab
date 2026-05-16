package com.rox;

/**
 * The path to reading from and writing to memory
 *
 * XXX Data types here need properly thought through
 */
public interface MemoryBus {
    /** Return the contents of memory at the given <cc>address</cc> **/
    int read(final int address);
    /** Put the given <cc>value</cc> at the given <cc>address</cc> **/
    void write(final int address, final int value);
}
