package com.rox.cartridge.debug;

import java.util.Map;

/** Display name for each iNES mapper number this codebase supports - see {@code RomLoader}. */
public final class MapperNames {
    private static final Map<Integer, String> NAMES = Map.of(
            0, "NROM",
            1, "MMC1",
            4, "MMC3"
    );

    private MapperNames(){
    }

    /** @return the board name for {@code mapperNumber}, or "Unknown" if this codebase doesn't support it. */
    public static String nameOf(final int mapperNumber){
        return NAMES.getOrDefault(mapperNumber, "Unknown");
    }
}
