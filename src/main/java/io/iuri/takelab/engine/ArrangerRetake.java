package io.iuri.takelab.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Transport;

import io.iuri.takelab.settings.RetakeSettings;

/**
 * Arranger retake: the API cannot enumerate or delete arranger clips, so the
 * take is discarded with a single undo (one record pass = one undo step), then
 * the transport is rewound to the captured anchor and recording restarts.
 */
public class ArrangerRetake {

    private static final long RECORD_START_TIMEOUT_MS = 1500;

    private final ControllerHost host;
    private final Transport transport;
    private final Application application;
    private final RecordingMonitor monitor;
    private final RetakeSettings settings;
    private final StepRunner runner;

    public ArrangerRetake(ControllerHost host, Transport transport, Application application,
            RecordingMonitor monitor, RetakeSettings settings, StepRunner runner) {
        this.host = host;
        this.transport = transport;
        this.application = application;
        this.monitor = monitor;
        this.settings = settings;
        this.runner = runner;
    }

    public void execute(RecordingContext ctx, Consumer<Boolean> onDone) {
        // A0 — synchronous: kill whatever the second tap started before it commits anything.
        transport.stop();
        if (settings.showNotifications()) {
            host.showPopupNotification("TakeLab: retaking (arranger)");
        }
        host.println("[TL] arranger retake: anchor=" + ctx.anchorBeats()
                + " recorded=" + ctx.recordedBeats() + " beats");

        final List<Runnable> steps = new ArrayList<>();

        // A1 — junk-pass guard: with re-arm suppression off, the second tap may have
        // started (and our stop just ended) a new record pass that committed content.
        steps.add(() -> {
            if (monitor.recordPassCount() > ctx.recordPassCount() && monitor.contentRecordedInCurrentPass()) {
                host.println("[TL] undoing junk pass");
                application.undo();
            }
        });

        // A2 — discard the flubbed take, unless it never crossed the anchor (count-in:
        // nothing was committed, undo would eat an unrelated edit).
        steps.add(() -> {
            if (ctx.contentRecorded()) {
                host.println("[TL] undoing take");
                application.undo();
            } else {
                host.println("[TL] count-in case, no undo");
            }
        });

        // A3 — rewind to our own captured anchor (playStartPosition after an undo is not trusted).
        steps.add(() -> transport.getPosition().set(ctx.anchorBeats()));

        // A4 — re-arm (also restores what the tap-window suppression turned off).
        steps.add(() -> transport.isArrangerRecordEnabled().set(true));

        // A5 — go. The user's own count-in/pre-roll settings apply.
        steps.add(transport::play);

        runner.run(settings.stepDelayMs(), steps, () ->
                runner.await(monitor::isArrangerRecordingActive, RECORD_START_TIMEOUT_MS,
                        () -> onDone.accept(true),
                        () -> onDone.accept(false)));
    }
}
