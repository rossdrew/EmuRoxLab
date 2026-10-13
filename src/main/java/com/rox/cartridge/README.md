# Cartridges

Loads `.nes` ROM files into RAM. `RomLoader` reads an
iNES file, `INesRom` parses its 16-byte header and slices out the PRG-ROM/CHR-ROM bytes, and
`RomLoader` picks a `Mapper` by the header's mapper number - the board-specific banking strategy that
owns the CPU-visible `$6000-$FFFF` window. `Cartridge` bundles the parsed `INesRom` with its `Mapper`
and implements `MemoryBus`, so `NESMemoryBus` can route straight to it.

## ROM types (mappers)

| # | Name | PRG-ROM | Banking |
|---|------|---------|---------|
| 0 | NROM | 16KB or 32KB, fixed | None. A 16KB image mirrors into both halves of `$8000-$FFFF`. |
| 1 | MMC1 | up to 256KB | 5-bit serial shift register, one bit per write to `$8000-$FFFF`. PRG in 16KB or 32KB banks; CHR in 4KB or 8KB banks. |
| 4 | MMC3 | up to 512KB | Bank select/data at `$8000`/`$8001`. PRG in four 8KB windows (two switchable); CHR in two 2KB + four 1KB windows. Scanline IRQ counter clocked by the PPU's A12 line. |

All three:
- back `$6000-$7FFF` with 8KB of PRG-RAM, saved to disk for battery-backed ROMs (see `com.rox.save`)
- use 8KB of CHR-RAM when the ROM has no CHR-ROM
- support full `snapshot()`/`restore()`; MMC1 and MMC3 also report their registers via `debugState()`

Any other mapper number is rejected by `RomLoader` with an "unsupported mapper" error. NES 2.0 headers are also rejected rather than misread as iNES 1.0.

## Not modeled yet

- **MMC1 SUROM/SOROM (>256KB)**, which reuses CHR bank bits as extra PRG bank bits.
- **PRG-RAM enable/write-protect** (MMC1's chip-enable bit, MMC3's `$A001`). PRG-RAM is always writable.
- **MMC3 A12 filtering.** Real hardware only counts an A12 rise after A12 has been low for a few cycles. Without that, mixed 8x16 sprites can clock the IRQ counter more than once per scanline, e.g. Super Mario Bros. 3's status-bar split.
