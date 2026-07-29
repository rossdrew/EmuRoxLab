# Cartridges

Loads `.nes` ROM files into RAM. `RomLoader` reads an
iNES file, `INesRom` parses its 16-byte header and slices out the PRG-ROM/CHR-ROM bytes, and
`RomLoader` picks a `Mapper` by the header's mapper number - the board-specific banking strategy that
owns the CPU-visible `$6000-$FFFF` window. `Cartridge` bundles the parsed `INesRom` with its `Mapper`
and implements `MemoryBus`, so `NESMemoryBus` can route straight to it.

## ROM types (mappers)

| # | Name | PRG-ROM | Banking |
|---|------|---------|---------|
| 0 | NROM | 16KB or 32KB, fixed | None - no bank registers. A 16KB image mirrors into both halves of `$8000-$FFFF`; 32KB fills it directly. |
| 1 | MMC1 | up to 512KB | 5-bit serial shift register loaded one bit per write to `$8000-$FFFF`; the assembled value latches into a control/CHR0/CHR1/PRG register depending on which address range the 5th write landed in. PRG-ROM banks 16KB or 32KB at a time, per the control register's mode bits. |

Both always back `$6000-$7FFF` with 8KB of PRG-RAM - not universal on real NROM boards, but needed
here since that's exactly where blargg's test ROMs (see `src/test/resources/roms/apu_test/`) report
their pass/fail status.

Everything else (UxROM, CNROM, MMC3, ...) is rejected by `RomLoader` with a clear "unsupported
mapper" error rather than silently misloaded - not yet implemented. NES 2.0 headers are also
rejected outright (rather than misread as iNES 1.0) until that format is supported.

## What's deliberately not modeled yet

- **CHR banking / rendering.** `Mmc1Mapper` captures its CHR bank registers but nothing reads them -
  there's no PPU yet, so nothing consumes CHR-ROM/CHR-RAM at all.
- **MMC1's `>256KB` SUROM/SOROM addressing**, which repurposes CHR bank register bits as extra PRG
  bank bits - not needed for any ROM this codebase currently loads.
- **PRG-RAM chip-enable.** Some MMC1 revisions (B2+) gate PRG-RAM on a bit in the PRG bank register;
  this codebase always leaves it writable.
