package io.iuri.takelab.settings;

import com.bitwig.extension.controller.api.DocumentState;
import com.bitwig.extension.controller.api.Preferences;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.SettableEnumValue;
import com.bitwig.extension.controller.api.SettableRangedValue;

/**
 * Typed access to every TakeLab setting.
 *
 * Split across two surfaces on purpose:
 * - DocumentState -> Studio I/O panel: per-project, always at hand, the
 *   toggles people flip while working (scope, gesture, late undo, keep takes).
 * - Preferences -> controller settings page: one-time setup and calibration
 *   (tap window, delays, MIDI trigger mapping, notifications).
 */
public class RetakeSettings {

    public static final String GESTURE_DOUBLE = "Double tap (stop, play)";
    public static final String GESTURE_TRIPLE = "Triple tap (stop, play, stop)";

    public static final String TRIGGER_OFF = "Off";
    public static final String TRIGGER_NOTE = "Note";
    public static final String TRIGGER_CC = "CC";

    // Quick access (Studio I/O panel, per project). Bitwig does not render
    // boolean DocumentState settings in the panel, so toggles are Off/On enums.
    private static final String OFF = "Off";
    private static final String ON = "On";
    private static final String[] OFF_ON = { OFF, ON };

    private final SettableEnumValue arrangerEnabled;
    private final SettableEnumValue launcherEnabled;
    private final SettableEnumValue gesture;
    private final SettableEnumValue lateUndo;
    private final SettableEnumValue keepTakes;
    private final SettableEnumValue alwaysRecord;

    // Preferences (controller settings page)
    private final SettableRangedValue tapWindowMs;
    private final SettableBooleanValue suppressRearm;
    private final SettableRangedValue minRecordedBeats;
    private final SettableEnumValue triggerType;
    private final SettableRangedValue triggerNumber;
    private final SettableRangedValue triggerChannel;
    private final SettableRangedValue stepDelayMs;
    private final SettableBooleanValue bypassLaunchQuantization;
    private final SettableBooleanValue showNotifications;

    public RetakeSettings(Preferences prefs, DocumentState doc) {
        // --- Studio I/O panel ---
        arrangerEnabled = doc.getEnumSetting("Retake in Arranger", "Retake", OFF_ON, ON);
        launcherEnabled = doc.getEnumSetting("Retake in Launcher", "Retake", OFF_ON, ON);
        gesture = doc.getEnumSetting("Retake gesture", "Retake",
                new String[] { GESTURE_DOUBLE, GESTURE_TRIPLE }, GESTURE_DOUBLE);
        lateUndo = doc.getEnumSetting("Late undo (3 quick taps)", "Retake", OFF_ON, OFF);
        keepTakes = doc.getEnumSetting("Keep discarded takes (Launcher only)", "Retake", OFF_ON, OFF);
        alwaysRecord = doc.getEnumSetting("Always record (Arranger)", "Retake", OFF_ON, OFF);

        // --- Preferences ---
        tapWindowMs = prefs.getNumberSetting("Tap window", "Gesture Tuning", 150, 1000, 10, "ms", 400);
        suppressRearm = prefs.getBooleanSetting("Suppress re-record during tap window", "Gesture Tuning", true);
        minRecordedBeats = prefs.getNumberSetting("Ignore takes shorter than", "Gesture Tuning",
                0, 8, 0.25, "beats", 0);

        triggerType = prefs.getEnumSetting("Trigger type", "MIDI Trigger",
                new String[] { TRIGGER_OFF, TRIGGER_NOTE, TRIGGER_CC }, TRIGGER_OFF);
        triggerNumber = prefs.getNumberSetting("Note/CC number", "MIDI Trigger", 0, 127, 1, "", 64);
        triggerChannel = prefs.getNumberSetting("Channel", "MIDI Trigger", 1, 16, 1, "", 1);

        stepDelayMs = prefs.getNumberSetting("Engine step delay", "Advanced", 50, 300, 10, "ms", 100);
        bypassLaunchQuantization = prefs.getBooleanSetting("Bypass launch quantization on retake",
                "Advanced", false);
        showNotifications = prefs.getBooleanSetting("Show notifications", "Advanced", true);

        arrangerEnabled.markInterested();
        launcherEnabled.markInterested();
        gesture.markInterested();
        lateUndo.markInterested();
        keepTakes.markInterested();
        alwaysRecord.markInterested();
        tapWindowMs.markInterested();
        suppressRearm.markInterested();
        minRecordedBeats.markInterested();
        triggerType.markInterested();
        triggerNumber.markInterested();
        triggerChannel.markInterested();
        stepDelayMs.markInterested();
        bypassLaunchQuantization.markInterested();
        showNotifications.markInterested();
    }

    public boolean gestureIsTriple() {
        return GESTURE_TRIPLE.equals(gesture.get());
    }

    public long tapWindowMs() {
        return Math.round(tapWindowMs.getRaw());
    }

    public boolean suppressRearm() {
        return suppressRearm.get();
    }

    public boolean lateUndo() {
        return ON.equals(lateUndo.get());
    }

    public boolean arrangerEnabled() {
        return ON.equals(arrangerEnabled.get());
    }

    public boolean launcherEnabled() {
        return ON.equals(launcherEnabled.get());
    }

    public double minRecordedBeats() {
        return minRecordedBeats.getRaw();
    }

    public String triggerType() {
        return triggerType.get();
    }

    public int triggerNumber() {
        return (int) Math.round(triggerNumber.getRaw());
    }

    /** 0-based MIDI channel. */
    public int triggerChannel() {
        return (int) Math.round(triggerChannel.getRaw()) - 1;
    }

    public long stepDelayMs() {
        return Math.round(stepDelayMs.getRaw());
    }

    public boolean bypassLaunchQuantization() {
        return bypassLaunchQuantization.get();
    }

    public boolean showNotifications() {
        return showNotifications.get();
    }

    public boolean keepTakes() {
        return ON.equals(keepTakes.get());
    }

    public boolean alwaysRecord() {
        return ON.equals(alwaysRecord.get());
    }

    public SettableEnumValue alwaysRecordSetting() {
        return alwaysRecord;
    }


    public SettableEnumValue triggerTypeSetting() {
        return triggerType;
    }

    public SettableRangedValue triggerNumberSetting() {
        return triggerNumber;
    }

    public SettableRangedValue triggerChannelSetting() {
        return triggerChannel;
    }
}
