package com.rox.save;

import com.rox.apu.ApuSnapshot;
import com.rox.cartridge.MapperSnapshot;
import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.ppu.PpuSnapshot;

/**
 * A whole NES's state, captured while paused (see {@code NES.captureSnapshot()}): every component's
 * own snapshot plus the CPU-side RAM. Cartridge ROM isn't included - {@code romCrc32} (over PRG-ROM
 * then CHR-ROM) identifies which cartridge the snapshot belongs to, so a restore can refuse a different one.
 */
public record SystemSnapshot(long romCrc32, MOS6502Snapshot cpu, PpuSnapshot ppu, ApuSnapshot apu, int[] ram,
                             MapperSnapshot mapper) {
}
