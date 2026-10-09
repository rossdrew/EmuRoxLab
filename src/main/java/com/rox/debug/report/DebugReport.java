package com.rox.debug.report;

import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import com.rox.save.SystemSnapshot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Everything captured when an issue is flagged mid-game - see {@link DebugReportWriter} for the files
 * it becomes. {@code snapshot} is the restorable whole-system state; the other fields are what can't
 * be derived from it alone (the ROM is needed to disassemble, the mapper to describe its banks).
 *
 * @param romName the ROM's file name, for the report header
 * @param screenshotRgb the last complete frame shown (packed {@code 0xRRGGBB}, 256x240), or
 * {@code null} if none had been shown yet
 * @param partialFrameRgb the PPU's framebuffer at the moment of capture - usually part-drawn
 * @param upcomingInstructions disassembly forward from the CPU's PC
 */
public record DebugReport(LocalDateTime capturedAt, String romName, String description, int[] screenshotRgb,
                          int[] partialFrameRgb, SystemSnapshot snapshot, Map<String, String> mapperState,
                          List<DisassembledInstruction> upcomingInstructions) {

    public DebugReport withDescription(final String newDescription){
        return new DebugReport(capturedAt, romName, newDescription, screenshotRgb, partialFrameRgb, snapshot,
                mapperState, upcomingInstructions);
    }
}
