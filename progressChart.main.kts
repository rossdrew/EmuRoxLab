#!/usr/bin/env kotlin

import java.io.File
        import kotlin.math.roundToInt

        data class OfficialOpcode(
    val hex: Int,
    val mnemonic: String
)

data class ImplementedOpcode(
    val hex: Int,
    val enumName: String
)

val official: Map<Int, OfficialOpcode> = listOf(
    // 0x0_
    0x00 to "BRK_IMP",
    0x01 to "ORA_IND_X",
    0x05 to "ORA_Z",
    0x06 to "ASL_Z",
    0x08 to "PHP_IMP",
    0x09 to "ORA_I",
    0x0A to "ASL_A",
    0x0D to "ORA_ABS",
    0x0E to "ASL_ABS",

    // 0x1_
    0x10 to "BPL_REL",
    0x11 to "ORA_IND_Y",
    0x15 to "ORA_Z_X",
    0x16 to "ASL_Z_X",
    0x18 to "CLC_IMP",
    0x19 to "ORA_ABS_Y",
    0x1D to "ORA_ABS_X",
    0x1E to "ASL_ABS_X",

    // 0x2_
    0x20 to "JSR_ABS",
    0x21 to "AND_IND_X",
    0x24 to "BIT_Z",
    0x25 to "AND_Z",
    0x26 to "ROL_Z",
    0x28 to "PLP_IMP",
    0x29 to "AND_I",
    0x2A to "ROL_A",
    0x2C to "BIT_ABS",
    0x2D to "AND_ABS",
    0x2E to "ROL_ABS",

    // 0x3_
    0x30 to "BMI_REL",
    0x31 to "AND_IND_Y",
    0x35 to "AND_Z_X",
    0x36 to "ROL_Z_X",
    0x38 to "SEC_IMP",
    0x39 to "AND_ABS_Y",
    0x3D to "AND_ABS_X",
    0x3E to "ROL_ABS_X",

    // 0x4_
    0x40 to "RTI_IMP",
    0x41 to "EOR_IND_X",
    0x45 to "EOR_Z",
    0x46 to "LSR_Z",
    0x48 to "PHA_IMP",
    0x49 to "EOR_I",
    0x4A to "LSR_A",
    0x4C to "JMP_ABS",
    0x4D to "EOR_ABS",
    0x4E to "LSR_ABS",

    // 0x5_
    0x50 to "BVC_REL",
    0x51 to "EOR_IND_Y",
    0x55 to "EOR_Z_X",
    0x56 to "LSR_Z_X",
    0x58 to "CLI_IMP",
    0x59 to "EOR_ABS_Y",
    0x5D to "EOR_ABS_X",
    0x5E to "LSR_ABS_X",

    // 0x6_
    0x60 to "RTS_IMP",
    0x61 to "ADC_IND_X",
    0x65 to "ADC_Z",
    0x66 to "ROR_Z",
    0x68 to "PLA_IMP",
    0x69 to "ADC_I",
    0x6A to "ROR_A",
    0x6C to "JMP_IND",
    0x6D to "ADC_ABS",
    0x6E to "ROR_ABS",

    // 0x7_
    0x70 to "BVS_REL",
    0x71 to "ADC_IND_Y",
    0x75 to "ADC_Z_X",
    0x76 to "ROR_Z_X",
    0x78 to "SEI_IMP",
    0x79 to "ADC_ABS_Y",
    0x7D to "ADC_ABS_X",
    0x7E to "ROR_ABS_X",

    // 0x8_
    0x81 to "STA_IND_X",
    0x84 to "STY_Z",
    0x85 to "STA_Z",
    0x86 to "STX_Z",
    0x88 to "DEY_IMP",
    0x8A to "TXA_IMP",
    0x8C to "STY_ABS",
    0x8D to "STA_ABS",
    0x8E to "STX_ABS",

    // 0x9_
    0x90 to "BCC_REL",
    0x91 to "STA_IND_Y",
    0x94 to "STY_Z_X",
    0x95 to "STA_Z_X",
    0x96 to "STX_Z_Y",
    0x98 to "TYA_IMP",
    0x99 to "STA_ABS_Y",
    0x9A to "TXS_IMP",
    0x9D to "STA_ABS_X",

    // 0xA_
    0xA0 to "LDY_I",
    0xA1 to "LDA_IND_X",
    0xA2 to "LDX_I",
    0xA4 to "LDY_Z",
    0xA5 to "LDA_Z",
    0xA6 to "LDX_Z",
    0xA8 to "TAY_IMP",
    0xA9 to "LDA_I",
    0xAA to "TAX_IMP",
    0xAC to "LDY_ABS",
    0xAD to "LDA_ABS",
    0xAE to "LDX_ABS",

    // 0xB_
    0xB0 to "BCS_REL",
    0xB1 to "LDA_IND_Y",
    0xB4 to "LDY_Z_X",
    0xB5 to "LDA_Z_X",
    0xB6 to "LDX_Z_Y",
    0xB8 to "CLV_IMP",
    0xB9 to "LDA_ABS_Y",
    0xBA to "TSX_IMP",
    0xBC to "LDY_ABS_X",
    0xBD to "LDA_ABS_X",
    0xBE to "LDX_ABS_Y",

    // 0xC_
    0xC0 to "CPY_I",
    0xC1 to "CMP_IND_X",
    0xC4 to "CPY_Z",
    0xC5 to "CMP_Z",
    0xC6 to "DEC_Z",
    0xC8 to "INY_IMP",
    0xC9 to "CMP_I",
    0xCA to "DEX_IMP",
    0xCC to "CPY_ABS",
    0xCD to "CMP_ABS",
    0xCE to "DEC_ABS",

    // 0xD_
    0xD0 to "BNE_REL",
    0xD1 to "CMP_IND_Y",
    0xD5 to "CMP_Z_X",
    0xD6 to "DEC_Z_X",
    0xD8 to "CLD_IMP",
    0xD9 to "CMP_ABS_Y",
    0xDD to "CMP_ABS_X",
    0xDE to "DEC_ABS_X",

    // 0xE_
    0xE0 to "CPX_I",
    0xE1 to "SBC_IND_X",
    0xE4 to "CPX_Z",
    0xE5 to "SBC_Z",
    0xE6 to "INC_Z",
    0xE8 to "INX_IMP",
    0xE9 to "SBC_I",
    0xEA to "NOP_IMP",
    0xEC to "CPX_ABS",
    0xED to "SBC_ABS",
    0xEE to "INC_ABS",

    // 0xF_
    0xF0 to "BEQ_REL",
    0xF1 to "SBC_IND_Y",
    0xF5 to "SBC_Z_X",
    0xF6 to "INC_Z_X",
    0xF8 to "SED_IMP",
    0xF9 to "SBC_ABS_Y",
    0xFD to "SBC_ABS_X",
    0xFE to "INC_ABS_X"
).associate { (hex, mnemonic) ->
    hex to OfficialOpcode(hex, mnemonic)
}

