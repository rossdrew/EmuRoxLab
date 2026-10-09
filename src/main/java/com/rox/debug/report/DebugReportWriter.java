package com.rox.debug.report;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import com.rox.ppu.PpuSnapshot;
import com.rox.save.SaveFileFormat;
import com.rox.save.SaveMetadata;
import com.rox.save.SaveType;
import com.rox.save.SystemSnapshot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;

/**
 * Writes a {@link DebugReport} as a folder of files a person (or an AI assistant) can dig through:
 * <ul>
 *     <li>{@code screenshot.png} - the last complete frame (absent if none had been shown yet)</li>
 *     <li>{@code framebuffer-partial.png} - the PPU's part-drawn frame at the moment of capture</li>
 *     <li>{@code description.txt} - what the person flagging the issue saw</li>
 *     <li>{@code state.txt} - CPU registers/flags, the next instructions, PPU and mapper state</li>
 *     <li>{@code ram.bin}/{@code ram.hex.txt} - CPU RAM {@code $0000-$1FFF}</li>
 *     <li>{@code oam.bin}, {@code nametables.bin}, {@code palette.bin}, {@code prg-ram.bin}, and
 *     {@code chr-ram.bin} (CHR-RAM boards only) - raw dumps</li>
 *     <li>{@code <folder name>.sav} - the restorable {@link SystemSnapshot}</li>
 * </ul>
 */
public final class DebugReportWriter {
    private static final int SCREEN_WIDTH = 256;
    private static final int SCREEN_HEIGHT = 240;
    /** $0000-$1FFF: the CPU's internal-RAM window. Covers the $0800-$1FFF mirror range too, so stray writes there show up. */
    static final int RAM_DUMP_SIZE = 0x2000;
    private static final int HEX_BYTES_PER_ROW = 16;
    private static final DateTimeFormatter HEADER_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DebugReportWriter(){
    }

    /** Writes every file into {@code directory} (created if needed), naming the {@code .sav} after the directory. */
    public static void write(final Path directory, final DebugReport report) throws IOException {
        Files.createDirectories(directory);
        final SystemSnapshot snapshot = report.snapshot();
        final PpuSnapshot ppu = snapshot.ppu();

        if (report.screenshotRgb() != null){
            writePng(directory.resolve("screenshot.png"), report.screenshotRgb());
        }
        writePng(directory.resolve("framebuffer-partial.png"), report.partialFrameRgb());
        Files.writeString(directory.resolve("description.txt"), report.description(), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("state.txt"), formatState(report), StandardCharsets.UTF_8);

        final int[] ram = Arrays.copyOf(snapshot.ram(), RAM_DUMP_SIZE);
        Files.write(directory.resolve("ram.bin"), toBytes(ram));
        Files.writeString(directory.resolve("ram.hex.txt"), hexDump(ram), StandardCharsets.UTF_8);
        Files.write(directory.resolve("oam.bin"), toBytes(ppu.oam()));
        Files.write(directory.resolve("nametables.bin"), toBytes(ppu.nametableRam()));
        Files.write(directory.resolve("palette.bin"), toBytes(ppu.paletteRam()));
        Files.write(directory.resolve("prg-ram.bin"), toBytes(snapshot.mapper().prgRam()));
        if (snapshot.mapper().chrRam().length > 0){
            Files.write(directory.resolve("chr-ram.bin"), toBytes(snapshot.mapper().chrRam()));
        }

        final SaveMetadata metadata = new SaveMetadata(SaveType.LIVE_SNAPSHOT,
                report.capturedAt().atZone(ZoneId.systemDefault()).toInstant(), 0);
        SaveFileFormat.writeLiveSnapshot(directory.resolve(directory.getFileName() + ".sav"), metadata, snapshot);
    }

