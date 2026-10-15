package com.rox;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.INesRom;
import com.rox.cartridge.RomLoader;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * Builds iNES ROM files for tests, e.g.
 * <pre>
 * TestRoms.nrom().at(0x9000, 0x4C, 0x00, 0x90).resetVector(0x9000).cartridge(); //JMP $9000 forever
 * </pre>
 * Sizes are in bytes and written to the header in its own units (16KB PRG, 8KB CHR), rounding down -
 * so a deliberately undersized ROM (e.g. 8KB of PRG, headed as 0 banks) is still expressible.
 */
public final class TestRoms {
    private static final int HEADER_SIZE = 16;
    private static final int PRG_UNIT = 0x4000;
    private static final int CHR_UNIT = 0x2000;
    private static final int CPU_PRG_START = 0x8000;
    private static final int NMI_VECTOR = 0xFFFA;
    private static final int RESET_VECTOR = 0xFFFC;

    private TestRoms(){
    }

    /** Mapper 0 with one 16KB PRG-ROM bank, all zero, and no CHR-ROM (so CHR-RAM). */
    public static Builder nrom(){
        return mapper(0);
    }

    /** {@code mapperNumber} with one 16KB PRG-ROM bank, all zero, and no CHR-ROM (so CHR-RAM). */
    public static Builder mapper(final int mapperNumber){
        return new Builder(mapperNumber);
    }

    public static final class Builder {
        private final int mapperNumber;
        private byte[] prg = new byte[PRG_UNIT];
        private byte[] chr = new byte[0];
        private boolean verticalMirroring;
        private boolean battery;

        private Builder(final int mapperNumber){
            this.mapperNumber = mapperNumber;
        }

        /** Resizes PRG-ROM, keeping what fits. */
        public Builder prgSize(final int bytes){
            prg = Arrays.copyOf(prg, bytes);
            return this;
        }

        /** Resizes CHR-ROM, keeping what fits - 0 means the board uses CHR-RAM. */
        public Builder chrSize(final int bytes){
            chr = Arrays.copyOf(chr, bytes);
            return this;
        }

        public Builder verticalMirroring(final boolean vertical){
            verticalMirroring = vertical;
            return this;
        }

        public Builder battery(){
            battery = true;
            return this;
        }

        /** {@code bytes} from PRG-ROM offset {@code offset}. */
        public Builder prg(final int offset, final int... bytes){
            put(prg, offset, bytes);
            return this;
        }

        /**
         * {@code bytes} at CPU address {@code cpuAddress} ({@code $8000+}), assuming PRG-ROM is mapped there
         * unbanked - smaller ROMs mirror, as on NROM.
         */
        public Builder at(final int cpuAddress, final int... bytes){
            return prg((cpuAddress - CPU_PRG_START) % prg.length, bytes);
        }

        /** {@code bytes} from CHR-ROM offset {@code offset}. */
        public Builder chr(final int offset, final int... bytes){
            put(chr, offset, bytes);
            return this;
        }

        /** Every PRG-ROM byte from its offset, e.g. {@code offset -> offset} for "position-encoded" banks. */
        public Builder fillPrg(final IntUnaryOperator byteAtOffset){
            fill(prg, byteAtOffset);
            return this;
        }

        public Builder fillChr(final IntUnaryOperator byteAtOffset){
            fill(chr, byteAtOffset);
            return this;
        }

        /** The first byte of each {@code bankSize} PRG-ROM bank set to that bank's index - shows which bank is mapped. */
        public Builder markPrgBanks(final int bankSize){
            mark(prg, bankSize);
            return this;
        }

        /** The first byte of each {@code bankSize} CHR-ROM bank set to that bank's index. */
        public Builder markChrBanks(final int bankSize){
            mark(chr, bankSize);
            return this;
        }

        public Builder resetVector(final int address){
            return at(RESET_VECTOR, address & 0xFF, address >> 8);
        }

        public Builder nmiVector(final int address){
            return at(NMI_VECTOR, address & 0xFF, address >> 8);
        }

        /** The whole file: header, PRG-ROM, then CHR-ROM. */
        public byte[] bytes(){
            final byte[] file = new byte[HEADER_SIZE + prg.length + chr.length];
            file[0] = 'N';
            file[1] = 'E';
            file[2] = 'S';
            file[3] = 0x1A;
            file[4] = (byte) (prg.length / PRG_UNIT);
            file[5] = (byte) (chr.length / CHR_UNIT);
            file[6] = (byte) (((mapperNumber & 0x0F) << 4) | (battery ? 0x02 : 0) | (verticalMirroring ? 0x01 : 0));
            file[7] = (byte) (mapperNumber & 0xF0);
            System.arraycopy(prg, 0, file, HEADER_SIZE, prg.length);
            System.arraycopy(chr, 0, file, HEADER_SIZE + prg.length, chr.length);
            return file;
        }

        public INesRom rom(){
            return INesRom.parse(bytes());
        }

        public Cartridge cartridge(){
            return RomLoader.fromBytes(bytes());
        }

        private static void put(final byte[] target, final int offset, final int... bytes){
            for (int i = 0; i < bytes.length; i++){
                target[offset + i] = (byte) bytes[i];
            }
        }

        private static void mark(final byte[] target, final int bankSize){
            for (int bank = 0; bank * bankSize < target.length; bank++){
                target[bank * bankSize] = (byte) bank;
            }
        }

        private static void fill(final byte[] target, final IntUnaryOperator byteAtOffset){
            for (int i = 0; i < target.length; i++){
                target[i] = (byte) byteAtOffset.applyAsInt(i);
            }
        }
    }
}
