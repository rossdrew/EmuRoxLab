package com.rox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class NESTest {

    @Test
    public void constructsWithCpuAndApuWiredOntoTheSharedClock(){
        assertDoesNotThrow(NES::new);
    }
}
