package com.rox.cartridge;

/** {@link Mmc1Mapper}'s state, including a part-filled serial shift register. */
public record Mmc1MapperSnapshot(int[] prgRam, int[] chrRam, int shiftRegister, int shiftCount,
                                 int controlRegister, int chrBank0Register, int chrBank1Register,
                                 int prgBankRegister) implements MapperSnapshot {
}
