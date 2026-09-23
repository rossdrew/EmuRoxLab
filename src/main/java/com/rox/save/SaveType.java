package com.rox.save;

/** What kind of save a file holds. */
public enum SaveType {
    /** Raw cartridge PRG-RAM ($6000-$7FFF) - mirrors real battery-backed SRAM. */
    BATTERY_PRG_RAM,
    /** A full system snapshot (CPU/PPU/APU/RAM/mapper state) - lets you resume mid-gameplay. */
    LIVE_SNAPSHOT
}
