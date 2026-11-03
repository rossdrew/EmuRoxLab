package com.rox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class LaunchOptionsTest {
    @Test
    void everythingIsOptional(){
        assertEquals(new LaunchOptions(Optional.empty(), Optional.empty(), OptionalInt.empty()), LaunchOptions.parse());
    }

    @Test
    void readsEveryFlagInAnyOrder(){
        assertEquals(new LaunchOptions(Optional.of(Path.of("loz.nes")), Optional.of(Path.of("pads.properties")), OptionalInt.of(30)),
                LaunchOptions.parse("--seconds", "30", "--controllers", "pads.properties", "--rom", "loz.nes"));
    }

    @Test
    void controllersCanBeGivenWithoutARom(){
        final LaunchOptions options = LaunchOptions.parse("--controllers", "pads.properties");

        assertEquals(Optional.empty(), options.rom());
        assertEquals(Optional.of(Path.of("pads.properties")), options.controllers());
    }

    @Test
    void zeroSecondsIsAllowed(){
        assertEquals(OptionalInt.of(0), LaunchOptions.parse("--seconds", "0").seconds());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "loz.nes                          | Unknown option: loz.nes",
            "--rom                            | --rom needs a value",
            "--rom a.nes --seconds            | --seconds needs a value",
            "--rom a.nes --rom b.nes          | --rom given more than once",
            "--controllers a --controllers b  | --controllers given more than once",
            "--seconds 1 --seconds 2          | --seconds given more than once",
            "--seconds -1                     | --seconds must be non-negative",
            "--seconds ten                    | --seconds must be a whole number, not ten"
    })
    void rejectsBadArgumentsSayingWhy(final String args, final String message){
        assertEquals(message, assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(args.split(" "))).getMessage());
    }
}
