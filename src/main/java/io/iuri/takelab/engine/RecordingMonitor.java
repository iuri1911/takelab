package io.iuri.takelab.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;

import io.iuri.takelab.engine.RecordingContext.Mode;
import io.iuri.takelab.engine.RecordingContext.SlotRef;

/**
 * Continuously tracks what is being recorded: the arranger record pass (take
 * anchor + how far the playhead got) and every clip-launcher slot currently or
 * recently recording, across a fixed 32x32 track/scene window.
 *
 * All callbacks arrive on the controller thread; no synchronization needed.
 */
public class RecordingMonitor {

    /** Slots that stopped recording within this window still count for a snapshot
     * (the isPlaying/isRecording callback order on stop is unspecified). */
    private static final long RECENT_SLOT_MS = 1000;
    private static final long RECENT_ARRANGER_MS = 1000;

    private final Transport transport;
    private final TrackBank trackBank;

    private boolean playing;
    private boolean arrangerRecordEnabled;
    private boolean arrangerRecActive;
    private long arrangerRecStoppedAtMs = Long.MIN_VALUE;
    private int recordPassCount;
    private double anchorBeats;
    private double maxPlayPosBeats = Double.NEGATIVE_INFINITY;

    private final Map<Long, SlotRef> recordingSlots = new LinkedHashMap<>();
    private final Map<Long, SlotRef> queuedSlots = new LinkedHashMap<>();
    private final Map<Long, RecentSlot> recentSlots = new LinkedHashMap<>();

    private record RecentSlot(SlotRef ref, long stoppedAtMs) {
    }

    public RecordingMonitor(Transport transport, TrackBank trackBank) {
        this.transport = transport;
        this.trackBank = trackBank;

        transport.playStartPosition().markInterested();

        // "Actively recording an arranger pass" has no direct API value (the old
        // addIsRecordingObserver is deprecated and only mirrored the record toggle,
        // and Bitwig 6 throws on deprecated calls) — derive it: playing AND record enabled.
        transport.isPlaying().addValueObserver(playing -> {
            this.playing = playing;
            recomputeArrangerRecording();
        });
        transport.isArrangerRecordEnabled().addValueObserver(enabled -> {
            this.arrangerRecordEnabled = enabled;
            recomputeArrangerRecording();
        });

        transport.playPosition().addValueObserver(pos -> {
            if (arrangerRecActive) {
                maxPlayPosBeats = Math.max(maxPlayPosBeats, pos);
            }
        });

        final int tracks = trackBank.getSizeOfBank();
        for (int t = 0; t < tracks; t++) {
            final Track track = trackBank.getItemAt(t);
            final ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
            final int scenes = slotBank.getSizeOfBank();
            for (int s = 0; s < scenes; s++) {
                final ClipLauncherSlot slot = slotBank.getItemAt(s);
                final long key = key(t, s);
                final SlotRef ref = new SlotRef(t, s, slot);
                slot.hasContent().markInterested();
                slot.isRecording().addValueObserver(rec -> {
                    if (rec) {
                        recordingSlots.put(key, ref);
                        recentSlots.remove(key);
                    } else if (recordingSlots.remove(key) != null) {
                        recentSlots.put(key, new RecentSlot(ref, System.currentTimeMillis()));
                    }
                });
                slot.isRecordingQueued().addValueObserver(queued -> {
                    if (queued) {
                        queuedSlots.put(key, ref);
                    } else {
                        queuedSlots.remove(key);
                    }
                });
            }
        }
    }

    private void recomputeArrangerRecording() {
        final boolean active = playing && arrangerRecordEnabled;
        if (active == arrangerRecActive) {
            return;
        }
        arrangerRecActive = active;
        if (active) {
            recordPassCount++;
            anchorBeats = transport.playStartPosition().get();
            maxPlayPosBeats = anchorBeats;
        } else {
            arrangerRecStoppedAtMs = System.currentTimeMillis();
        }
    }

    private static long key(int track, int scene) {
        return ((long) track << 32) | scene;
    }

    public boolean isArrangerRecordingActive() {
        return arrangerRecActive;
    }

    public int recordPassCount() {
        return recordPassCount;
    }

    /** Content committed in the pass currently tracked (used for the junk-pass guard). */
    public boolean contentRecordedInCurrentPass() {
        return maxPlayPosBeats > anchorBeats + 1e-3;
    }

    public boolean anySlotRecordingOrQueued() {
        return !recordingSlots.isEmpty() || !queuedSlots.isEmpty();
    }

    /** True if anything (arranger pass or launcher slot) is recording now or stopped within toleranceMs. */
    public boolean wasRecordingRecently(long toleranceMs) {
        final long now = System.currentTimeMillis();
        if (arrangerRecActive || now - arrangerRecStoppedAtMs < toleranceMs) {
            return true;
        }
        if (!recordingSlots.isEmpty()) {
            return true;
        }
        for (RecentSlot recent : recentSlots.values()) {
            if (now - recent.stoppedAtMs() < toleranceMs) {
                return true;
            }
        }
        return false;
    }

    /** Snapshot taken at the moment the transport stops (gesture arming). */
    public RecordingContext snapshot() {
        final long now = System.currentTimeMillis();

        final List<SlotRef> slots = new ArrayList<>(recordingSlots.values());
        for (RecentSlot recent : recentSlots.values()) {
            if (now - recent.stoppedAtMs() < RECENT_SLOT_MS && !slots.contains(recent.ref())) {
                slots.add(recent.ref());
            }
        }

        final boolean arrangerTook = arrangerRecActive || now - arrangerRecStoppedAtMs < RECENT_ARRANGER_MS;

        final Mode mode;
        if (arrangerTook && slots.isEmpty()) {
            mode = Mode.ARRANGER;
        } else if (!arrangerTook && !slots.isEmpty()) {
            mode = Mode.LAUNCHER;
        } else if (arrangerTook) {
            mode = Mode.MIXED;
        } else {
            mode = Mode.NONE;
        }

        return new RecordingContext(mode, anchorBeats, maxPlayPosBeats, List.copyOf(slots), recordPassCount, now);
    }

    /** Track whose slots we may search for an empty take slot (keep-takes mode). */
    public Track trackAt(int index) {
        return trackBank.getItemAt(index);
    }
}
