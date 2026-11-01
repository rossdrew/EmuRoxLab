package com.rox.debug.breakpoints;

/** Which CPU accesses a watchpoint catches. */
public enum WatchKind {
    READ(true, false),
    WRITE(false, true),
    ACCESS(true, true);

    private final boolean reads;
    private final boolean writes;

    WatchKind(final boolean reads, final boolean writes){
        this.reads = reads;
        this.writes = writes;
    }

    public boolean catches(final boolean write){
        return write ? writes : reads;
    }
}
