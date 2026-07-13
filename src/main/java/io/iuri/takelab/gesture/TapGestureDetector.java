package io.iuri.takelab.gesture;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Transport;

import io.iuri.takelab.engine.RecordingContext;
import io.iuri.takelab.engine.RecordingContext.Mode;
import io.iuri.takelab.engine.RecordingMonitor;
import io.iuri.takelab.settings.RetakeSettings;

/**
 * Infers the "double-tap spacebar" retake gesture from transport state, since
 * extensions cannot see the computer keyboard: a stop while recording ARMs a
 * short window; a play inside it fires the retake (double-tap mode) or waits
 * for one more stop (strict triple-tap mode).
 *
 * Timers cannot be cancelled in this API, so every armed window carries a
 * generation token; stale timers are ignored.
 */
public class TapGestureDetector {

    private enum State {
        IDLE,
        ARMED,
        CONFIRM_WAIT,
        EXECUTING
    }

    /** isPlaying/isRecording callback order on stop is unspecified; treat
     * "recording ended this recently" as still-recording. */
    private static final long REC_TOLERANCE_MS = 200;

    private final ControllerHost host;
    private final Transport transport;
    private final RetakeSettings settings;
    private final RecordingMonitor monitor;
    private final BiConsumer<RecordingContext, Consumer<Boolean>> executor;
    private final BooleanSupplier gesturesSuppressed;
    private final Runnable lateUndoAction;

    private State state = State.IDLE;
    private long generation;
    private RecordingContext ctx;
    private Boolean savedRecEnable;

    private long lastIdleToggleAtMs = Long.MIN_VALUE;
    private int idleToggleCount;

    public TapGestureDetector(ControllerHost host, Transport transport, RetakeSettings settings,
            RecordingMonitor monitor, BiConsumer<RecordingContext, Consumer<Boolean>> executor,
            BooleanSupplier gesturesSuppressed, Runnable lateUndoAction) {
        this.host = host;
        this.transport = transport;
        this.settings = settings;
        this.monitor = monitor;
        this.executor = executor;
        this.gesturesSuppressed = gesturesSuppressed;
        this.lateUndoAction = lateUndoAction;

        transport.isPlaying().addValueObserver(this::onPlayingChanged);
    }

    private void onPlayingChanged(boolean playing) {
        if (gesturesSuppressed.getAsBoolean()) {
            idleToggleCount = 0;
            return;
        }
        switch (state) {
            case IDLE -> {
                if (!playing && monitor.wasRecordingRecently(REC_TOLERANCE_MS)) {
                    idleToggleCount = 0;
                    arm();
                } else {
                    countIdleToggle();
                }
            }
            case ARMED -> {
                if (playing) {
                    if (settings.gestureIsTriple()) {
                        state = State.CONFIRM_WAIT;
                        host.println("[TL] CONFIRM_WAIT gen=" + generation);
                        scheduleWindow();
                    } else {
                        fire();
                    }
                }
            }
            case CONFIRM_WAIT -> {
                if (!playing) {
                    fire();
                }
            }
            case EXECUTING -> {
                // The retake sequence itself toggles the transport; ignore.
            }
        }
    }

    /**
     * Late undo: the user missed the retake window and the flubbed take is
     * committed. Three rapid play/stop toggles outside any recording context
     * (each within the tap window) fire a plain undo. Off by default.
     */
    private void countIdleToggle() {
        if (!settings.lateUndo()) {
            return;
        }
        final long now = System.currentTimeMillis();
        idleToggleCount = now - lastIdleToggleAtMs <= settings.tapWindowMs() ? idleToggleCount + 1 : 1;
        lastIdleToggleAtMs = now;
        if (idleToggleCount >= 3) {
            idleToggleCount = 0;
            host.println("[TL] late undo gesture");
            lateUndoAction.run();
        }
    }

    private void arm() {
        final RecordingContext snapshot = monitor.snapshot();
        if (snapshot.mode() == Mode.NONE) {
            return;
        }
        if (snapshot.mode() == Mode.ARRANGER && !settings.arrangerEnabled()) {
            return;
        }
        if (snapshot.mode() == Mode.LAUNCHER && !settings.launcherEnabled()) {
            return;
        }
        ctx = snapshot;
        state = State.ARMED;
        if (settings.suppressRearm() && ctx.mode() == Mode.ARRANGER) {
            savedRecEnable = transport.isArrangerRecordEnabled().get();
            transport.isArrangerRecordEnabled().set(false);
        }
        host.println("[TL] ARMED gen=" + (generation + 1) + " mode=" + ctx.mode()
                + " anchor=" + ctx.anchorBeats());
        scheduleWindow();
    }

    private void scheduleWindow() {
        final long gen = ++generation;
        host.scheduleTask(() -> onWindowExpired(gen), settings.tapWindowMs());
    }

    private void onWindowExpired(long gen) {
        if (gen != generation) {
            return;
        }
        if (state == State.ARMED || state == State.CONFIRM_WAIT) {
            host.println("[TL] window expired, back to IDLE");
            restoreRecEnable();
            state = State.IDLE;
        }
    }

    private void fire() {
        generation++; // invalidate pending window timers

        if (ctx.mode() == Mode.MIXED) {
            host.showPopupNotification(
                    "TakeLab: simultaneous arranger + launcher recording not supported");
            restoreRecEnable();
            state = State.IDLE;
            return;
        }

        final double minBeats = settings.minRecordedBeats();
        if (minBeats > 0 && ctx.recordedBeats() < minBeats) {
            host.println("[TL] below min recorded length, ignoring gesture");
            restoreRecEnable();
            state = State.IDLE;
            return;
        }

        // The arranger sequence re-enables record itself; drop the saved value.
        savedRecEnable = null;
        state = State.EXECUTING;
        host.println("[TL] EXECUTING mode=" + ctx.mode());
        executor.accept(ctx, this::onSequenceDone);
    }

    private void onSequenceDone(boolean success) {
        state = State.IDLE;
        if (!success) {
            host.showPopupNotification("TakeLab: retake failed (recording did not restart)");
            host.println("[TL] sequence FAILED");
        } else {
            host.println("[TL] sequence done");
        }
    }

    /** Footswitch/button trigger: one press = stop (if rolling) + retake. */
    public void onExternalTrigger() {
        switch (state) {
            case EXECUTING -> {
                // Already retaking; ignore.
            }
            case ARMED, CONFIRM_WAIT -> fire();
            case IDLE -> {
                if (monitor.isArrangerRecordingActive() || monitor.anySlotRecordingOrQueued()
                        || monitor.wasRecordingRecently(REC_TOLERANCE_MS)) {
                    ctx = monitor.snapshot();
                    if (ctx.mode() == Mode.NONE) {
                        return;
                    }
                    fire();
                }
            }
        }
    }

    private void restoreRecEnable() {
        if (savedRecEnable != null) {
            transport.isArrangerRecordEnabled().set(savedRecEnable);
            savedRecEnable = null;
        }
    }
}
