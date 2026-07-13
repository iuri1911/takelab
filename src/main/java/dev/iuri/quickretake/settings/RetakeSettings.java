package dev.iuri.quickretake.settings;

import com.bitwig.extension.controller.api.Preferences;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.SettableEnumValue;
import com.bitwig.extension.controller.api.SettableRangedValue;

/** Typed access to the extension's Preferences page. */
public class RetakeSettings {

    public static final String GESTURE_DOUBLE = "Stop then Play";
    public static final String GESTURE_TRIPLE = "Stop, Play, Stop (strict)";

    public static final String TRIGGER_OFF = "Off";
    public static final String TRIGGER_NOTE = "Note";
    public static final String TRIGGER_CC = "CC";

    private final SettableEnumValue gesture;
    private final SettableRangedValue tapWindowMs;
    private final SettableBooleanValue suppressRearm;
    private final SettableBooleanValue lateUndo;

    private final SettableBooleanValue arrangerEnabled;
    private final SettableBooleanValue launcherEnabled;
    private final SettableRangedValue minRecordedBeats;

    private final SettableEnumValue triggerType;
    private final SettableRangedValue triggerNumber;
    private final SettableRangedValue triggerChannel;

    private final SettableRangedValue stepDelayMs;
    private final SettableBooleanValue bypassLaunchQuantization;
    private final SettableBooleanValue showNotifications;
    private final SettableBooleanValue keepTakes;

    public RetakeSettings(Preferences prefs) {
        gesture = prefs.getEnumSetting("Gesture", "Gesture",
                new String[] { GESTURE_DOUBLE, GESTURE_TRIPLE }, GESTURE_DOUBLE);
        tapWindowMs = prefs.getNumberSetting("Tap window", "Gesture", 150, 1000, 10, "ms", 400);
        suppressRearm = prefs.getBooleanSetting("Suppress re-record during tap window", "Gesture", true);
        lateUndo = prefs.getBooleanSetting("Late undo (triple tap outside recording)", "Gesture", false);

        arrangerEnabled = prefs.getBooleanSetting("Arranger retake", "Scope", true);
        launcherEnabled = prefs.getBooleanSetting("Launcher retake", "Scope", true);
        minRecordedBeats = prefs.getNumberSetting("Minimum recorded length", "Scope", 0, 8, 0.25, "beats", 0);

        triggerType = prefs.getEnumSetting("Trigger type", "MIDI Trigger",
                new String[] { TRIGGER_OFF, TRIGGER_NOTE, TRIGGER_CC }, TRIGGER_OFF);
        triggerNumber = prefs.getNumberSetting("Note/CC number", "MIDI Trigger", 0, 127, 1, "", 64);
        triggerChannel = prefs.getNumberSetting("Channel", "MIDI Trigger", 1, 16, 1, "", 1);

        stepDelayMs = prefs.getNumberSetting("Step delay", "Advanced", 50, 300, 10, "ms", 100);
        bypassLaunchQuantization = prefs.getBooleanSetting("Bypass launch quantization on retake", "Advanced", false);
        showNotifications = prefs.getBooleanSetting("Show notifications", "Advanced", true);
        keepTakes = prefs.getBooleanSetting("Keep discarded takes (launcher)", "Advanced", false);

        gesture.markInterested();
        tapWindowMs.markInterested();
        suppressRearm.markInterested();
        lateUndo.markInterested();
        arrangerEnabled.markInterested();
        launcherEnabled.markInterested();
        minRecordedBeats.markInterested();
        triggerType.markInterested();
        triggerNumber.markInterested();
        triggerChannel.markInterested();
        stepDelayMs.markInterested();
        bypassLaunchQuantization.markInterested();
        showNotifications.markInterested();
        keepTakes.markInterested();
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
        return lateUndo.get();
    }

    public boolean arrangerEnabled() {
        return arrangerEnabled.get();
    }

    public boolean launcherEnabled() {
        return launcherEnabled.get();
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
        return keepTakes.get();
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
