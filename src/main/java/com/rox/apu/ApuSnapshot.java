package com.rox.apu;

/**
 * An {@link APU}'s complete state, composed of each unit's own {@code Snapshot} record. Plain values all
 * the way down - no arrays - so ordinary record equality is a full deep comparison.
 */
public record ApuSnapshot(FrameSequencer.Snapshot frameSequencer, PulseChannel.Snapshot pulse1,
                          PulseChannel.Snapshot pulse2, TriangleChannel.Snapshot triangle,
                          NoiseChannel.Snapshot noise, DMCChannel.Snapshot dmc) {
}
