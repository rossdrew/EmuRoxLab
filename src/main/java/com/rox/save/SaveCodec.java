package com.rox.save;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.CRC32;

/**
 * Low-level byte format shared by every save type - the magic/version/metadata header, a trailing
 * whole-file CRC32, and atomic disk writes. {@link SaveFileFormat} is the public, save-type-aware API
 * built on top of this.
 *
 * <pre>
 * offset  size  field
 * 0       4     magic: 'R','O','X','S'
 * 4       2     format version (short)
 * 6       1     save type ordinal (byte)
 * 7       8     createdAt, epoch millis (long) - fixed for the file's life
 * 15      8     accumulatedGameTimeMillis (long) - rewritten every save
 * 23      4     payload length (int)
 * 27      N     payload
 * 27+N    4     CRC32 of bytes [0, 27+N)
 * </pre>
 */
final class SaveCodec {
    private static final byte[] MAGIC = {'R', 'O', 'X', 'S'};
    private static final short VERSION = 1;

    private SaveCodec(){
    }

    record Decoded(SaveType type, SaveMetadata metadata, byte[] payload){
    }

    /** Builds a complete save file's bytes: header + payload + trailing CRC32 of everything before it. */
    static byte[] encode(final SaveType type, final SaveMetadata metadata, final byte[] payload){
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)){
            out.write(MAGIC);
            out.writeShort(VERSION);
            out.writeByte(type.ordinal());
            out.writeLong(metadata.createdAt().toEpochMilli());
            out.writeLong(metadata.accumulatedGameTimeMillis());
            out.writeInt(payload.length);
            out.write(payload);
            //no explicit flush() needed - DataOutputStream forwards every write() straight through to
            //the underlying ByteArrayOutputStream with no internal buffering to flush
            final CRC32 crc = new CRC32();
            crc.update(buffer.toByteArray());
            out.writeInt((int) crc.getValue());
        } catch (IOException e){
            //a DataOutputStream wrapping a ByteArrayOutputStream never actually does real I/O - purely
            //in-memory arithmetic, so this can't genuinely fail
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    /** Empty on truncated/malformed input, an unrecognised magic/version, or a CRC mismatch - all treated as "not a usable save file". */
    static Optional<Decoded> decode(final byte[] bytes){
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))){
            final byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)){
                return Optional.empty();
            }
            if (in.readShort() != VERSION){
                return Optional.empty();
            }
            final int typeOrdinal = in.readUnsignedByte();
            if (typeOrdinal >= SaveType.values().length){
                return Optional.empty();
            }
            final SaveType type = SaveType.values()[typeOrdinal];
            final long createdAtEpochMillis = in.readLong();
            final long accumulatedGameTimeMillis = in.readLong();
            final int payloadLength = in.readInt();
            //reject before allocating - a corrupt/malicious file declaring a huge payloadLength must
            //not reach `new byte[payloadLength]` (risking OutOfMemoryError) before the length is even
            //checked against what's actually left in the file (the payload itself, plus the trailing CRC)
            if (payloadLength < 0 || payloadLength != in.available() - Integer.BYTES){
                return Optional.empty();
            }
            final byte[] payload = new byte[payloadLength];
            in.readFully(payload);
            final int storedCrc = in.readInt();

            final int crcCoveredLength = bytes.length - Integer.BYTES;
            final CRC32 crc = new CRC32();
            crc.update(bytes, 0, crcCoveredLength);
            if ((int) crc.getValue() != storedCrc){
                return Optional.empty();
            }

            final SaveMetadata metadata = new SaveMetadata(type, Instant.ofEpochMilli(createdAtEpochMillis), accumulatedGameTimeMillis);
            return Optional.of(new Decoded(type, metadata, payload));
        } catch (EOFException e){
            return Optional.empty(); //truncated file
        } catch (IOException e){
            //a DataInputStream wrapping a ByteArrayInputStream only ever short-reads (EOFException,
            //caught above) - it has no real I/O to genuinely fail with any other IOException
            throw new UncheckedIOException(e);
        }
    }

    /** Writes {@code content} to {@code target} without ever leaving a torn/partial file behind, even on a crash mid-write. */
    static void writeAtomically(final Path target, final byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        final Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, content);
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
