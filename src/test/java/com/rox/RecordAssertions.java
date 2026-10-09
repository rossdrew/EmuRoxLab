package com.rox;

import java.lang.reflect.RecordComponent;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Deep equality for snapshot records: a record's own {@code equals()} compares array components by
 * reference, which is never what a snapshot round-trip test means. Recurses into nested records and
 * compares {@code int[]}/{@code boolean[]} by contents, naming the first mismatching component path.
 */
public final class RecordAssertions {
    private RecordAssertions(){
    }

    /** Takes {@code Object} so interface-typed snapshots (e.g. a sealed {@code MapperSnapshot}) can be passed as-is. */
    public static void assertRecordsEqual(final Object expected, final Object actual){
        assertTrue(expected instanceof Record, "expected a record, got " + expected.getClass());
        assertRecordsEqual((Record) expected, (Record) actual, expected.getClass().getSimpleName());
    }

    private static void assertRecordsEqual(final Record expected, final Record actual, final String path){
        assertSame(expected.getClass(), actual.getClass(), path);
        for (final RecordComponent component : expected.getClass().getRecordComponents()){
            final String componentPath = path + "." + component.getName();
            final Object expectedValue;
            final Object actualValue;
            try {
                expectedValue = component.getAccessor().invoke(expected);
                actualValue = component.getAccessor().invoke(actual);
            } catch (ReflectiveOperationException e){
                fail(componentPath, e);
                return;
            }
            if (expectedValue instanceof Record expectedRecord){
                assertRecordsEqual(expectedRecord, (Record) actualValue, componentPath);
            } else if (expectedValue instanceof int[] expectedArray){
                assertArrayEquals(expectedArray, (int[]) actualValue, componentPath);
            } else if (expectedValue instanceof boolean[] expectedArray){
                assertArrayEquals(expectedArray, (boolean[]) actualValue, componentPath);
            } else {
                assertTrue(Objects.equals(expectedValue, actualValue),
                        componentPath + ": expected " + expectedValue + " but was " + actualValue);
            }
        }
    }
}