    static String formatState(final DebugReport report){
        final MOS6502Snapshot cpu = report.snapshot().cpu();
        final PpuSnapshot ppu = report.snapshot().ppu();
        final StringBuilder out = new StringBuilder();
        out.append("EmuRoxLab debug snapshot\n");
        out.append("ROM:      ").append(report.romName()).append('\n');
        out.append("Captured: ").append(HEADER_TIMESTAMP.format(report.capturedAt())).append("\n\n");

        out.append("--- CPU ---\n");
        out.append(String.format("PC: $%04X  A: $%02X  X: $%02X  Y: $%02X  SP: $%02X%n",
                cpu.pc(), cpu.a(), cpu.x(), cpu.y(), cpu.stackPointer()));
        out.append("Flags: ").append(flags(cpu)).append("  (NV-BDIZC, uppercase = set)\n");
        out.append("IRQ line: ").append(cpu.irqLineAsserted() ? "asserted" : "clear")
                .append("  NMI pending: ").append(yesNo(cpu.nmiPending())).append("\n\n");

        out.append("--- Next instructions ---\n");
        boolean first = true;
        for (final DisassembledInstruction instruction : report.upcomingInstructions()){
            out.append(first ? "> " : "  ").append(String.format("$%04X  %s%n", instruction.address(), instruction.formatted()));
            first = false;
        }
        out.append('\n');

        final PpuSnapshot.Timing timing = ppu.timing();
        final PpuSnapshot.Registers registers = ppu.registers();
        out.append("--- PPU ---\n");
        out.append(String.format("Scanline: %d  Dot: %d  VBlank: %s%n", timing.scanline(), timing.dot(), yesNo(timing.vblankFlag())));
        out.append(String.format("PPUCTRL: $%02X  PPUMASK: $%02X  OAMADDR: $%02X%n",
                registers.control(), registers.mask(), registers.oamAddress()));
        out.append(String.format("v: $%04X  t: $%04X  fine X: %d  write toggle: %d%n", registers.currentVramAddress(),
                registers.temporaryVramAddress(), registers.fineXScroll(), registers.writeToggle() ? 1 : 0));
        out.append("Sprite overflow: ").append(yesNo(ppu.sprites().spriteOverflow()))
                .append("  Sprite 0 hit: ").append(yesNo(ppu.sprites().spriteZeroHitFlag())).append("\n\n");

        out.append("--- Mapper ---\n");
        if (report.mapperState().isEmpty()){
            out.append("(fixed mapping, no bank registers)\n");
        }
        for (final Map.Entry<String, String> entry : report.mapperState().entrySet()){
            out.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        return out.toString();
    }

    private static String flags(final MOS6502Snapshot cpu){
        return new StringBuilder()
                .append(cpu.negative() ? 'N' : 'n')
                .append(cpu.signedOverflow() ? 'V' : 'v')
                .append('-')
                .append(cpu.breakFlag() ? 'B' : 'b')
                .append(cpu.decimal() ? 'D' : 'd')
                .append(cpu.interruptDisable() ? 'I' : 'i')
                .append(cpu.zero() ? 'Z' : 'z')
                .append(cpu.carry() ? 'C' : 'c')
                .toString();
    }

    private static String yesNo(final boolean value){
        return value ? "yes" : "no";
    }

    /** 16 bytes per row, each row prefixed with its address: {@code 0010: 00 01 ... 0F}. */
    static String hexDump(final int[] bytes){
        final StringBuilder out = new StringBuilder();
        for (int row = 0; row < bytes.length; row += HEX_BYTES_PER_ROW){
            out.append(String.format("%04X:", row));
            for (int i = row; i < Math.min(row + HEX_BYTES_PER_ROW, bytes.length); i++){
                out.append(String.format(" %02X", bytes[i]));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static void writePng(final Path path, final int[] rgb) throws IOException {
        final BufferedImage image = new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, rgb, 0, SCREEN_WIDTH);
        ImageIO.write(image, "png", path.toFile());
    }

    private static byte[] toBytes(final int[] values){
        final byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++){
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
