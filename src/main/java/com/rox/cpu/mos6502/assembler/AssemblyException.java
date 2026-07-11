package com.rox.cpu.mos6502.assembler;

/** Thrown when 6502 assembly source cannot be assembled. Always attributed to a source line number. */
public class AssemblyException extends RuntimeException {
    public AssemblyException(final int lineNumber, final String message) {
        super("Line " + lineNumber + ": " + message);
    }
}
