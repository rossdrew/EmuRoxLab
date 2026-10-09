package com.rox.save;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static com.rox.RecordAssertions.assertRecordsEqual;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnapshotCodecTest {
    public sealed interface Shape permits Circle, Square {
    }

    public record Circle(int radius) implements Shape {
    }

    public record Square(boolean filled, int side) implements Shape {
    }

    public record Inner(int value, boolean flag) {
    }

    /** One component of every supported type. */
    public record Everything(int number, long big, boolean flag, int[] bytes, boolean[] flags, Inner inner, Shape shape) {
    }

    public record Positive(int value) {
        public Positive {
            if (value < 0){
                throw new IllegalArgumentException("negative");
            }
        }
    }

    public record Unsupported(String text) {
    }

    /** An array as the very last thing in the payload, so its length exactly equals what's left to read. */
    public record TrailingBytes(int[] bytes) {
    }

    private static Everything everything(final Shape shape){
        return new Everything(-123_456, Long.MIN_VALUE + 7, true, new int[]{0, 0x7F, 0xFF},
                new boolean[]{true, false, true}, new Inner(42, true), shape);
    }

    @Test
    public void roundTripsEverySupportedComponentType(){
        final Everything original = everything(new Square(true, 9));

        final Optional<Everything> decoded = SnapshotCodec.decode(SnapshotCodec.encode(original), Everything.class);

        assertTrue(decoded.isPresent());
        assertRecordsEqual(original, decoded.get());
    }

    @Test
    public void sealedComponentsComeBackAsTheSameConcreteType(){
        final Everything withCircle = everything(new Circle(5));

        final Everything decoded = SnapshotCodec.decode(SnapshotCodec.encode(withCircle), Everything.class).orElseThrow();

        assertEquals(new Circle(5), decoded.shape());
    }

    @Test
    public void encodeRejectsArrayValuesThatAreNotBytes(){
        final Everything negative = new Everything(0, 0, false, new int[]{-1}, new boolean[0], new Inner(0, false), new Circle(0));
        final Everything tooBig = new Everything(0, 0, false, new int[]{0x100}, new boolean[0], new Inner(0, false), new Circle(0));

        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.encode(negative));
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.encode(tooBig));
    }

    @Test
    public void encodeRejectsUnsupportedComponentTypes(){
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.encode(new Unsupported("x")));
    }

    @Test
    public void decodeIsEmptyForUnsupportedComponentTypes(){
        assertEquals(Optional.empty(), SnapshotCodec.decode(new byte[8], Unsupported.class));
    }

    @Test
    public void decodeIsEmptyWhenTruncated(){
        final byte[] bytes = SnapshotCodec.encode(everything(new Circle(1)));

        assertEquals(Optional.empty(), SnapshotCodec.decode(Arrays.copyOf(bytes, bytes.length - 1), Everything.class));
    }

    @Test
    public void decodeIsEmptyWithTrailingBytes(){
        final byte[] bytes = SnapshotCodec.encode(everything(new Circle(1)));

        assertEquals(Optional.empty(), SnapshotCodec.decode(Arrays.copyOf(bytes, bytes.length + 1), Everything.class));
    }

    @Test
    public void decodeIsEmptyForAnUnknownSealedTypeTag(){
        final byte[] bytes = SnapshotCodec.encode(new Everything(0, 0, false, new int[0], new boolean[0], new Inner(0, false), new Circle(0)));
        final int tagOffset = 4 + 8 + 1 + 4 + 4 + 4 + 1; //number, big, flag, bytes length, flags length, inner
        assertEquals(0, bytes[tagOffset], "test setup: expected Circle's tag here");
        bytes[tagOffset] = 2; //only Circle (0) and Square (1) exist

        assertEquals(Optional.empty(), SnapshotCodec.decode(bytes, Everything.class));
    }

    @Test
    public void decodeIsEmptyForAnArrayLengthLongerThanThePayload() throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)){
            out.writeInt(0); //number
            out.writeLong(0); //big
            out.writeBoolean(false); //flag
            out.writeInt(Integer.MAX_VALUE); //bytes length - would be a 2GB allocation if believed
        }

        assertEquals(Optional.empty(), SnapshotCodec.decode(buffer.toByteArray(), Everything.class));
    }

    @Test
    public void decodeIsEmptyForANegativeArrayLength() throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)){
            out.writeInt(0);
            out.writeLong(0);
            out.writeBoolean(false);
            out.writeInt(-1);
        }

        assertEquals(Optional.empty(), SnapshotCodec.decode(buffer.toByteArray(), Everything.class));
    }

    @Test
    public void decodeIsEmptyWhenARecordRejectsItsDecodedValues() throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)){
            out.writeInt(-5);
        }

        assertEquals(Optional.empty(), SnapshotCodec.decode(buffer.toByteArray(), Positive.class));
    }

    @Test
    public void roundTripsEmptyArrays(){
        final Everything empty = new Everything(1, 2, false, new int[0], new boolean[0], new Inner(3, true), new Circle(4));

        assertRecordsEqual(empty, SnapshotCodec.decode(SnapshotCodec.encode(empty), Everything.class).orElseThrow());
    }

    @Test
    public void roundTripsAnArrayThatRunsExactlyToTheEndOfThePayload(){
        final TrailingBytes trailing = new TrailingBytes(new int[]{1, 2, 3});

        assertRecordsEqual(trailing, SnapshotCodec.decode(SnapshotCodec.encode(trailing), TrailingBytes.class).orElseThrow());
    }
}
