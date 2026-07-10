# EmuRox Lab

[![Build Status](https://github.com/rossdrew/EmuRoxLab/actions/workflows/gradle.yml/badge.svg)](https://github.com/rossdrew/EmuRoxLab/actions/workflows/gradle.yml)
[![Code Coverage](https://codecov.io/github/rossdrew/EmuRoxLab/graph/badge.svg)](https://codecov.io/github/rossdrew/EmuRoxLab)
[![Mutation Coverage](https://img.shields.io/endpoint?url=https://rossdrew.github.io/EmuRoxLab/pitest.json)](https://rossdrew.github.io/EmuRoxLab/pitest/)

[EmuRox](https://github.com/rossdrew/emuRox) has gotten a little behind the times and I'm now faced with two issues on that repo
 1. Bringing everything up to date so that it works
 2. Developing it

I don't want to do #1 yet.  It's no fun.  So I've created this project to run some experiments regarding #2.  Of course starting with the [6502](https://github.com/rossdrew/EmuRoxLab/tree/main/src/main/java/com/rox/cpu) and of course making sure we build [great regression testing](https://github.com/rossdrew/EmuRoxLab/tree/main/src/test/java) in from the start.  

# Approach

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

# Current state
1. a CPU clock that can run at a given Hz and Frame Rate
2. some memory structures in place so that instructions work on something sensible
3. a reusable (new) [functional enum](https://dev.to/rossdrew/functional-enums-in-java-34o1) design for building micro ops and op codes
4. All 6502 opcodes implemented
![Current progress...](https://github.com/rossdrew/EmuRoxLab/blob/main/resource/opcodes.svg)

In addition, the build system
1. Builds the code using Gradle and Kotlin
2. Uses JaCoCo to build code coverage data and Codecov to build a report from it
3. Uses pitest and a custom embedded Kotlin script to build a badge and only runs manually or when '+fullBuild' is in the commit message
4. Will skip builds on non code changes
5. Using [CodeRabbit](https://app.coderabbit.ai/) for automated AI code reviews on PRs

# Next up
1. Reviewing the whole design for optimisations
2. An assembler so that we can run more complex programs and make sure it works as expected.
3. Integrating this back into EmuRox
4. Looking at where else AI can be integrated.  For example, plans being tickets on a board or local AI review help.  Later, when there's a cohesive it might be good to have AI QA for system testing.

# Uncovered issues with my approach
- STA_ABS_X and LDA_ABS_X: LDA_ABS_X is a read instruction, so the 6502 can optimistically try to read from the partially indexed address first; if adding X does not cross a page, that read is valid and the instruction finishes in 4 cycles, but if the low byte overflows, the CPU needs one extra cycle to fix the high byte and reread from the correct address. STA_ABS_X is a write instruction, so the CPU cannot safely do that optimistic access because an early write to the wrong address would corrupt memory; it must always spend the indexing/dummy-read cycle before performing the real write, making it 5 cycles whether or not a page is crossed.  My approach needs fully duplicated MicroOps for the case where an instruction is optionally added and the case where it is always added.
- STA_IND_Y and STA_ABS_Y: same as above