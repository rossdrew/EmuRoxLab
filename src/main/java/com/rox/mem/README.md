# Memory Design

Memory is layered in order to allow maximum flexibility.  We can have standard memory where we can write to an
address in one call or latched where you set the address bus then you read or write separately.  I also implemented
the bus thinking of situations where we would want logging busses or multiple destination buses.  It may be overkill.
However, we have:

1. `Memory` interface that can be read from or written to
2. `RAM` implementation of `Memory` where you can put data at a given address
3. `MemoryBus` interface that can be read from or written to as if it were direct to the memory
  <i>this may be overkill but for now it stays</i>
4. `MemoryBus8Bit` implementation of `MemoryBus` created for the MOS6502 which masks the address to 16bits and data to 8 bits.
5. `LatchedMemoryBus` which splits address and data into a two step process; set the address then read/write to it.
6. `Latched8BitMemoryBus` implementation of `LatchedMemoryBus`, masking addresses to 16 bits and data to 8 bits

![Memory Implementation](https://github.com/rossdrew/EmuRoxLab/blob/main/resource/memory%20uml.png)