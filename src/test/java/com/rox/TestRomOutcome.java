package com.rox;

/**
 * What one test ROM reported, for {@link TestRomScoreboard}.
 *
 * @param status the ROM's own result code - 0 passed; meaningless unless {@code kind} is PASSED or FAILED
 * @param message the ROM's final words, or the emulator's error for CRASHED
 */
record TestRomOutcome(String suite, String rom, Kind kind, int status, String message) {
    enum Kind {
        PASSED,
        FAILED,
        /** still running when its time ran out - stuck waiting on something unemulated */
        NEVER_FINISHED,
        /** the emulator threw - e.g. an unimplemented opcode */
        CRASHED
    }

    static TestRomOutcome of(final String suite, final String rom, final BlarggRomRunner.Result result){
        if (!result.finished()){
            return new TestRomOutcome(suite, rom, Kind.NEVER_FINISHED, result.status(), result.text());
        }
        return new TestRomOutcome(suite, rom, result.status() == 0 ? Kind.PASSED : Kind.FAILED, result.status(), result.text());
    }

    /** The ROM's message, cut to its last non-blank line - blargg ends with "Passed"/"Failed #n" or a CRC. */
    String lastLine(){
        final String[] lines = message.strip().split("\n");
        return lines[lines.length - 1].strip();
    }
}
