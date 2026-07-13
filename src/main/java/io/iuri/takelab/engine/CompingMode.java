package io.iuri.takelab.engine;

import java.util.ArrayList;
import java.util.List;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.DocumentState;
import com.bitwig.extension.controller.api.Signal;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;

import io.iuri.takelab.settings.RetakeSettings;

/**
 * MIDI comping via take lanes: the user arms N tracks ("lanes", typically note
 * tracks routed to one instrument track), sets the arranger loop over the
 * passage, and hits Start in the Studio I/O panel. Recording rolls in a loop
 * and on every loop wrap the record-arm rotates to the next lane, so each pass
 * lands on its own track. Only the active lane is audible (mute-based, so the
 * rest of the project keeps playing). Audition cycles which lane is unmuted.
 *
 * The session ends when the user presses Stop in the panel OR stops the
 * transport by any means; both restore every touched state (arm, mute,
 * overdub, loop toggle) and clear automation overrides.
 */
public class CompingMode {

    private final ControllerHost host;
    private final Transport transport;
    private final TrackBank trackBank;
    private final RetakeSettings settings;
    private final StepRunner runner;

    private final List<Integer> lanes = new ArrayList<>();
    private boolean active;
    /** True between Start and the moment recording actually rolls; suppresses
     * the auto-finish that would otherwise trigger on our own initial stop(). */
    private boolean starting;
    private int currentLane;
    private int auditionLane = -1;
    private double lastPlayPos = Double.NEGATIVE_INFINITY;

    private boolean[] savedArm = new boolean[0];
    private boolean[] savedMute = new boolean[0];
    private boolean savedOverdub;
    private boolean savedLoopEnabled;

    public CompingMode(ControllerHost host, Transport transport, TrackBank trackBank,
            RetakeSettings settings, StepRunner runner, DocumentState doc) {
        this.host = host;
        this.transport = transport;
        this.trackBank = trackBank;
        this.settings = settings;
        this.runner = runner;

        transport.isArrangerLoopEnabled().markInterested();
        transport.arrangerLoopStart().markInterested();
        transport.arrangerLoopDuration().markInterested();
        transport.isArrangerOverdubEnabled().markInterested();

        for (int t = 0; t < trackBank.getSizeOfBank(); t++) {
            final Track track = trackBank.getItemAt(t);
            track.arm().markInterested();
            track.mute().markInterested();
            track.name().markInterested();
            track.exists().markInterested();
        }

        transport.playPosition().addValueObserver(this::onPlayPosition);
        transport.isPlaying().addValueObserver(this::onPlayingChanged);

        final Signal start = doc.getSignalSetting("Armed tracks become lanes", "MIDI Comping", "Start");
        final Signal stop = doc.getSignalSetting("End session, restore state", "MIDI Comping", "Stop");
        final Signal audition = doc.getSignalSetting("Cycle audible lane", "MIDI Comping", "Audition next lane");
        final Signal unmute = doc.getSignalSetting("Unmute lanes, clear overrides", "MIDI Comping", "Unmute all");
        start.addSignalObserver(this::start);
        stop.addSignalObserver(this::stopRequested);
        audition.addSignalObserver(this::auditionNext);
        unmute.addSignalObserver(this::unmuteAllLanes);
    }

    public boolean isActive() {
        return active;
    }

    private void start() {
        if (active) {
            return;
        }
        lanes.clear();
        for (int t = 0; t < trackBank.getSizeOfBank(); t++) {
            final Track track = trackBank.getItemAt(t);
            if (track.exists().get() && track.arm().get()) {
                lanes.add(t);
            }
        }
        if (lanes.size() < 2) {
            host.showPopupNotification("TakeLab comping: arm 2+ lane tracks first");
            return;
        }

        // Snapshot everything the session will touch, to restore on finish.
        savedArm = new boolean[lanes.size()];
        savedMute = new boolean[lanes.size()];
        for (int i = 0; i < lanes.size(); i++) {
            final Track track = trackBank.getItemAt(lanes.get(i));
            savedArm[i] = track.arm().get();
            savedMute[i] = track.mute().get();
        }
        savedOverdub = transport.isArrangerOverdubEnabled().get();
        savedLoopEnabled = transport.isArrangerLoopEnabled().get();

        active = true;
        starting = true;
        currentLane = 0;
        auditionLane = -1;
        lastPlayPos = Double.NEGATIVE_INFINITY;

        // Re-entering a lane must REPLACE its previous take, not stack notes.
        transport.isArrangerOverdubEnabled().set(false);

        final double loopStart = transport.arrangerLoopStart().get();
        focusLane(currentLane);

        final List<Runnable> steps = new ArrayList<>();
        steps.add(transport::stop);
        steps.add(() -> transport.isArrangerLoopEnabled().set(true));
        steps.add(() -> transport.getPosition().set(loopStart));
        steps.add(() -> transport.isArrangerRecordEnabled().set(true));
        steps.add(transport::play);
        runner.run(settings.stepDelayMs(), steps, () ->
                host.showPopupNotification("Comping: recording lane 1/" + lanes.size()
                        + " (" + laneName(0) + ")"));
        host.println("[TL] comping start, lanes=" + lanes);
    }

