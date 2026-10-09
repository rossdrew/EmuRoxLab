package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502;
import com.rox.mem.Latched8BitMemoryBus;
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.RAM;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class InstructionTraceTest {
    private final AtomicBoolean atBoundary = new AtomicBoolean(true);
    private final AtomicInteger pc = new AtomicInteger();

    private void tickAt(final InstructionTrace trace, final int... addresses){
        for (final int address : addresses){
            pc.set(address);
            trace.tick();
        }
    }

    @Test
    public void startsEmpty(){
        assertArrayEquals(new int[0], new InstructionTrace(atBoundary::get, pc::get).recent());
    }

    @Test
    public void recordsThePcOnlyWhenBetweenInstructions(){
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, 4);
        tickAt(trace, 0x8000);
        atBoundary.set(false);
        tickAt(trace, 0x8001, 0x8002); //mid-instruction - not recorded
        atBoundary.set(true);
        tickAt(trace, 0x8003);

        assertArrayEquals(new int[]{0x8000, 0x8003}, trace.recent());
    }

    @Test
    public void keepsOnlyTheNewestEntriesOldestFirstOnceFull(){
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, 3);

        tickAt(trace, 1, 2, 3, 4, 5);

        assertArrayEquals(new int[]{3, 4, 5}, trace.recent());
    }

    @Test
    public void exactlyFullIsOldestFirstToo(){
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, 3);

        tickAt(trace, 1, 2, 3);

        assertArrayEquals(new int[]{1, 2, 3}, trace.recent());
    }

    @Test
    public void defaultCapacityIs32(){
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get);
        for (int i = 0; i < 40; i++){
            tickAt(trace, i);
        }

        assertArrayEquals(java.util.stream.IntStream.range(8, 40).toArray(), trace.recent());
    }

    /** With a real CPU: each instruction's start address is recorded once, however many cycles it takes. */
    @Test
    public void recordsEachInstructionOnceWithARealCpu(){
        //$0000: LDX #$03; $0002: DEX; $0003: BNE $0002 (taken twice, then falls through to $0005)
        final RAM ram = new RAM(0x10000);
        final int[] program = {0xA2, 0x03, 0xCA, 0xD0, 0xFD};
        for (int i = 0; i < program.length; i++){
            ram.write(i, program[i]);
        }
        final MOS6502 cpu = new MOS6502(new Latched8BitMemoryBus(new MemoryBus8Bit(ram)));
        final InstructionTrace trace = new InstructionTrace(cpu::isAtInstructionBoundary, cpu::programCounter);

        for (int i = 0; i < 2 + 3 * (2 + 3) - 1; i++){ //LDX, then DEX+BNE x3 (taken, taken, not taken)
            cpu.tick();
            trace.tick();
        }

        assertArrayEquals(new int[]{0x0002, 0x0003, 0x0002, 0x0003, 0x0002, 0x0003, 0x0005}, trace.recent());
    }
}
