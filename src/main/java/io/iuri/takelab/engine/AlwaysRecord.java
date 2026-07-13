package io.iuri.takelab.engine;

import java.util.function.BooleanSupplier;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Transport;

import io.iuri.takelab.settings.RetakeSettings;

/**
 * Persistent record mode: while the panel toggle is on, the arranger record
 * toggle is guaranteed on — record, stop, click around, come back, press
 * play: still recording. Bitwig clears the record toggle after every stop;
 * a watchdog re-enables it continuously.
 *
 * The record button (the R key, when bound to Bitwig's Toggle Record action)
 * pauses and resumes the mode — it never touches the panel toggle. Disarming
 * record by hand pauses enforcement; arming it by hand resumes. The panel
 * toggle alone enables or disables the feature.
 *
 * Deciding whether a record-off was manual or Bitwig's automatic
 * clear-on-stop is deferred by 300ms, because the record-off and play-stop
 * callbacks arrive in unspecified order: any transport transition near the
 * off event (before or after) marks it automatic. While a decision is
 * pending the watchdog holds back, so it never re-arms against the user's
 * explicit R press.
 *
 * Resuming is guaranteed to converge: a clean manual arm resumes instantly,
 * and even if the timing classifier misses it, the watchdog resumes whenever
 * record has been armed and stable for a moment while paused — pressing R
 * always brings the mode back.
 */
public class AlwaysRecord {

    private static final long WATCHDOG_MS = 500;
    private static final long DECISION_DELAY_MS = 300;
    /** A transport transition within this window around a record-off event
     * means Bitwig cleared record itself (stop), not the user. */
    private static final long TRANSPORT_GRACE_MS = 800;
    /** How long record must stay armed while paused before the watchdog
     * concludes the arm was deliberate and resumes. Longer than any
     * retake/comping arm-to-play gap, so sequences never trip it. */
    private static final long ARM_SETTLE_MS = 600;

    private final ControllerHost host;
    private final Transport transport;
    private final RetakeSettings settings;
    private final BooleanSupplier holdoff;

    private boolean recordEnabled;
    private long lastPlayTransitionMs = Long.MIN_VALUE;
    private long lastRecordOnMs = Long.MIN_VALUE;
    private boolean decisionPending;
    /** Manual R-off pauses enforcement without touching the panel toggle;
     * manual R-on resumes it. */
    private boolean paused;

    public AlwaysRecord(ControllerHost host, Transport transport, RetakeSettings settings,
            BooleanSupplier holdoff) {
        this.host = host;
        this.transport = transport;
        this.settings = settings;
        this.holdoff = holdoff;

        transport.isPlaying().addValueObserver(playing ->
                lastPlayTransitionMs = System.currentTimeMillis());

        transport.isArrangerRecordEnabled().addValueObserver(enabled -> {
            recordEnabled = enabled;
            if (!enabled && settings.alwaysRecord() && !paused && !holdoff.getAsBoolean()) {
                final long tOff = System.currentTimeMillis();
                decisionPending = true;
                host.scheduleTask(() -> decideManualOff(tOff), DECISION_DELAY_MS);
            } else if (enabled) {
                final long tOn = System.currentTimeMillis();
                lastRecordOnMs = tOn;
                if (settings.alwaysRecord() && paused && !holdoff.getAsBoolean()) {
                    host.scheduleTask(() -> decideManualOn(tOn), DECISION_DELAY_MS);
                }
            }
        });

        settings.alwaysRecordSetting().addValueObserver(value -> {
            if (settings.alwaysRecord()) {
                paused = false;
                enforce();
            }
        });

        watchdog();
    }

    private void decideManualOff(long tOff) {
        decisionPending = false;
        if (!settings.alwaysRecord() || paused || recordEnabled || holdoff.getAsBoolean()) {
            return; // re-armed or managed meanwhile: automatic
        }
        if (lastPlayTransitionMs >= tOff - TRANSPORT_GRACE_MS) {
            return; // a stop/play happened around the off event: Bitwig's own clear
        }
        // Steady-state manual off (the R key): pause, panel toggle untouched.
        paused = true;
        host.showPopupNotification("TakeLab: always record paused (arm record to resume)");
        host.println("[TL] always-record: paused by manual record-off");
    }

    private void decideManualOn(long tOn) {
        if (!settings.alwaysRecord() || !paused || !recordEnabled || holdoff.getAsBoolean()) {
            return; // resumed, disarmed again, or managed meanwhile
        }
        if (lastPlayTransitionMs >= tOn - TRANSPORT_GRACE_MS) {
            return; // ambiguous timing: leave it to the watchdog's settle check
        }
        resume("manual record-on");
    }

    /** Convergence guarantee behind the fast path: while paused, record
     * armed and stable means the user wants the mode back. */
    private void syncResumeToArm() {
        if (!settings.alwaysRecord() || !paused || !recordEnabled
                || decisionPending || holdoff.getAsBoolean()) {
            return;
        }
        if (System.currentTimeMillis() - lastRecordOnMs < ARM_SETTLE_MS) {
            return; // just armed: let sequences (retake A4, comping) move on
        }
        resume("armed record settled");
    }

    private void resume(String why) {
        paused = false;
        host.showPopupNotification("TakeLab: always record ON");
        host.println("[TL] always-record: resumed (" + why + ")");
    }

    private void watchdog() {
        host.scheduleTask(() -> {
            syncResumeToArm();
            enforce();
            watchdog();
        }, WATCHDOG_MS);
    }

    private void enforce() {
        if (settings.alwaysRecord() && !paused && !recordEnabled && !decisionPending
                && !holdoff.getAsBoolean()) {
            transport.isArrangerRecordEnabled().set(true);
            host.println("[TL] always-record: re-enabled arranger record");
        }
    }
}
