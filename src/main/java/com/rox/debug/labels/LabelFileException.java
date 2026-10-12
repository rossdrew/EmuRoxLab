package com.rox.debug.labels;

/** A labels file line that can't be used - carries the 1-based line number so the message points at it. */
public final class LabelFileException extends Exception {
    private final int lineNumber;

    public LabelFileException(final int lineNumber, final String problem){
        super("Line " + lineNumber + ": " + problem);
        this.lineNumber = lineNumber;
    }

    public int lineNumber(){
        return lineNumber;
    }
}
