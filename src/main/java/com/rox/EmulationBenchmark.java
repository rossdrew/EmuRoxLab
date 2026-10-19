package com.rox;

import com.rox.audio.AudioOutput;
import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import com.rox.clock.FPSClock;
import com.rox.time.SystemTimeSource;
import com.rox.time.ThreadSleeper;
import com.rox.save.LiveSaveFile;
import com.rox.save.SaveFileFormat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * Manual benchmark: how long the emulation thread takes per frame, with no window, no audio device and no
 * real-time pacing - just the NES ticking through the same {@link FPSClock} dispatch the game uses.
 * Optionally starts from a saved snapshot (e.g. an F12 debug snapshot's {@code .sav}) so a busy screen can
 * be measured the same way every time. Controllers are idle, so a snapshot plays on with no input.
 *
 * <pre>./gradlew runEmulationBenchmark --args="resource/rom/loz.nes [snapshot.sav] [frames] [trials]"</pre>
 */
public final class EmulationBenchmark {
    private static final double FRAME_BUDGET_MS = 1000.0 / 60;
    private static final int WARMUP_FRAMES = 600;

    private EmulationBenchmark(){
    }

    static void main(final String[] args) throws Exception {
        if (args.length < 1){
            System.err.println("Usage: EmulationBenchmark <rom.nes> [snapshot.sav or -] [frames, default 1800] [trials, default 3]");
            System.exit(1);
            return;
        }
        final Path rom = Path.of(args[0]);
        final Path snapshot = args.length >= 2 && !args[1].equals("-") ? Path.of(args[1]) : null;
        final int frames = args.length >= 3 ? Integer.parseInt(args[2]) : 1800;
        final int trials = args.length >= 4 ? Integer.parseInt(args[3]) : 3;

        System.out.printf(Locale.ROOT, "%s%s - %d warm-up + %d measured frames, %d trials%n", rom.getFileName(),
                snapshot == null ? "" : " from " + snapshot.getFileName(), WARMUP_FRAMES, frames, trials);
        for (int trial = 1; trial <= trials; trial++){
            final double[] ms = run(rom, snapshot, frames);
            report(trial, ms);
        }
    }

    /** Fresh NES per trial (same JVM, so later trials also show the JIT fully warmed). */
    private static double[] run(final Path rom, final Path snapshot, final int frames) throws Exception {
        final Cartridge cartridge = RomLoader.load(rom);
        final FrameCounter video = new FrameCounter();
        final FPSClock clock = new FPSClock(NES.intendedCpuHz(), 60, new SystemTimeSource(), new ThreadSleeper());
        final NES nes = new NES(SILENT, video, cartridge, clock);
        nes.cpu().reset();
        if (snapshot != null){
            final LiveSaveFile save = SaveFileFormat.readLiveSnapshot(snapshot)
                    .orElseThrow(() -> new IllegalArgumentException("Not a readable snapshot: " + snapshot));
            nes.restoreSnapshot(save.snapshot());
        }

        for (int i = 0; i < WARMUP_FRAMES; i++){
            runFrame(clock, video);
        }
        final double[] ms = new double[frames];
        for (int i = 0; i < frames; i++){
            final long start = System.nanoTime();
            runFrame(clock, video);
            ms[i] = (System.nanoTime() - start) / 1e6;
        }
        return ms;
    }

    private static void runFrame(final FPSClock clock, final FrameCounter video){
        final long target = video.frames + 1;
        while (video.frames < target){
            clock.tick();
        }
    }

    private static void report(final int trial, final double[] ms){
        final double[] sorted = ms.clone();
        Arrays.sort(sorted);
        final double mean = Arrays.stream(ms).average().orElse(0);
        final long overBudget = Arrays.stream(ms).filter(m -> m > FRAME_BUDGET_MS).count();
        System.out.printf(Locale.ROOT,
                "trial %d: mean %.2f ms  p50 %.2f  p99 %.2f  max %.2f  over 16.7ms %d  -> %.0f fps (%.0f%% of real time)%n",
                trial, mean, percentile(sorted, 0.50), percentile(sorted, 0.99), sorted[sorted.length - 1], overBudget,
                1000 / mean, 100 * FRAME_BUDGET_MS / mean);
    }

    private static double percentile(final double[] sorted, final double p){
        return sorted[Math.min(sorted.length - 1, (int) Math.floor(p * sorted.length))];
    }

    private static final class FrameCounter implements com.rox.video.VideoOutput {
        private long frames;

        @Override
        public void present(final int[] rgbFrame){
            frames++;
        }
    }

    private static final AudioOutput SILENT = new AudioOutput() {
        @Override
        public void start(){
        }

        @Override
        public void write(final double sample){
        }

        @Override
        public void stop(){
        }
    };
}
