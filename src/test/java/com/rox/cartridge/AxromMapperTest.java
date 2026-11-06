package com.rox.cartridge;

import com.rox.TestRoms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AxromMapperTest {
    private static final int BANK = 0x8000;

    /** {@code banks} 32KB PRG banks, each byte holding its bank number in the high nibble and offset's low byte below. */
    private static AxromMapper mapper(final int banks){
        return new AxromMapper(TestRoms.mapper(7).prgSize(banks * BANK)
                .fillPrg(offset -> ((offset / BANK) << 4) | (offset & 0x0F)).rom());
    }

    @Test
    void bankZeroIsMappedAtPowerOn(){
        final AxromMapper mapper = mapper(8);

        assertEquals(0x00, mapper.read(0x8000));
        assertEquals(0x0F, mapper.read(0xFFFF));
    }

    @ParameterizedTest
    @CsvSource({"0, 0x00", "1, 0x10", "5, 0x50", "7, 0x70"})
    void bitsZeroToTwoSelectThe32KbBank(final int bank, final String firstByte){
        final AxromMapper mapper = mapper(8);

        mapper.write(0x8000, bank);

        assertEquals(Integer.decode(firstByte), mapper.read(0x8000));
        assertEquals(Integer.decode(firstByte) | 0x0F, mapper.read(0xFFFF), "the whole window is that bank");
    }

    @ParameterizedTest
    @ValueSource(ints = {0x8000, 0x9ABC, 0xFFFF})
    void theRegisterAnswersAnywhereFrom8000(final int address){
        final AxromMapper mapper = mapper(8);

        mapper.write(address, 3);

        assertEquals(0x30, mapper.read(0x8000));
    }

    @Test
    void bitsAboveTheBankNumberDontMoveIt(){
        final AxromMapper mapper = mapper(8);

        mapper.write(0x8000, 0xE8 | 2); //bits 3 and 5-7 set too

        assertEquals(0x20, mapper.read(0x8000));
    }

    @Test
    void aBankPastTheEndOfASmallerRomWraps(){
        final AxromMapper mapper = mapper(4);

        mapper.write(0x8000, 6);

        assertEquals(0x20, mapper.read(0x8000), "bank 6 of 4 is bank 2");
    }

    @Test
    void bit4PicksWhichNametablePageAllFourUse(){
        final AxromMapper mapper = mapper(2);
        assertEquals(Mirroring.SINGLE_SCREEN_LOWER, mapper.nametableMirroring());

        mapper.write(0x8000, 0x10);
        assertEquals(Mirroring.SINGLE_SCREEN_UPPER, mapper.nametableMirroring());

        mapper.write(0x8000, 0x01);
        assertEquals(Mirroring.SINGLE_SCREEN_LOWER, mapper.nametableMirroring());
    }

    @Test
    void thereIsNoPrgRam(){
        final AxromMapper mapper = mapper(1);

        mapper.write(0x6000, 0x42);
        mapper.write(0x7FFF, 0x99);

        assertEquals(0, mapper.read(0x6000));
        assertEquals(0, mapper.read(0x7FFF));
        assertEquals(0x00, mapper.read(0x8000), "a $6000-$7FFF write doesn't switch banks");
        assertEquals(0, mapper.prgRam().length);
    }

    @Test
    void restoringPrgRamAcceptsOnlyNone(){
        final AxromMapper mapper = mapper(1);

        mapper.restorePrgRam(new int[0]);

        assertThrows(IllegalArgumentException.class, () -> mapper.restorePrgRam(new int[0x2000]));
    }

    @Test
    void chrRamReadsBackWhatWasWrittenAcross8Kb(){
        final AxromMapper mapper = mapper(1);

        mapper.writeChr(0x0000, 0x12);
        mapper.writeChr(0x1FFF, 0x34);

        assertEquals(0x12, mapper.readChr(0x0000));
        assertEquals(0x34, mapper.readChr(0x1FFF));
    }

    @Test
    void chrRomIsReadOnly(){
        final AxromMapper mapper = new AxromMapper(TestRoms.mapper(7).prgSize(BANK).chrSize(0x2000).fillChr(offset -> 0x5A).rom());

        mapper.writeChr(0x0010, 0x12);

        assertEquals(0x5A, mapper.readChr(0x0010));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 0x4000, 0x8000 + 0x4000})
    void rejectsPrgRomThatIsntWhole32KbBanks(final int size){
        assertThrows(IllegalArgumentException.class,
                () -> new AxromMapper(TestRoms.mapper(7).prgSize(size).rom()));
    }

    @Test
    void debugStateShowsTheRegisterAndTheBankItSelects(){
        final AxromMapper mapper = mapper(4);
        mapper.write(0x8000, 0x16);

        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("Bank select", "$16");
        expected.put("PRG bank", "2");
        assertEquals(expected, mapper.debugState());
    }
}
