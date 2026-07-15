---
name: add-6502-opcode
description: Use when implementing new MOS6502 opcode(s) in this emulator (the phase 1-5 opcode plan). Encodes the file-by-file checklist and test conventions so new opcodes stay consistent with existing ones.
---

# Add a 6502 Opcode

Checklist for adding one or more MOS6502 opcodes to this emulator. Follow in order; only steps 3-4 are mandatory for every opcode — steps 1-2 apply only when there's nothing existing to reuse.

## 1. ALU function (if needed)
- Add to `src/main/java/com/rox/cpu/mos6502/MOS6502ALU.java` only if the opcode needs arithmetic/comparison logic not already exposed (e.g. `cpx`, `cpy`). Reuse an existing ALU method (`cmp`, `adc`, etc.) if its semantics already match.
- If added, add a `@ParameterizedTest` + `@CsvSource` test to `MOS6502ALUTest.java`. Cover equal/greater/less comparisons (if a compare), boundary wraps (0x00/0xFF), and assert both the resulting flags and that the source register is left unmodified.

## 2. MicroOp enum entry (if needed)
- Add to `src/main/java/com/rox/cpu/mos6502/MOS6502MicroOp.java` only if no existing microop already does the job — reuse where possible (e.g. `SEI_IMP` reuses the existing `INTERRUPT` microop rather than adding a new one).
- Javadoc format: `/** <description> <code>register := expr</code> */` directly above the constant, matching the terse register-transfer notation already used throughout the file.
- If added, add a matching test to `MOS6502MicroOpTest.java`.

## 3. OpCode enum entry (always)
- Add to `src/main/java/com/rox/cpu/mos6502/MOS6502OpCode.java`: one enum constant per addressing-mode variant (e.g. `CPX_I`, `CPX_Z`, `CPX_ABS`), each with its opcode byte and `clockTicks(opsInTick(...), ...)` describing the cycle-by-cycle microop sequence. Match the existing tick sequence already used by other opcodes in the same addressing mode.
- Add an `@Nested` class per opcode to `MOS6502OpCodeTest.java` with `@ParameterizedTest`/`@CsvSource` covering flag-setting behavior and boundary wraps, plus an assertion that unrelated registers/flags are untouched (mirror the `DEX`/`INY` nested test style).

## 4. Regenerate the progress chart (always)
```sh
./generate-opcode-chart.sh
```
Overwrites `resource/opcodes.svg` from `MOS6502OpCode.java`. Run after opcodes are added; commit the regenerated SVG alongside the code.

## Notes
- Work one phase of the opcode plan at a time — implement + test, then wait for the user's manual build/test verification before starting the next phase.
- Don't fabricate an ALU function or microop just to have one — reuse is the common case for simple opcodes (flag set/clear, register inc/dec).
