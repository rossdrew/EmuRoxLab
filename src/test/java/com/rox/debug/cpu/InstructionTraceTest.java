package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import com.rox.mem.Latched8BitMemoryBus;
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.RAM;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertArrayEquals(new int[0], new InstructionTrace(atBoundary::get, pc::get, address -> 0).recent());
    }

    @Test
    public void recordsThePcOnlyWhenBetweenInstructions(){
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, address -> 0, 4);
        tickAt(trace, 0x8000);
        atBoundary.set(false);
        tickAt(trace, 0x8001, 0x8002); //mid-instruction - not recorded
        atBoundary.set(true);
        tickAt(trace, 0x8003);

        assertArrayEquals(new int[]{0x8000, 0x8003}, trace.recent());
    }

    @Test
    public void keepsOnlyTheNewestEntriesOldestFirstOnceFull(){
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, address -> 0, 3);

        tickAt(trace, 1, 2, 3, 4, 5);

        assertArrayEquals(new int[]{3, 4, 5}, trace.recent());
    }

    @Test
    public void exactlyFullIsOldestFirstToo(){
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, address -> 0, 3);

        tickAt(trace, 1, 2, 3);

        assertArrayEquals(new int[]{1, 2, 3}, trace.recent());
    }

    @Test
    public void recordedCountsEveryEntryEvenOnceFull(){
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, address -> 0, 3);
        tickAt(trace, 1, 2);
        atBoundary.set(false);
        tickAt(trace, 3); //mid-instruction - not recorded
        atBoundary.set(true);
        tickAt(trace, 4, 5, 6);

        assertEquals(5, trace.recorded());
    }

    @Test
    public void defaultCapacityIs32(){
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, address -> 0);
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
        final InstructionTrace trace = new InstructionTrace(cpu::isAtInstructionBoundary, cpu::programCounter, ram::read);

        for (int i = 0; i < 2 + 3 * (2 + 3) - 1; i++){ //LDX, then DEX+BNE x3 (taken, taken, not taken)
            cpu.tick();
            trace.tick();
        }

        assertArrayEquals(new int[]{0x0002, 0x0003, 0x0002, 0x0003, 0x0002, 0x0003, 0x0005}, trace.recent());
    }

    /** CodeRabbit's PR #44 finding: a bank switch or self-modifying write must not rewrite history. */
    @Test
    public void historyKeepsTheBytesThatRanEvenAfterTheAddressHoldsDifferentCode(){
        final int[] memory = new int[0x10000];
        memory[0x8000] = 0xA9; //LDA #$05
        memory[0x8001] = 0x05;
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, address -> memory[address]);
        tickAt(trace, 0x8000);

        memory[0x8000] = 0xEA; //now NOP - e.g. a different PRG bank mapped in
        memory[0x8001] = 0xEA;

        assertEquals("LDA #$05", trace.recentInstructions().get(0).formatted());
        assertEquals(0x8000, trace.recentInstructions().get(0).address());
    }

    @Test
    public void recordsAllThreeBytesOfTheLongestInstructions(){
        final int[] memory = new int[0x10000];
        memory[0x8000] = 0x4C; //JMP $1234
        memory[0x8001] = 0x34;
        memory[0x8002] = 0x12;
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, address -> memory[address]);
        tickAt(trace, 0x8000);

        assertEquals("JMP $1234", trace.recentInstructions().get(0).formatted());
    }

    @Test
    public void recordedBytesWrapAtTheTopOfTheAddressSpace(){
        final int[] memory = new int[0x10000];
        memory[0xFFFF] = 0x4C; //JMP $1234, operand at $0000-$0001
        memory[0x0000] = 0x34;
        memory[0x0001] = 0x12;
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, address -> memory[address]);
        tickAt(trace, 0xFFFF);

        assertEquals("JMP $1234", trace.recentInstructions().get(0).formatted());
    }

    @Test
    public void recentInstructionsAreOldestFirstAndMatchRecentAddresses(){
        final int[] memory = new int[0x10000];
        java.util.Arrays.fill(memory, 0xEA); //NOP everywhere
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, address -> memory[address], 2);
        tickAt(trace, 0x10, 0x20, 0x30);

        assertEquals(List.of(0x20, 0x30), trace.recentInstructions().stream().map(DisassembledInstruction::address).toList());
        assertTrue(new InstructionTrace(atBoundary::get, pc::get, address -> 0).recentInstructions().isEmpty());
    }

    /** Runs on the clock thread for every instruction, so it reads only what the longest instruction needs. */
    @Test
    public void readsExactlyThreeBytesPerRecordedInstruction(){
        final AtomicInteger reads = new AtomicInteger();
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, address -> {
            reads.incrementAndGet();
            return 0;
        });

        tickAt(trace, 0x8000, 0x8003);

        assertEquals(6, reads.get());
    }

    /** Each entry carries the registers its instruction started with - the state just before it ran. */
    @Test
    public void historyRecordsTheStateEachInstructionStartedWith(){
        //$0000: LDX #$03; $0002: DEX; $0003: BNE $0002
        final RAM ram = new RAM(0x10000);
        final int[] program = {0xA2, 0x03, 0xCA, 0xD0, 0xFD};
        for (int i = 0; i < program.length; i++){
            ram.write(i, program[i]);
        }
        final MOS6502 cpu = new MOS6502(new Latched8BitMemoryBus(new MemoryBus8Bit(ram)));
        final InstructionTrace trace = new InstructionTrace(cpu::isAtInstructionBoundary, cpu::programCounter, ram::read,
                cpu::packedRegisters, cpu::packedFlags, InstructionTrace.DEFAULT_CAPACITY);

        for (int i = 0; i < 2 + 2 + 3 + 2; i++){ //LDX, DEX, BNE (taken), DEX
            cpu.tick();
            trace.tick();
        }

        final List<TracedInstruction> history = trace.history();
        assertEquals(List.of("DEX", "BNE $0002", "DEX", "BNE $0002"),
                history.stream().map(t -> t.instruction().formatted()).toList());
        assertEquals(List.of(3, 2, 2, 1), history.stream().map(t -> t.before().x()).toList(), "X as each instruction started");
        assertEquals(List.of(0x0002, 0x0003, 0x0002, 0x0003), history.stream().map(t -> t.before().pc()).toList());
        assertEquals(cpu.liveState().x(), history.get(history.size() - 1).before().x(), "the newest entry is the current state");
    }

    @Test
    public void withoutStateSuppliersHistoryHasZeroedState(){
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, address -> 0xEA);
        tickAt(trace, 0x8000);

        final TracedInstruction only = trace.history().get(0);
        assertEquals("NOP", only.instruction().formatted());
        assertEquals(com.rox.cpu.mos6502.MOS6502Snapshot.unpack(0x8000, 0, 0), only.before());
    }

    @Test
    public void historyMatchesRecentInstructionsWhenTheBufferHasWrapped(){
        final AtomicInteger registers = new AtomicInteger();
        final InstructionTrace trace = new InstructionTrace(atBoundary::get, pc::get, address -> 0xEA,
                registers::get, () -> 0, 2);
        for (int i = 1; i <= 3; i++){
            registers.set(i); //A = i
            tickAt(trace, 0x8000 + i);
        }

        final List<TracedInstruction> history = trace.history();
        assertEquals(List.of(0x8002, 0x8003), history.stream().map(t -> t.instruction().address()).toList());
        assertEquals(List.of(2, 3), history.stream().map(t -> t.before().a()).toList(), "oldest dropped, state kept aligned");
    }
}
