package com.rox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Draws {@link TestRomOutcome}s as an SVG chart in the style of {@code resource/opcodes.svg}. */
final class ScoreboardSvg {
    static final int WIDTH = 1320;
    private static final int MARGIN = 20;
    private static final int LABEL_WIDTH = 210;
    private static final int TILE_WIDTH = 170;
    private static final int TILE_HEIGHT = 44;
    private static final int GAP = 6;
    private static final int HEADER_HEIGHT = 120;
    private static final int LEGEND_HEIGHT = 50;
    private static final int TILES_PER_ROW = (WIDTH - 2 * MARGIN - LABEL_WIDTH) / (TILE_WIDTH + GAP);
    static final Map<TestRomOutcome.Kind, String> COLOURS = Map.of(
            TestRomOutcome.Kind.PASSED, "#1f7a1f",
            TestRomOutcome.Kind.FAILED, "#8b1e1e",
            TestRomOutcome.Kind.NEVER_FINISHED, "#a86a00",
            TestRomOutcome.Kind.CRASHED, "#555555");

    private ScoreboardSvg(){
    }

    static String render(final List<TestRomOutcome> outcomes){
        final Map<String, List<TestRomOutcome>> suites = new LinkedHashMap<>();
        outcomes.forEach(o -> suites.computeIfAbsent(o.suite(), s -> new java.util.ArrayList<>()).add(o));

        final StringBuilder body = new StringBuilder();
        int y = HEADER_HEIGHT;
        for (final Map.Entry<String, List<TestRomOutcome>> suite : suites.entrySet()){
            final List<TestRomOutcome> roms = suite.getValue();
            final int rows = (roms.size() + TILES_PER_ROW - 1) / TILES_PER_ROW;
            body.append(text(MARGIN, y + TILE_HEIGHT / 2 + 6, 16, "#ffffff", "bold", suite.getKey()));
            for (int i = 0; i < roms.size(); i++){
                final int x = MARGIN + LABEL_WIDTH + (i % TILES_PER_ROW) * (TILE_WIDTH + GAP);
                body.append(tile(x, y + (i / TILES_PER_ROW) * (TILE_HEIGHT + GAP), roms.get(i)));
            }
            y += rows * (TILE_HEIGHT + GAP) + GAP * 2;
        }

        final long passed = outcomes.stream().filter(o -> o.kind() == TestRomOutcome.Kind.PASSED).count();
        final int height = y + LEGEND_HEIGHT;
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + WIDTH + "\" height=\"" + height
                + "\" viewBox=\"0 0 " + WIDTH + " " + height + "\" font-family=\"Helvetica, Arial, sans-serif\">\n"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#000000\"/>\n"
                + header(passed, outcomes.size())
                + body
                + legend(y + 10)
                + "</svg>\n";
    }

    private static String header(final long passed, final int total){
        final double fraction = total == 0 ? 0 : (double) passed / total;
        final int barWidth = WIDTH - 2 * MARGIN;
        return text(MARGIN, 40, 30, "#ffffff", "bold", "Test ROM Progress")
                + text(MARGIN, 66, 16, "#bbbbbb", "normal", "blargg's test ROMs (src/test/resources/roms) as EmuRoxLab runs them today")
                + text(WIDTH - MARGIN, 40, 22, "#ffffff", "bold", passed + " / " + total + " passing", "end")
                + "<rect x=\"" + MARGIN + "\" y=\"80\" width=\"" + barWidth + "\" height=\"18\" rx=\"4\" fill=\"#333333\"/>\n"
                + "<rect x=\"" + MARGIN + "\" y=\"80\" width=\"" + Math.round(barWidth * fraction) + "\" height=\"18\" rx=\"4\" fill=\""
                + COLOURS.get(TestRomOutcome.Kind.PASSED) + "\"/>\n"
                + text(WIDTH / 2, 94, 13, "#ffffff", "bold", String.format("%.0f%%", fraction * 100), "middle");
    }

    private static String tile(final int x, final int y, final TestRomOutcome outcome){
        return "<g><title>" + escape(outcome.suite() + "/" + outcome.rom() + ": " + outcome.message().strip()) + "</title>"
                + "<rect x=\"" + x + "\" y=\"" + y + "\" width=\"" + TILE_WIDTH + "\" height=\"" + TILE_HEIGHT + "\" rx=\"4\" fill=\""
                + COLOURS.get(outcome.kind()) + "\"/>"
                + text(x + TILE_WIDTH / 2, y + 18, 12, "#ffffff", "bold", outcome.rom(), "middle")
                + text(x + TILE_WIDTH / 2, y + 35, 11, "#eeeeee", "normal", shortResult(outcome), "middle")
                + "</g>\n";
    }

    /** What fits under a tile's name: "Passed", "Failed #4", "Failed", "Never finishes", "Crashed". */
    static String shortResult(final TestRomOutcome outcome){
        return switch (outcome.kind()){
            case PASSED -> "Passed";
            case FAILED -> outcome.status() > 1 ? "Failed #" + outcome.status() : "Failed";
            case NEVER_FINISHED -> "Never finishes";
            case CRASHED -> "Crashed";
        };
    }

    private static String legend(final int y){
        final StringBuilder legend = new StringBuilder();
        final String[][] entries = {{"PASSED", "Passed"}, {"FAILED", "Failed (#code)"},
                {"NEVER_FINISHED", "Never finishes"}, {"CRASHED", "Crashed (emulator error)"}};
        int x = MARGIN;
        for (final String[] entry : entries){
            legend.append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"18\" height=\"18\" rx=\"3\" fill=\"")
                    .append(COLOURS.get(TestRomOutcome.Kind.valueOf(entry[0]))).append("\"/>")
                    .append(text(x + 26, y + 14, 14, "#dddddd", "normal", entry[1]));
            x += 260;
        }
        return legend.toString();
    }

    private static String text(final int x, final int y, final int size, final String colour, final String weight, final String value){
        return text(x, y, size, colour, weight, value, "start");
    }

    private static String text(final int x, final int y, final int size, final String colour, final String weight,
                               final String value, final String anchor){
        return "<text x=\"" + x + "\" y=\"" + y + "\" font-size=\"" + size + "\" fill=\"" + colour + "\" font-weight=\"" + weight
                + "\" text-anchor=\"" + anchor + "\">" + escape(value) + "</text>\n";
    }

    /** XML-safe: markup characters escaped, and control characters (illegal in XML 1.0) dropped bar newline and tab. */
    static String escape(final String value){
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }
}
