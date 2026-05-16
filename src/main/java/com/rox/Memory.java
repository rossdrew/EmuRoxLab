package com.rox;

public interface Memory {
    /** Return the contents of memory at the given <cc>address</cc> **/
    int read(final int address);
    /** Put the given <cc>value</cc> at the given <cc>address</cc> **/
    void write(final int address, final int value);
}