fun String.xmlEscape(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

fun hex2(value: Int): String =
    value.toString(16).uppercase().padStart(2, '0')

fun parseImplementedOpcodes(file: File): Map<Int, ImplementedOpcode> {
    val text = file.readText()

    // Matches enum entries like:
    // ADC_I(0x69, clockTicks(
    //
    // It deliberately anchors to uppercase enum-style names to avoid helper methods.
    val regex = Regex("""\b([A-Z][A-Z0-9_]*)\s*\(\s*0x([0-9A-Fa-f]{2})\s*,""")

    return regex.findAll(text)
        .map { match ->
            val enumName = match.groupValues[1]
            val hex = match.groupValues[2].toInt(16)
            hex to ImplementedOpcode(hex, enumName)
        }
        .toMap()
}

fun text(
    x: Int,
    y: Int,
    value: String,
    size: Int,
    weight: String = "400",
    fill: String = "#ffffff",
    anchor: String = "middle",
    style: String = ""
): String =
    """<text x="$x" y="$y" text-anchor="$anchor" font-size="$size" font-weight="$weight" fill="$fill" $style>${value.xmlEscape()}</text>"""

fun rect(
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    fill: String,
    stroke: String = "#9ca3af",
    strokeWidth: Int = 1,
    rx: Int = 0
): String =
    """<rect x="$x" y="$y" width="$w" height="$h" rx="$rx" fill="$fill" stroke="$stroke" stroke-width="$strokeWidth"/>"""

fun generateSvg(implemented: Map<Int, ImplementedOpcode>): String {
    val legalCount = official.size
    val implementedLegalCount = implemented.keys.count { it in official }
    val remainingLegalCount = legalCount - implementedLegalCount
    val unusedCount = 256 - legalCount
    val percent = implementedLegalCount.toDouble() / legalCount.toDouble() * 100.0
    val percentText = "${(percent * 10.0).roundToInt() / 10.0}%"

    val cellW = 84
    val cellH = 54
    val rowHeaderW = 72
    val headerH = 42
    val gridX = 24
    val gridY = 150
    val gridW = rowHeaderW + 16 * cellW
    val gridH = headerH + 16 * cellH

    val width = gridX * 2 + gridW
    val height = gridY + gridH + 120

    val implementedGreen = "#15803d"
    val legalGrey = "#4b5563"
    val unusedBlack = "#020617"
    val headerFill = "#111827"
    val pageBg = "#020617"
    val border = "#d1d5db"

    val sb = StringBuilder()

    sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">""")
    sb.appendLine("""<style>
        text {
            font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
            dominant-baseline: middle;
        }
        .small-note {
            font-style: italic;
        }
    </style>""".trimIndent())

    sb.appendLine(rect(0, 0, width, height, pageBg, pageBg))

    // Title
    sb.appendLine(text(24, 34, "MOS6502 Opcode Implementation Progress", 32, "800", "#ffffff", "start"))
    sb.appendLine(text(24, 70, "Based on current EmuRoxLab MOS6502OpCode.java", 20, "400", "#e5e7eb", "start", """class="small-note""""))
    sb.appendLine(text(24, 100, "Overlaying current code progress on the official NMOS 6502 opcode map", 16, "400", "#cbd5e1", "start", """class="small-note""""))

    // Summary boxes
    val summaryY = 18
    val boxH = 42
    val boxW = 210
    val gap = 14
    val startX = width - 24 - (boxW * 3 + gap * 2)

    sb.appendLine(rect(startX, summaryY, boxW, boxH, "#052e16", "#22c55e", 2, 6))
    sb.appendLine(text(startX + boxW / 2, summaryY + boxH / 2, "$implementedLegalCount / $legalCount implemented", 18, "800"))

    sb.appendLine(rect(startX + boxW + gap, summaryY, boxW, boxH, "#082f49", "#06b6d4", 2, 6))
    sb.appendLine(text(startX + boxW + gap + boxW / 2, summaryY + boxH / 2, "$percentText complete", 18, "800"))

    sb.appendLine(rect(startX + (boxW + gap) * 2, summaryY, boxW, boxH, "#422006", "#f59e0b", 2, 6))
    sb.appendLine(text(startX + (boxW + gap) * 2 + boxW / 2, summaryY + boxH / 2, "$remainingLegalCount remaining", 18, "800"))

    // Progress bar
    val barX = startX
    val barY = summaryY + boxH + 14
    val barW = boxW * 3 + gap * 2
    val barH = 28
    val fillW = (barW * percent / 100.0).roundToInt()

    sb.appendLine(rect(barX, barY, barW, barH, "#374151", "#4b5563", 1, 6))
    sb.appendLine("""<rect x="$barX" y="$barY" width="$fillW" height="$barH" rx="6" fill="#22c55e"/>""")
    sb.appendLine(text(barX + fillW / 2, barY + barH / 2, percentText, 16, "800", "#ffffff"))

    val unusedBadgeW = 360
    val unusedBadgeX = barX + (barW - unusedBadgeW) / 2
    val unusedBadgeY = barY + barH + 14
    sb.appendLine(rect(unusedBadgeX, unusedBadgeY, unusedBadgeW, 30, "#020617", "#d1d5db", 1, 5))
    sb.appendLine(text(unusedBadgeX + unusedBadgeW / 2, unusedBadgeY + 15, "$unusedCount unused / unofficial opcode slots", 16, "700"))

    // Table headers
    sb.appendLine(rect(gridX, gridY, rowHeaderW, headerH, headerFill, border))
    sb.appendLine(text(gridX + rowHeaderW / 2, gridY + headerH / 2, "Hi\\Lo", 16, "700"))

    for (col in 0..15) {
        val x = gridX + rowHeaderW + col * cellW
        sb.appendLine(rect(x, gridY, cellW, headerH, headerFill, border))
        sb.appendLine(text(x + cellW / 2, gridY + headerH / 2, col.toString(16).uppercase(), 20, "800"))
    }

    // Rows and cells
    for (row in 0..15) {
        val y = gridY + headerH + row * cellH

        sb.appendLine(rect(gridX, y, rowHeaderW, cellH, headerFill, border))
        sb.appendLine(text(gridX + rowHeaderW / 2, y + cellH / 2, "0x${row.toString(16).uppercase()}_", 18, "800"))

        for (col in 0..15) {
            val opcode = row * 16 + col
            val x = gridX + rowHeaderW + col * cellW

            val officialOpcode = official[opcode]
            val implementedOpcode = implemented[opcode]

            val isLegal = officialOpcode != null
            val isImplemented = implementedOpcode != null
            val isImplementedLegal = isImplemented && isLegal
            val isMismatch = isImplementedLegal && implementedOpcode!!.enumName != officialOpcode!!.mnemonic

            val fill = when {
                !isLegal -> unusedBlack
                isImplementedLegal -> implementedGreen
                else -> legalGrey
            }

            val stroke = if (isMismatch) "#facc15" else border
            val strokeWidth = if (isMismatch) 3 else 1

            sb.appendLine(rect(x, y, cellW, cellH, fill, stroke, strokeWidth))

            sb.appendLine(text(x + cellW / 2, y + 15, hex2(opcode), 15, "800"))

            val label = when {
                officialOpcode != null -> officialOpcode.mnemonic
                else -> "unused"
            }

            val labelSize = if (label.length > 10) 10 else 12
            sb.appendLine(text(x + cellW / 2, y + 35, label, labelSize, "700", if (isLegal) "#ffffff" else "#cbd5e1"))

            if (isMismatch) {
                sb.appendLine(text(x + cellW - 10, y + 10, "!", 13, "900", "#facc15"))
            }
        }
    }

    // Legend
    val legendY = gridY + gridH + 52
    val legendX = width / 2 - 440

    fun legendItem(x: Int, fill: String, stroke: String, title: String, sub: String) {
        sb.appendLine(rect(x, legendY - 22, 42, 42, fill, stroke, 1, 0))
        sb.appendLine(text(x + 58, legendY - 4, "= $title", 18, "700", "#ffffff", "start"))
        sb.appendLine(text(x + 58, legendY + 22, sub, 14, "400", "#d1d5db", "start", """class="small-note""""))
    }

    legendItem(legendX, implementedGreen, border, "Implemented", "(In MOS6502OpCode.java)")
    legendItem(legendX + 330, legalGrey, border, "Legal but not implemented", "(Official NMOS 6502 opcode)")
    legendItem(legendX + 720, unusedBlack, border, "Unused / unofficial", "(Not used on NMOS 6502)")

    // Mismatch note, only if needed.
    val mismatches = implemented.values
        .filter { official[it.hex] != null && official[it.hex]!!.mnemonic != it.enumName }
        .sortedBy { it.hex }

    if (mismatches.isNotEmpty()) {
        val note = mismatches.joinToString(", ") {
            "${hex2(it.hex)} code=${it.enumName}, official=${official[it.hex]!!.mnemonic}"
        }

        sb.appendLine(text(24, height - 22, "Yellow border = implemented enum name differs from official opcode: $note", 13, "500", "#facc15", "start"))
    }

    sb.appendLine("</svg>")

    return sb.toString()
}

if (args.size != 2) {
    error(
        """
        Usage:
          kotlinc -script src/main/kotlin/progressChart.kts <MOS6502OpCode.java> <output.svg>

        Example:
          kotlinc -script src/main/kotlin/progressChart.kts \
            src/main/java/com/rox/cpu/mos6502/MOS6502OpCode.java \
            resource/opcode-progress.svg
        """.trimIndent()
    )
}

val input = File(args[0])
val output = File(args[1])

require(input.exists()) {
    "Input file does not exist: ${input.absolutePath}"
}

output.parentFile?.mkdirs()

val implemented = parseImplementedOpcodes(input)
val svg = generateSvg(implemented)

output.writeText(svg)

println("Generated ${output.path}")
println("Implemented enum entries found: ${implemented.size}")
println("Official legal opcodes: ${official.size}")
println("Implemented legal opcodes: ${implemented.keys.count { it in official }}")
println("Unused / unofficial slots: ${256 - official.size}")

val unofficialImplemented = implemented.keys
    .filter { it !in official }
    .sorted()

if (unofficialImplemented.isNotEmpty()) {
    println("WARNING: Implemented opcode IDs not in official NMOS 6502 map:")
    unofficialImplemented.forEach {
        println("  ${hex2(it)} ${implemented[it]!!.enumName}")
    }
}

val mismatches = implemented.values
    .filter { official[it.hex] != null && official[it.hex]!!.mnemonic != it.enumName }
    .sortedBy { it.hex }

if (mismatches.isNotEmpty()) {
    println("WARNING: Implemented enum names differ from official opcode labels:")
    mismatches.forEach {
        println("  ${hex2(it.hex)} code=${it.enumName}, official=${official[it.hex]!!.mnemonic}")
    }
}