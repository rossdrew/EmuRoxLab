package com.rox.save;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Public save-file codec. A missing file, bad magic/version, or a CRC mismatch all read back
 * identically as {@link Optional#empty()} - callers treat that exactly like "no save file exists",
 * never a crash.
 */
public final class SaveFileFormat {
    private SaveFileFormat(){
    }

    public static void writeBatterySave(final Path path, final SaveMetadata metadata, final int[] prgRam) throws IOException {
        SaveCodec.writeAtomically(path, SaveCodec.encode(SaveType.BATTERY_PRG_RAM, metadata, toBytes(prgRam)));
    }

    public static Optional<BatterySaveFile> readBatterySave(final Path path){
        if (!Files.isRegularFile(path)){
            return Optional.empty();
        }
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e){
            //a narrow TOCTOU race (the file existed a moment ago, per isRegularFile above, but no
            //longer does) or a permissions error - either way, treated the same as "no usable save"
            return Optional.empty();
        }
        return SaveCodec.decode(bytes)
                .filter(decoded -> decoded.type() == SaveType.BATTERY_PRG_RAM)
                .map(decoded -> new BatterySaveFile(decoded.metadata(), toPrgRam(decoded.payload())));
    }

    private static byte[] toBytes(final int[] prgRam){
        final byte[] bytes = new byte[prgRam.length];
        for (int i = 0; i < prgRam.length; i++){
            bytes[i] = (byte) prgRam[i];
        }
        return bytes;
    }

    private static int[] toPrgRam(final byte[] bytes){
        final int[] prgRam = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++){
            prgRam[i] = bytes[i] & 0xFF;
        }
        return prgRam;
    }
}
