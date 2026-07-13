package dev.iuri.quickretake.engine;

import java.util.ArrayList;
import java.util.List;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.DocumentState;
import com.bitwig.extension.controller.api.Signal;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;

import dev.iuri.quickretake.settings.RetakeSettings;

/**
 * MIDI comping via take lanes: the user arms N tracks ("lanes", typically note
 * tracks routed to one instrument track), sets the arranger loop over the
 * passage, and hits Start in the Studio I/O panel. Recording rolls in a loop
 * and on every loop wrap the record-arm rotates to the next lane, so each pass
 * lands on its own track. Audition buttons cycle an exclusive solo per lane.
 *
 * Controls live in the Studio I/O panel (DocumentState settings, per-project).
 */
public class CompingMode {

    private final ControllerHost host;
    private final Transport transport;
    private final TrackBank trackBank;
    private final RetakeSettings settings;
    private final StepRunner runner;

    private final List<Integer> lanes = new ArrayList<>();
    private boolean active;
    private int currentLane;
    private int auditionLane = -1;
    private double lastPlayPos = Double.NEGATIVE_INFINITY;
    private Boolean savedOverdub;

    public CompingMode(ControllerHost host, Transport transport, TrackBank trackBank,
            RetakeSettings settings, StepRunner runner) {
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

        final DocumentState doc = host.getDocumentState();
        final Signal start = doc.getSignalSetting("Armed tracks become lanes", "MIDI Comping", "Start");
        final Signal stop = doc.getSignalSetting("Stop and keep lanes", "MIDI Comping", "Stop");
        final Signal audition = doc.getSignalSetting("Cycle audible lane", "MIDI Comping", "Audition next lane");
        final Signal unmute = doc.getSignalSetting("Unmute all lanes", "MIDI Comping", "Unmute all");
        start.addSignalObserver(this::start);
        stop.addSignalObserver(this::stop);
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
            host.showPopupNotification("QuickRetake comping: arm 2+ lane tracks first");
            return;
        }

        active = true;
        currentLane = 0;
        auditionLane = -1;
        lastPlayPos = Double.NEGATIVE_INFINITY;

        // Re-entering a lane must REPLACE its previous take, not stack notes on
        // top of it: arranger overdub off for the whole comping session.
        savedOverdub = transport.isArrangerOverdubEnabled().get();
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
        host.println("[QR] comping start, lanes=" + lanes);
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
        host.println("[QR] comping lane -> " + currentLane);
    }

    private void stop() {
        if (!active) {
            return;
        }
        active = false;
        transport.stop();
        transport.isArrangerRecordEnabled().set(false);
        if (savedOverdub != null) {
            transport.isArrangerOverdubEnabled().set(savedOverdub);
            savedOverdub = null;
        }
        // Leave all lanes armed (as the user started); the last recorded lane
        // stays audible and the others muted — audition picks up from there.
        for (int lane : lanes) {
            trackBank.getItemAt(lane).arm().set(true);
        }
        auditionLane = currentLane;
        host.showPopupNotification("Comping: done, " + lanes.size()
                + " lanes recorded — cycle them with Audition next lane");
        host.println("[QR] comping stop");
    }

    /**
     * Audition by MUTE, not solo: only the audible lane changes, the rest of
     * the project keeps playing (musical context preserved).
     */
    private void auditionNext() {
        if (lanes.isEmpty()) {
            host.showPopupNotification("QuickRetake comping: no lanes yet");
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