    private void onPlayingChanged(boolean playing) {
        if (!active) {
            return;
        }
        if (playing) {
            starting = false;
        } else if (!starting) {
            // Transport stopped by any means (spacebar, panel Stop, controller):
            // the session is over — never leave arms/mutes behind.
            finish();
        }
    }

    private void onPlayPosition(double pos) {
        if (!active) {
            lastPlayPos = pos;
            return;
        }
        // A loop wrap shows as a large backwards jump of the playhead.
        final double loopLen = Math.max(1.0, transport.arrangerLoopDuration().get());
        if (lastPlayPos != Double.NEGATIVE_INFINITY && pos < lastPlayPos - loopLen * 0.5) {
            advanceLane();
        }
        lastPlayPos = pos;
    }

    private void advanceLane() {
        currentLane = (currentLane + 1) % lanes.size();
        focusLane(currentLane);
        final String cycled = currentLane == 0 ? " — cycled back, replacing takes" : "";
        host.showPopupNotification("Comping: lane " + (currentLane + 1) + "/" + lanes.size()
                + " (" + laneName(currentLane) + ")" + cycled);
        host.println("[TL] comping lane -> " + currentLane);
    }

    private void stopRequested() {
        if (!active) {
            return;
        }
        transport.stop();
        finish(); // idempotent with the isPlaying-driven finish
    }

    /** Restore every state the session touched and clear automation overrides. */
    private void finish() {
        if (!active) {
            return;
        }
        active = false;
        starting = false;
        transport.isArrangerRecordEnabled().set(false);
        transport.isArrangerOverdubEnabled().set(savedOverdub);
        transport.isArrangerLoopEnabled().set(savedLoopEnabled);
        for (int i = 0; i < lanes.size(); i++) {
            final Track track = trackBank.getItemAt(lanes.get(i));
            track.arm().set(savedArm[i]);
            track.mute().set(savedMute[i]);
        }
        transport.resetAutomationOverrides();
        auditionLane = -1;
        host.showPopupNotification("Comping: done, " + lanes.size()
                + " lanes recorded — cycle them with Audition next lane");
        host.println("[TL] comping finished, state restored");
    }

    /**
     * Audition by MUTE, not solo: only the audible lane changes, the rest of
     * the project keeps playing (musical context preserved).
     */
    private void auditionNext() {
        if (lanes.isEmpty()) {
            host.showPopupNotification("TakeLab comping: no lanes yet");
            return;
        }
        auditionLane = (auditionLane + 1) % lanes.size();
        muteAllExcept(auditionLane);
        host.showPopupNotification("Comping: auditioning lane " + (auditionLane + 1) + "/"
                + lanes.size() + " (" + laneName(auditionLane) + ")");
    }

    private void unmuteAllLanes() {
        for (int lane : lanes) {
            trackBank.getItemAt(lane).mute().set(false);
        }
        transport.resetAutomationOverrides();
        auditionLane = -1;
    }

    /** The active lane records and is heard; every other lane is muted. */
    private void focusLane(int laneIndex) {
        for (int i = 0; i < lanes.size(); i++) {
            trackBank.getItemAt(lanes.get(i)).arm().set(i == laneIndex);
        }
        muteAllExcept(laneIndex);
    }

    private void muteAllExcept(int laneIndex) {
        for (int i = 0; i < lanes.size(); i++) {
            trackBank.getItemAt(lanes.get(i)).mute().set(i != laneIndex);
        }
    }

    private String laneName(int laneIndex) {
        final String name = trackBank.getItemAt(lanes.get(laneIndex)).name().get();
        return name == null || name.isBlank() ? "track " + (lanes.get(laneIndex) + 1) : name;
    }
}
