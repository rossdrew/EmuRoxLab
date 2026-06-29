package com.rox.cpu.mos6502;

import org.junit.jupiter.api.Test;

public class MOS6502IntegrationTest {
    @Test
    public void simpleProgram(){
        final String simpleProgram = """
                        LDX #$00      ; X = 0
                        LDA #$09      ; A = value to store
                
                LOOP:   STA $0200,X   ; Store A at $0200 + X
                        INX           ; X = X + 1
                        CPX #$FF      ; Have we reached the end?
                        BNE LOOP      ; No, continue
                
                        BRK           ; Stop
                """;

        //TODO compile the program
        //TODO put the program into memory
        //TODO Create a MOS6502
        //TODO Set the program counter to the start of the program
        //TODO run the program
        //TODO makde sure memory in in the expected state
    }
}
