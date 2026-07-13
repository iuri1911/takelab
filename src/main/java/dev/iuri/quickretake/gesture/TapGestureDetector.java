package dev.iuri.quickretake.gesture;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Transport;

import dev.iuri.quickretake.engine.RecordingContext;
import dev.iuri.quickretake.engine.RecordingContext.Mode;
import dev.iuri.quickretake.engine.RecordingMonitor;
import dev.iuri.quickretake.settings.RetakeSettings;

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

    private State state = State.IDLE;
    private long generation;
    private RecordingContext ctx;
    private Boolean savedRecEnable;

    public TapGestureDetector(ControllerHost host, Transport transport, RetakeSettings settings,
            RecordingMonitor monitor, BiConsumer<RecordingContext, Consumer<Boolean>> executor) {
        this.host = host;
        this.transport = transport;
        this.settings = settings;
        this.monitor = monitor;
        this.executor = executor;

        transport.isPlaying().addValueObserver(this::onPlayingChanged);
    }

    private void onPlayingChanged(boolean playing) {
        switch (state) {
            case IDLE -> {
                if (!playing && monitor.wasRecordingRecently(REC_TOLERANCE_MS)) {
                    arm();
                }
            }
            case ARMED -> {
                if (playing) {
                    if (settings.gestureIsTriple()) {
                        state = State.CONFIRM_WAIT;
                        host.println("[QR] CONFIRM_WAIT gen=" + generation);
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
        host.println("[QR] ARMED gen=" + (generation + 1) + " mode=" + ctx.mode()
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
            host.println("[QR] window expired, back to IDLE");
            restoreRecEnable();
            state = State.IDLE;
        }
    }

    private void fire() {
        generation++; // invalidate pending window timers

        if (ctx.mode() == Mode.MIXED) {
            host.showPopupNotification(
                    "QuickRetake: simultaneous arranger + launcher recording not supported");
            restoreRecEnable();
            state = State.IDLE;
            return;
        }

        final double minBeats = settings.minRecordedBeats();
        if (minBeats > 0 && ctx.recordedBeats() < minBeats) {
            host.println("[QR] below min recorded length, ignoring gesture");
            restoreRecEnable();
            state = State.IDLE;
            return;
        }

        // The arranger sequence re-enables record itself; drop the saved value.
        savedRecEnable = null;
        state = State.EXECUTING;
        host.println("[QR] EXECUTING mode=" + ctx.mode());
        executor.accept(ctx, this::onSequenceDone);
    }

    private void onSequenceDone(boolean success) {
        state = State.IDLE;
        if (!success) {
            host.showPopupNotification("QuickRetake: retake failed (recording did not restart)");
            host.println("[QR] sequence FAILED");
        } else {
            host.println("[QR] sequence done");
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
