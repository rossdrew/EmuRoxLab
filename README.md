# EmuRox Lab

[![Build Status](https://github.com/rossdrew/EmuRoxLab/actions/workflows/gradle.yml/badge.svg)](https://github.com/rossdrew/EmuRoxLab/actions/workflows/gradle.yml)
[![Code Coverage](https://codecov.io/github/rossdrew/EmuRoxLab/graph/badge.svg)](https://codecov.io/github/rossdrew/EmuRoxLab)
[![Mutation Coverage](https://img.shields.io/endpoint?url=https://rossdrew.github.io/EmuRoxLab/pitest.json)](https://rossdrew.github.io/EmuRoxLab/pitest/)

[EmuRox](https://github.com/rossdrew/emuRox) has gotten a little behind the times and I'm now faced with two issues on that repo
 1. Bringing everything up to date so that it works
 2. Developing it

I don't want to do #1 yet.  It's no fun.  So I've created this project to run some experiments regarding #2.  Of course starting with the [6502](https://github.com/rossdrew/EmuRoxLab/tree/main/src/main/java/com/rox/cpu) and of course making sure we build [great regression testing](https://github.com/rossdrew/EmuRoxLab/tree/main/src/test/java) in from the start.  

# How am I using AI

I'm using this to get familiar with sensibly integrating AI into my workflow, rather than the current industry standard which is to put it everywhere and gain speed over any achievable human governance.  
- Coding isn't being taken over entirely by UI (yet?) so that means I want this to be a sensible, human-accessible abstraction.  
- AI driven design is token based, and that means we want to limit the tokens we give it when we give it a task. So good abstractions and single responsibility code are even more essential than they always were.
- As AI moves fast, governance is key. That means good outputs for auditing and [solid testing](https://github.com/rossdrew/EmuRoxLab/tree/main/src/test/java) to catch regression.

That means control over the overall design, the abstractions that are built and the code that is written to support & test those abstractions.  

I began this journey by

 1. using AI A (ChatGPT Web) as an architect pair to discuss, iterate and build on the design
 2. generating extensive AI (ChatGPT CLI) unit tests (validating them manually and refining the style)
 3. using AI B (ChatGPT Web) to act as a test expert to discuss additional test options
 4. generating implementation manually and using AI where sensible (GitHub Copilot), often I find the generic AI approach is not what I need

after a little over half the opcodes were manually implemented. I believe setting a good standard, I expanded my AI use:

 1. Claude CLI using the /plan skill and developing a 5 part plan, grouping similar opcodes together
 2. Manually stepping through the subtasks in the plan, approving as it went
 3. Approving the whole piece of work before committing to a branch
 4. Using PR (tagged as AI work), link in AI code reviews with CodeRabbit and doing one more manual review

I review all of this manually so that I understand it, maintain all of the quality considerations from above and make sure I can communicate meaningfully with AI on next steps.  Reducing the amount of rework, because rework is more tokens and more room for error.  I've moved from manual review and commit to review within PRs.
At the same time I'm reviewing I use [CodeRabbit](https://www.coderabbit.ai/?campaign_id=20944421732&ad_group_id=158532047035) as a co-reviewer.  

In future, I want to explore next levels of AI development integration:
 1. Make this multi-agent. Developer, pair-programmer, reviewer & QA.
 2. Turn this into a semi-autonomous workflow based on a task list provided by myself. Work with orchestration tools to analyze and refine outputs and token usage
 3. A multi-agent autonomous team where the task list is also built and my role is refinement 

# Current state of Development
1. a CPU clock that can run at a given Hz and Frame Rate
2. [Memory structures](https://github.com/rossdrew/EmuRoxLab/tree/main/src/main/java/com/rox/mem) and a bus system.
3. A [6502 implemented](https://github.com/rossdrew/EmuRoxLab/blob/main/resource/opcodes.svg) using a new approach to the old [functional enum](https://dev.to/rossdrew/functional-enums-in-java-34o1)
4. A [6502 assembler](https://github.com/rossdrew/EmuRoxLab/tree/main/src/main/java/com/rox/cpu/mos6502/assembler)
5. An APU (Audio Processing Unit) implementation capable of playing pulse, triangle, noise and dmc channels
   6. A smoke test class capable of playing audio 
6. `.nes` ROM loading (NROM and MMC1 mappers), so actual game ROMs can be run instead of just hand-assembled test programs
7. A PPU with correct vblank/NMI timing, full CHR-ROM/CHR-RAM/nametable-mirroring/palette memory wiring and OAM DMA.
   8. A simple debug UI for visualising the structures from memory and playing sound from running ROMs
   ![Current UI](https://github.com/rossdrew/EmuRoxLab/blob/main/resource/ui_010826.png)

## Next up
1. Reviewing the whole design for optimisations
2. More mapper support (UxROM, CNROM, MMC3, ...) - NROM and MMC1 only for now
3. PPU background/sprite rendering to a framebuffer, then a visualization of the actual running screen
Y. Integrating this back into EmuRox

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