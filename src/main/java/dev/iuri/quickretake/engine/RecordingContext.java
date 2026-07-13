package dev.iuri.quickretake.engine;

import java.util.List;

import com.bitwig.extension.controller.api.ClipLauncherSlot;

/**
 * Immutable snapshot of what was being recorded at the moment the transport stopped.
 * Captured when the gesture detector arms, consumed when a retake fires.
 */
public record RecordingContext(
        Mode mode,
        double anchorBeats,
        double maxPlayPosBeats,
        List<SlotRef> slots,
        int recordPassCount,
        long stopTimeMs) {

    public enum Mode {
        ARRANGER,
        LAUNCHER,
        MIXED,
        NONE
    }

    public record SlotRef(int trackIndex, int slotIndex, ClipLauncherSlot slot) {
    }

    /**
     * True when the playhead crossed the take anchor, i.e. something was actually
     * committed to the timeline. False during count-in/pre-roll, where an undo
     * would eat an unrelated edit instead of the (nonexistent) take.
     */
    public boolean contentRecorded() {
        return maxPlayPosBeats > anchorBeats + 1e-3;
    }

    public double recordedBeats() {
        return Math.max(0, maxPlayPosBeats - anchorBeats);
    }
}
