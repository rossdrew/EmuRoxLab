# EmuRox Lab

[![Build Status](https://github.com/rossdrew/EmuRoxLab/actions/workflows/gradle.yml/badge.svg)](https://github.com/rossdrew/EmuRoxLab/actions/workflows/gradle.yml)
[![Code Coverage](https://codecov.io/github/rossdrew/EmuRoxLab/graph/badge.svg)](https://codecov.io/github/rossdrew/EmuRoxLab)
[![Mutation Coverage](https://img.shields.io/endpoint?url=https://rossdrew.github.io/EmuRoxLab/pitest.json)](https://rossdrew.github.io/EmuRoxLab/pitest/)

[EmuRox](https://github.com/rossdrew/emuRox) has gotten a little behind the times and I'm now faced with two issues on that repo
 1. Bringing everything up to date so that it works
 2. Developing it

I don't want to do #1 yet.  It's no fun.  So I've created this project to run some experiments regarding #2.  Of course starting with the [6502](https://github.com/rossdrew/EmuRoxLab/tree/main/src/main/java/com/rox/cpu) and of course making sure we build [great regression testing](https://github.com/rossdrew/EmuRoxLab/tree/main/src/test/java) in from the start.  

# The AI Development Story so Far

I'm using this to get familiar with sensibly integrating AI into my workflow, rather than the current industry standard which is to put it everywhere and gain speed over any achievable human governance.  Currently that means coming up with a design, writing a poc manually that lays down
 1. My coding, commenting & formatting standards
 2. The initial design choices and paradigms
 2. The test strategy

 Then I started with

 1. using AI as an architect pair to discuss, iterate and build on the design
 2. generating extensive AI unit tests (validating them manually and refining the style)
 3. using AI B to act as a test expert to discuss additional test options
 4. generating implementation manually and using AI where sensible, often I find the generic AI approach is not what I need

and after a little over half the opcodes were implemented, setting a standard moved on to

 1. Claude CLI using the /plan skill and developing a 5 part plan, goruping similar opcodes together
 2. Manually stepping through the subtasks in the plan, approving as it went
 3. Approving the whole piece of work before committing to a branch
 4. Using PR (tagged as AI work), link in AI code reviews with CodeRabbit and doing one more manual review

I want to get to a quicker, more automated agent reviewer loop but I'm finding issues with Claudes output too often to be comfortable with.

# Current state of Development
1. a CPU clock that can run at a given Hz and Frame Rate
2. Some memory structures in place so that instructions work on something sensible
3. A [6502 implemented](https://github.com/rossdrew/EmuRoxLab/blob/main/resource/opcodes.svg) using a new approach to the old [functional enum](https://dev.to/rossdrew/functional-enums-in-java-34o1)
4. A [6502 assembler](https://github.com/rossdrew/EmuRoxLab/tree/main/src/main/java/com/rox/cpu/mos6502/assembler)
5. An APU (Audio Processing Unit) implementation capable of playing pulse, triangle, noise and dmc channels
6. Real `.nes` ROM loading (NROM and MMC1 mappers), so actual game ROMs can be run instead of just hand-assembled test programs
7. A PPU with correct vblank/NMI timing, full CHR-ROM/CHR-RAM/nametable-mirroring/palette memory wiring and OAM DMA - no pixel rendering yet
8. A controller stub and a CLI demo ([`RomAudioSmokeDemo`](https://github.com/rossdrew/EmuRoxLab/blob/main/src/main/java/com/rox/RomAudioSmokeDemo.java)) so `.nes` files can actually be run and heard

## Next up
1. Reviewing the whole design for optimisations
2. More mapper support (UxROM, CNROM, MMC3, ...) - NROM and MMC1 only for now
3. PPU background/sprite rendering to a framebuffer, then a real screen to view it
Y. Integrating this back into EmuRox
Z. Looking at where else AI can be integrated.  For example, plans being tickets on a board or local AI review help.  Later, when there's a cohesive it might be good to have AI QA for system testing.

## Known Issues

#### 6502
- STA_ABS_X and LDA_ABS_X: LDA_ABS_X is a read instruction, so the 6502 can optimistically try to read from the partially indexed address first; if adding X does not cross a page, that read is valid and the instruction finishes in 4 cycles, but if the low byte overflows, the CPU needs one extra cycle to fix the high byte and reread from the correct address. STA_ABS_X is a write instruction, so the CPU cannot safely do that optimistic access because an early write to the wrong address would corrupt memory; it must always spend the indexing/dummy-read cycle before performing the real write, making it 5 cycles whether or not a page is crossed.  My approach needs fully duplicated MicroOps for the case where an instruction is optionally added and the case where it is always added.
- STA_IND_Y and STA_ABS_Y: same as above
#### APU
- Testing actual hardware calls is difficult so we take a small hit on mutation coverage there
- DMC sample fetches don't stall the CPU for 1-4 cycles as real hardware does, which may affect games that depend on exact DMC DMA timing
#### Cartridges / Mappers
- Only NROM (mapper 0) and MMC1 (mapper 1) are implemented; other common mappers (UxROM, CNROM, MMC3, ...) aren't yet - loading one fails with a clear "unsupported mapper" error rather than silently misbehaving
#### PPU
- Headless only: no pixel rendering or framebuffer yet, though the full CHR/nametable/palette memory space and OAM DMA are wired up
- OAM DMA always stalls the CPU for a fixed 514 cycles rather than the real hardware's cycle-accurate 513/514 (odd/even alignment isn't tracked), which may affect cycle-sensitive software
- Four-screen nametable mirroring isn't modeled (only horizontal/vertical/single-screen)
#### Controller
- No real input: $4016/$4017 always report "no buttons pressed", so games' input-polling loops behave sanely but nothing is ever actually pressed

# The Build System
1. Builds the code using Gradle and Kotlin
2. Uses JaCoCo to build code coverage data and Codecov to build a report from it
3. Uses pitest and a custom embedded Kotlin script to build a badge 
   1. only runs manually or when '+fullBuild' is in the commit message
   2. expensive so can be run on a subset using `-PpitestScope=com.rox.apu.*` 
4. Will skip builds on non code changes
5. Using [CodeRabbit](https://app.coderabbit.ai/) for automated AI code reviews on PRs