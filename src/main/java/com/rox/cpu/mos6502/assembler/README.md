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
