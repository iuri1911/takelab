package dev.iuri.quickretake.engine;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.bitwig.extension.controller.api.ControllerHost;

/**
 * Runs a sequence of steps spaced by a delay, then optionally polls for a
 * completion condition. Needed because Bitwig queues host actions in the audio
 * engine: issuing stop/undo/jump/record back-to-back in one callback is unreliable.
 */
public class StepRunner {

    private static final long POLL_MS = 50;

    private final ControllerHost host;

    public StepRunner(ControllerHost host) {
        this.host = host;
    }

    public void run(long delayMs, List<Runnable> steps, Runnable onDone) {
        next(steps, 0, delayMs, onDone);
    }

    private void next(List<Runnable> steps, int index, long delayMs, Runnable onDone) {
        if (index >= steps.size()) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        host.scheduleTask(() -> {
            steps.get(index).run();
            next(steps, index + 1, delayMs, onDone);
        }, delayMs);
    }

    public void await(BooleanSupplier condition, long timeoutMs, Runnable onSuccess, Runnable onTimeout) {
        poll(condition, System.currentTimeMillis() + timeoutMs, onSuccess, onTimeout);
    }

    private void poll(BooleanSupplier condition, long deadlineMs, Runnable onSuccess, Runnable onTimeout) {
        if (condition.getAsBoolean()) {
            onSuccess.run();
            return;
        }
        if (System.currentTimeMillis() >= deadlineMs) {
            onTimeout.run();
            return;
        }
        host.scheduleTask(() -> poll(condition, deadlineMs, onSuccess, onTimeout), POLL_MS);
    }
}
