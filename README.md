# EmuRox Lab

[![Build Status](https://github.com/rossdrew/EmuRoxLab/actions/workflows/gradle.yml/badge.svg)](https://github.com/rossdrew/EmuRoxLab/actions/workflows/gradle.yml)
[![Code Coverage](https://codecov.io/github/rossdrew/EmuRoxLab/graph/badge.svg)](https://codecov.io/github/rossdrew/EmuRoxLab)
[![Mutation Coverage](https://img.shields.io/endpoint?url=https://rossdrew.github.io/EmuRoxLab/pitest.json)](https://rossdrew.github.io/EmuRoxLab/pitest.json)

[EmuRox](https://github.com/rossdrew/emuRox) has gotten a little behind the times and I'm now faced with two issues on that repo
 1. Bringing everything up to date so that it works
 2. Developing it

I don't want to do #1 yet.  It's no fun.  So Ive created this project to run some experiments regarding #2.

So far
1. We have a CPU clock that can run at a given Hz and Frame Rate
2. We have some memory structures in place so that instructions work on something sensible


Next up
1. Working on a good abstraction for Opcodes, Micro Operations and ALU operations