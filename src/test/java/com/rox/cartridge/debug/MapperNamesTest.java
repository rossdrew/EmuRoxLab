package com.rox.cartridge.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapperNamesTest {

    @Test
    public void namesMapperZeroNrom(){
        assertEquals("NROM", MapperNames.nameOf(0));
    }

    @Test
    public void namesMapperOneMmc1(){
        assertEquals("MMC1", MapperNames.nameOf(1));
    }

    @Test
    public void namesMapperFourMmc3(){
        assertEquals("MMC3", MapperNames.nameOf(4));
    }

    @Test
    public void fallsBackToUnknownForAnUnsupportedMapperNumber(){
        assertEquals("Unknown", MapperNames.nameOf(99));
    }
}
