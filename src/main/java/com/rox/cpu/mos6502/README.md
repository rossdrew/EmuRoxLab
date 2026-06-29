# The MOS6502

The MOS Technology 6502 is an 8-bit microprocessor introduced in 1975, famous for combining low cost with high
performance. It has an 8-bit data bus, a 16-bit address bus capable of accessing 64KB of memory, and a small set of
registers consisting of the accumulator (A), index registers (X and Y), stack pointer (SP), processor status register
(P), and a 16-bit program counter (PC). The 6502 executes instructions through a sequence of clock-driven memory reads,
writes, and internal operations, and its efficient design made it the CPU of many influential systems including
the Nintendo Entertainment System, Commodore 64, Apple II, and Atari 2600. Its simple architecture, rich addressing
modes, and well-understood behaviour continue to make it a popular target for emulator development and computer
architecture study.

## Addressing Modes

### Immediate

The operand is embedded directly in the instruction stream and is used as the value itself.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Fetch operand and execute instruction |

---

### Zero Page

The operand byte specifies an address in page zero (`$0000-$00FF`), allowing faster access.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Fetch zero-page address |
| 3 | Read operand from zero-page address and execute instruction |

---

### Zero Page,X

The zero-page address is offset by the X register and wraps within page zero.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Fetch zero-page base address |
| 3 | Add X to address (wrap at `$FF`) |
| 4 | Read operand and execute instruction |

---

### Zero Page,Y

The zero-page address is offset by the Y register and wraps within page zero.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Fetch zero-page base address |
| 3 | Add Y to address (wrap at `$FF`) |
| 4 | Read operand and execute instruction |

---

### Absolute

The instruction contains a full 16-bit address from which the operand is read.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Fetch low address byte |
| 3 | Fetch high address byte |
| 4 | Read operand and execute instruction |

---

### Absolute,X

The 16-bit address is offset by X before reading the operand.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Fetch low address byte |
| 3 | Fetch high address byte and add X |
| 4 | Read operand and execute instruction |
| 5* | Dummy read and page correction if page crossed |

\* Only if page crossing occurs.

---

### Absolute,Y

The 16-bit address is offset by Y before reading the operand.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Fetch low address byte |
| 3 | Fetch high address byte and add Y |
| 4 | Read operand and execute instruction |
| 5* | Dummy read and page correction if page crossed |

\* Only if page crossing occurs.

---

### Indirect

Used only by `JMP`, the instruction contains a pointer to another 16-bit address.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Fetch pointer low byte |
| 3 | Fetch pointer high byte |
| 4 | Read target low byte |
| 5 | Read target high byte |
| 6 | Load target address into PC |

---

### (Indirect,X) ? Indexed Indirect

A zero-page pointer is first indexed by X, then dereferenced to obtain the effective address.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Fetch zero-page pointer |
| 3 | Add X and read effective address low byte |
| 4 | Read effective address high byte |
| 5 | Read operand |
| 6 | Execute instruction |

---

### (Indirect),Y ? Indirect Indexed

A 16-bit address is read from zero page and then indexed by Y.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Fetch zero-page pointer |
| 3 | Read effective address low byte |
| 4 | Read effective address high byte and add Y |
| 5 | Read operand and execute instruction |
| 6* | Dummy read and page correction if page crossed |

\* Only if page crossing occurs.

---

### Relative

Used by branch instructions, the operand is treated as a signed offset from the current program counter.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Fetch signed offset |
| 3 | Apply branch if condition met |
| 4* | Correct page if branch crosses page boundary |

\* Only if branch is taken and crosses a page.

---

### Accumulator

The instruction operates directly on the accumulator register without accessing memory.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Execute operation on A |

---

### Implied

The instruction does not require an operand because it operates on CPU state directly.

| Cycle | Step |
|--------|------|
| 1 | Fetch opcode |
| 2 | Execute instruction |