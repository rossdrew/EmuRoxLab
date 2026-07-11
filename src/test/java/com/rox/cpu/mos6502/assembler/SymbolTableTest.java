package com.rox.cpu.mos6502.assembler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SymbolTableTest {

    @Test
    public void resolveReturnsTheDefinedAddress() {
        final SymbolTable symbolTable = new SymbolTable();
        symbolTable.define("LOOP", 0x8004, 1);

        assertEquals(0x8004, symbolTable.resolve("LOOP", 5));
    }

    @Test
    public void duplicateLabelDefinitionThrows() {
        final SymbolTable symbolTable = new SymbolTable();
        symbolTable.define("LOOP", 0x8004, 1);

        final AssemblyException exception = assertThrows(AssemblyException.class,
                () -> symbolTable.define("LOOP", 0x8010, 6));

        assertTrue(exception.getMessage().contains("Duplicate label: LOOP"));
        assertTrue(exception.getMessage().startsWith("Line 6:"));
    }

    @Test
    public void resolvingAnUndefinedLabelThrows() {
        final SymbolTable symbolTable = new SymbolTable();

        final AssemblyException exception = assertThrows(AssemblyException.class,
                () -> symbolTable.resolve("MISSING", 3));

        assertTrue(exception.getMessage().contains("Undefined label: MISSING"));
        assertTrue(exception.getMessage().startsWith("Line 3:"));
    }

    @Test
    public void asMapReflectsAllDefinedLabels() {
        final SymbolTable symbolTable = new SymbolTable();
        symbolTable.define("START", 0x8000, 1);
        symbolTable.define("LOOP", 0x8004, 3);

        assertEquals(Map.of("START", 0x8000, "LOOP", 0x8004), symbolTable.asMap());
    }

    @Test
    public void asMapIsImmutable() {
        final SymbolTable symbolTable = new SymbolTable();
        symbolTable.define("START", 0x8000, 1);

        final Map<String, Integer> labels = symbolTable.asMap();

        assertThrows(UnsupportedOperationException.class, () -> labels.put("OTHER", 0x9000));
    }
}
