# The 6502 Assembler

This package is a small, hand-rolled two-pass assembler that turns 6502 text source (mnemonics, operands, labels,
comments) into the raw bytes the emulator can load into memory and execute. It works purely from the public
`MOS6502OpCode` enum in the parent package - mnemonic + addressing mode resolves directly to the concrete opcode via
a lookup built once from the enum's own constant names, so no new addressing-mode knowledge is duplicated onto the
core CPU model. Parsing is entirely mnemonic-agnostic (an operand's shape alone decides most addressing modes), which
lets pass 1 size every instruction - and therefore resolve every address, including forward label references - before
pass 2 ever needs to know what a label actually points to.

## Development

There's nothing new in putting a simple compiler together.  Therefore I thought it would be a good opportunity to let
Claude lead the development of my MOS6502 assembler.  Walking through a /plan skill and developing a 5 phase plan.  I
acted as a pair programmer and reviewer, manually approving each step then each phase then the entire PR.

## Walkthrough from source text to `AssembledProgram`

1. **`LineScanner`** splits the source into `SourceLine`s: comments and blank lines stripped, each remaining line
   broken into an optional label, a mnemonic, and raw operand text.
2. **`OperandParser`** parses each line's operand text into an `Operand` - deciding IMMEDIATE, ZERO_PAGE(+X/Y),
   ABSOLUTE(+X/Y), INDIRECT(+X/Y), or ACCUMULATOR from shape alone. A bare label or no operand at all is left as an
   unresolved `Operand` sentinel, since only the mnemonic can disambiguate those.
3. **`InstructionResolver`** (via **`OpCodeTable`**) resolves each mnemonic + `Operand` to a concrete
   `MOS6502OpCode`, picking IMPLIED/ACCUMULATOR or RELATIVE/ABSOLUTE for the two sentinel cases, and falling back
   from zero page to absolute when a mnemonic has no zero-page form.
4. **`Assembler`**, pass 1: walks the `SourceLine`s, defining each label's address in a **`SymbolTable`** and
   recording every resolved instruction as a **`PendingInstruction`** - this fully determines every instruction's
   length (and so every subsequent address) without ever needing a label's value.
5. **`Assembler`**, pass 2: emits bytes for each `PendingInstruction`, resolving any label reference against the
   now-complete `SymbolTable` (little-endian for absolute addresses, a range-checked signed offset for branches),
   producing the final **`AssembledProgram`** (bytes, start address, and the resolved label table).

## Language Reference

### Line structure

Each line may contain an optional label, an instruction, and a comment, in that order. Comments start with `;` and
run to the end of the line; blank lines are ignored.

### Labels

A label is a bare identifier followed by `:`. It marks the address of whatever comes next - either a later
instruction, or another label if it stands alone on its own line (`LOOP: STA $0200,X` and a label-only `LOOP:` line
both work). Labels may be referenced before they're defined (forward references) as well as after (backward
references); duplicate definitions and undefined references are both rejected.

### Numbers

Numeric literals are written in hex with a `$` prefix (`$FF`) or as plain decimal (`255`) - both mean the same value.

### Addressing modes

| Mode | Syntax | Example |
|---|---|---|
| Implied | *(no operand)* | `INX` |
| Accumulator | `A` or *(no operand)* | `ASL A` |
| Immediate | `#value` | `LDA #$09` |
| Zero page | `value` | `LDA $10` |
| Zero page,X | `value,X` | `LDA $10,X` |
| Zero page,Y | `value,Y` | `LDX $10,Y` |
| Absolute | `value` | `LDA $0200` |
| Absolute,X | `value,X` | `STA $0200,X` |
| Absolute,Y | `value,Y` | `STA $0200,Y` |
| Indirect | `(value)` | `JMP ($1234)` |
| Indirect,X | `(value,X)` | `LDA ($10,X)` |
| Indirect,Y | `(value),Y` | `LDA ($10),Y` |
| Relative | `label` | `BNE LOOP` |

Zero page vs. absolute is decided automatically rather than written explicitly: a hex literal with 1-2 digits
(`$10`) is zero page, 3-4 digits (`$0200`) is absolute; a decimal literal is zero page if it fits in a byte (`≤255`)
and absolute otherwise - matching how real 6502 assemblers behave. A label used as an operand resolves to a relative
branch offset for branch mnemonics, or an absolute address for anything else that supports it (e.g. `JMP`/`JSR`); if
a mnemonic has no zero-page form at all, a zero-page-shaped operand is automatically widened to its absolute
equivalent.

### Errors

Every failure is an `AssemblyException` naming the source line. This covers: unknown mnemonics, an addressing mode a
mnemonic doesn't support (the message lists what it does support), operand values out of range for their byte
width, duplicate label definitions, undefined label references, and branch targets more than 127 bytes away.

### Not supported

There are no assembler directives (no `ORG`, `EQU`, `.byte`, etc.) - the load address is passed to
`Assembler.assemble(source, startAddress)` directly, not written in source. There are no macros, and a label used as
an operand never auto-downgrades to zero page even if its resolved address would fit, to keep instruction sizing
fully determined in a single pass.
