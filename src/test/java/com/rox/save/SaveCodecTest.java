package com.rox.save;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SaveCodecTest {
    private static final int HEADER_SIZE = 27;

    private static SaveMetadata metadata(){
        return new SaveMetadata(SaveType.BATTERY_PRG_RAM, Instant.ofEpochMilli(1_000), 42_000);
    }

    @Test
    public void encodedBytesDecodeBackToTheSameTypeMetadataAndPayload(){
        final byte[] payload = {0x11, 0x22, 0x33};
        final byte[] encoded = SaveCodec.encode(SaveType.BATTERY_PRG_RAM, metadata(), payload);

        final Optional<SaveCodec.Decoded> decoded = SaveCodec.decode(encoded);

        assertTrue(decoded.isPresent());
        assertEquals(SaveType.BATTERY_PRG_RAM, decoded.get().type());
        assertEquals(metadata(), decoded.get().metadata());
        assertArrayEquals(payload, decoded.get().payload());
    }

    @Test
    public void decodeRejectsWrongMagic(){
        final byte[] encoded = SaveCodec.encode(SaveType.BATTERY_PRG_RAM, metadata(), new byte[]{0x01});
        encoded[0] = 'X';

        assertTrue(SaveCodec.decode(encoded).isEmpty());
    }

    @Test
    public void decodeRejectsAnUnrecognisedVersion(){
        final byte[] encoded = SaveCodec.encode(SaveType.BATTERY_PRG_RAM, metadata(), new byte[]{0x01});
        encoded[5] = 99; //low byte of the version short

        assertTrue(SaveCodec.decode(encoded).isEmpty());
    }

    @Test
    public void decodeRejectsACorruptedPayloadByte(){
        final byte[] encoded = SaveCodec.encode(SaveType.BATTERY_PRG_RAM, metadata(), new byte[]{0x01, 0x02, 0x03});
        encoded[HEADER_SIZE] ^= 0xFF; //flip the first payload byte

        assertTrue(SaveCodec.decode(encoded).isEmpty());
    }

    @Test
    public void decodeRejectsInputTruncatedInsidePayloadOrTrailingCrc(){
        //by the time payloadLength itself is fully read, this is caught by the new
        //payloadLength-vs-actually-remaining check below, not an EOFException - either way, empty
        final byte[] encoded = SaveCodec.encode(SaveType.BATTERY_PRG_RAM, metadata(), new byte[]{0x01, 0x02, 0x03});

        assertTrue(SaveCodec.decode(Arrays.copyOf(encoded, encoded.length - 2)).isEmpty());
    }

    @Test
    public void decodeRejectsInputTruncatedWithinTheFixedHeader(){
        //truncated before payloadLength (or anything after it) can even be read - this is the one
        //scenario that genuinely reaches the EOFException catch, not the payloadLength check
        final byte[] encoded = SaveCodec.encode(SaveType.BATTERY_PRG_RAM, metadata(), new byte[]{0x01, 0x02, 0x03});

        assertTrue(SaveCodec.decode(Arrays.copyOf(encoded, 10)).isEmpty());
    }

    @Test
    public void decodeRejectsAPayloadLengthThatDoesNotMatchWhatsActuallyPresent(){
        //a corrupt/malicious file could otherwise claim a huge payloadLength and force an equally
        //huge allocation (new byte[payloadLength]) before that length is ever cross-checked - this
        //proves the check runs first, using a length far larger than the 3 bytes actually present
        final byte[] encoded = SaveCodec.encode(SaveType.BATTERY_PRG_RAM, metadata(), new byte[]{0x01, 0x02, 0x03});
        encoded[23] = 0x00;
        encoded[24] = 0x00;
        encoded[25] = 0x03;
        encoded[26] = (byte) 0xE8; //claims 1000 bytes of payload

        assertTrue(SaveCodec.decode(encoded).isEmpty());
    }

    @Test
    public void decodeRejectsEmptyInput(){
        assertTrue(SaveCodec.decode(new byte[0]).isEmpty());
    }

    @Test
    public void decodeRejectsAnOutOfRangeSaveTypeOrdinal(){
        final byte[] encoded = SaveCodec.encode(SaveType.BATTERY_PRG_RAM, metadata(), new byte[]{0x01});
        encoded[6] = (byte) SaveType.values().length; //one past the last real SaveType ordinal

        assertTrue(SaveCodec.decode(encoded).isEmpty());
    }

    @Test
    public void decodeAcceptsAZeroLengthPayload(){
        //the boundary right next to decodeRejectsANegativePayloadLength - payloadLength == 0 is valid
        //and must NOT be rejected by an off-by-one in that check
        final byte[] encoded = SaveCodec.encode(SaveType.BATTERY_PRG_RAM, metadata(), new byte[0]);

        final Optional<SaveCodec.Decoded> decoded = SaveCodec.decode(encoded);

        assertTrue(decoded.isPresent());
        assertArrayEquals(new byte[0], decoded.get().payload());
    }

    @Test
    public void decodeRejectsANegativePayloadLength(){
        final byte[] encoded = SaveCodec.encode(SaveType.BATTERY_PRG_RAM, metadata(), new byte[]{0x01});
        //payloadLength is the 4-byte int at offset 23 (magic 4 + version 2 + type 1 + 2 longs = 23)
        encoded[23] = (byte) 0xFF;
        encoded[24] = (byte) 0xFF;
        encoded[25] = (byte) 0xFF;
        encoded[26] = (byte) 0xFF;

        assertTrue(SaveCodec.decode(encoded).isEmpty());
    }

    @Test
    public void writeAtomicallyProducesAReadableFileAndLeavesNoTmpFileBehind(@TempDir final Path tempDir) throws IOException {
        final Path target = tempDir.resolve("nested").resolve("battery.sav");
        final byte[] content = {1, 2, 3, 4};

        SaveCodec.writeAtomically(target, content);

        assertArrayEquals(content, Files.readAllBytes(target));
        assertFalse(Files.exists(target.resolveSibling(target.getFileName() + ".tmp")));
    }

    @Test
    public void writeAtomicallyReplacesAnExistingFile(@TempDir final Path tempDir) throws IOException {
        final Path target = tempDir.resolve("battery.sav");
        SaveCodec.writeAtomically(target, new byte[]{1});

        SaveCodec.writeAtomically(target, new byte[]{9, 9});

        assertArrayEquals(new byte[]{9, 9}, Files.readAllBytes(target));
    }
}
