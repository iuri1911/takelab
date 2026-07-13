package io.iuri.takelab.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.DeleteableObject;
import com.bitwig.extension.controller.api.Transport;

import io.iuri.takelab.engine.RecordingContext.SlotRef;
import io.iuri.takelab.settings.RetakeSettings;

/**
 * Launcher retake: slots are fully scriptable, so the take clip is deleted
 * (one named undo step for all slots) and each slot re-records. With
 * keep-takes enabled, the take is left in place and recording moves to the
 * first empty slot below in the same track instead.
 */
public class LauncherRetake {

    private static final long RECORD_START_TIMEOUT_MS = 2000;

    private final ControllerHost host;
    private final Transport transport;
    private final RecordingMonitor monitor;
    private final RetakeSettings settings;
    private final StepRunner runner;

    public LauncherRetake(ControllerHost host, Transport transport, RecordingMonitor monitor,
            RetakeSettings settings, StepRunner runner) {
        this.host = host;
        this.transport = transport;
        this.monitor = monitor;
        this.settings = settings;
        this.runner = runner;
    }

    public void execute(RecordingContext ctx, Consumer<Boolean> onDone) {
        // L0 — synchronous: the second tap only started plain playback; stop it.
        transport.stop();
        if (settings.showNotifications()) {
            host.showPopupNotification("TakeLab: retaking (launcher)");
        }
        host.println("[TL] launcher retake: " + ctx.slots().size() + " slot(s)");

        final boolean keepTakes = settings.keepTakes();
        final List<SlotRef> recordTargets = keepTakes ? resolveKeepTakeTargets(ctx) : ctx.slots();

        final boolean bypassQ = settings.bypassLaunchQuantization();
        final String[] savedQuantization = new String[1];

        final List<Runnable> steps = new ArrayList<>();

        if (!keepTakes) {
            // L1 — one named undo step for the whole discard: an accidental trigger is one Ctrl+Z away.
            steps.add(() -> {
                final DeleteableObject[] clips = ctx.slots().stream()
                        .map(SlotRef::slot)
                        .toArray(DeleteableObject[]::new);
                host.deleteObjects("TakeLab discard", clips);
            });
        }

        if (bypassQ) {
            steps.add(() -> {
                savedQuantization[0] = transport.defaultLaunchQuantization().get();
                transport.defaultLaunchQuantization().set("none");
            });
        }

        // L2 — re-record every slot that was recording.
        steps.add(() -> recordTargets.forEach(ref -> ref.slot().record()));

        runner.run(settings.stepDelayMs(), steps, () ->
                runner.await(monitor::anySlotRecordingOrQueued, RECORD_START_TIMEOUT_MS,
                        () -> {
                            restoreQuantization(bypassQ, savedQuantization);
                            onDone.accept(true);
                        },
                        () -> {
                            restoreQuantization(bypassQ, savedQuantization);
                            onDone.accept(false);
                        }));
    }

    private void restoreQuantization(boolean bypassed, String[] saved) {
        if (bypassed && saved[0] != null) {
            transport.defaultLaunchQuantization().set(saved[0]);
        }
    }

    /**
     * Keep-takes: for each recorded slot, record into the first empty slot below
     * it in the same track (within the monitored scene window). Falls back to
     * re-recording the same slot (overwriting) when no empty slot exists.
     */
    private List<SlotRef> resolveKeepTakeTargets(RecordingContext ctx) {
        final List<SlotRef> targets = new ArrayList<>();
        for (SlotRef ref : ctx.slots()) {
            final ClipLauncherSlotBank slotBank = monitor.trackAt(ref.trackIndex()).clipLauncherSlotBank();
            SlotRef target = null;
            for (int s = ref.slotIndex() + 1; s < slotBank.getSizeOfBank(); s++) {
                final ClipLauncherSlot candidate = slotBank.getItemAt(s);
                if (!candidate.hasContent().get()) {
                    target = new SlotRef(ref.trackIndex(), s, candidate);
                    break;
                }
            }
            if (target == null) {
                host.showPopupNotification("TakeLab: no empty slot below, overwriting take");
                target = ref;
            }
            targets.add(target);
        }
        return targets;
    }
}
