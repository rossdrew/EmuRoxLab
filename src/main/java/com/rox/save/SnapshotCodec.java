package com.rox.save;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Optional;

/**
 * Turns a snapshot record tree (see {@link SystemSnapshot}) into a live-snapshot save payload and
 * back, walking each record's components in declaration order - so a new field on any component's
 * snapshot record is picked up without touching this class, at the cost of that reordering or adding
 * components changes the byte layout (bump {@code SaveCodec}'s version when that matters).
 *
 * <p>Supported component types: {@code int}, {@code long}, {@code boolean}, {@code int[]} (every
 * element must be a byte value, 0-255 - all snapshot arrays are RAM/register bytes - and is stored as
 * one byte), {@code boolean[]}, nested records, and sealed interfaces of records (stored as the index
 * of the concrete type in {@link Class#getPermittedSubclasses()}, then that record).
 */
final class SnapshotCodec {
    private SnapshotCodec(){
    }

    static byte[] encode(final Record record){
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)){
            writeRecord(out, record);
        } catch (IOException e){
            //in-memory only, same reasoning as SaveCodec.encode()
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    /** Empty if {@code bytes} doesn't decode cleanly into exactly one {@code type} with nothing left over. */
    static <T extends Record> Optional<T> decode(final byte[] bytes, final Class<T> type){
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))){
            final T record = type.cast(readRecord(in, type));
            return in.available() == 0 ? Optional.of(record) : Optional.empty();
        } catch (IOException | IllegalArgumentException e){
            //truncated (EOFException), a bad sealed-type tag/array length, or a record's own constructor
            //rejecting what was read - all just "not a usable snapshot"
            return Optional.empty();
        }
    }

    private static void writeRecord(final DataOutputStream out, final Record record) throws IOException {
        for (final RecordComponent component : record.getClass().getRecordComponents()){
            write(out, component.getType(), accessorValue(component, record));
        }
    }

    private static void write(final DataOutputStream out, final Class<?> type, final Object value) throws IOException {
        if (type == int.class){
            out.writeInt((Integer) value);
        } else if (type == long.class){
            out.writeLong((Long) value);
        } else if (type == boolean.class){
            out.writeBoolean((Boolean) value);
        } else if (type == int[].class){
            final int[] array = (int[]) value;
            out.writeInt(array.length);
            for (final int element : array){
                if (element < 0 || element > 0xFF){
                    throw new IllegalArgumentException("Snapshot arrays must hold byte values (0-255), found " + element);
                }
                out.writeByte(element);
            }
        } else if (type == boolean[].class){
            final boolean[] array = (boolean[]) value;
            out.writeInt(array.length);
            for (final boolean element : array){
                out.writeBoolean(element);
            }
        } else if (type.isRecord()){
            writeRecord(out, (Record) value);
        } else if (type.isSealed()){
            out.writeByte(Arrays.asList(type.getPermittedSubclasses()).indexOf(value.getClass()));
            writeRecord(out, (Record) value);
        } else {
            throw new IllegalArgumentException("Unsupported snapshot component type " + type);
        }
    }

    private static Record readRecord(final DataInputStream in, final Class<?> type) throws IOException {
        final RecordComponent[] components = type.getRecordComponents();
        final Class<?>[] componentTypes = new Class<?>[components.length];
        final Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++){
            componentTypes[i] = components[i].getType();
            values[i] = read(in, componentTypes[i]);
        }
        try {
            final Constructor<?> canonical = type.getDeclaredConstructor(componentTypes);
            return (Record) canonical.newInstance(values);
        } catch (InvocationTargetException e){
            throw new IllegalArgumentException("Snapshot record rejected its decoded values", e.getCause());
        } catch (ReflectiveOperationException e){
            throw new IllegalStateException("Snapshot record " + type + " has no usable canonical constructor", e);
        }
    }

    private static Object read(final DataInputStream in, final Class<?> type) throws IOException {
        if (type == int.class){
            return in.readInt();
        } else if (type == long.class){
            return in.readLong();
        } else if (type == boolean.class){
            return in.readBoolean();
        } else if (type == int[].class){
            final int[] array = new int[checkedLength(in)];
            for (int i = 0; i < array.length; i++){
                array[i] = in.readUnsignedByte();
            }
            return array;
        } else if (type == boolean[].class){
            final boolean[] array = new boolean[checkedLength(in)];
            for (int i = 0; i < array.length; i++){
                array[i] = in.readBoolean();
            }
            return array;
        } else if (type.isRecord()){
            return readRecord(in, type);
        } else if (type.isSealed()){
            final Class<?>[] permitted = type.getPermittedSubclasses();
            final int tag = in.readUnsignedByte();
            if (tag >= permitted.length){
                throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " type tag " + tag);
            }
            return readRecord(in, permitted[tag]);
        }
        throw new IllegalArgumentException("Unsupported snapshot component type " + type);
    }

    /** Rejects a corrupt length before it can drive a huge allocation - same guard as {@code SaveCodec.decode()}'s payload length. */
    private static int checkedLength(final DataInputStream in) throws IOException {
        final int length = in.readInt();
        if (length < 0 || length > in.available()){
            throw new IllegalArgumentException("Array length " + length + " exceeds the remaining payload");
        }
        return length;
    }

    private static Object accessorValue(final RecordComponent component, final Record record){
        try {
            return component.getAccessor().invoke(record);
        } catch (ReflectiveOperationException e){
            throw new IllegalStateException("Can't read snapshot component " + component, e);
        }
    }
}
