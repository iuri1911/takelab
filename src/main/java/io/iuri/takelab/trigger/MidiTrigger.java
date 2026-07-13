package io.iuri.takelab.trigger;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.HardwareActionBindable;
import com.bitwig.extension.controller.api.HardwareButton;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.MidiIn;

import io.iuri.takelab.settings.RetakeSettings;

/**
 * Optional one-press retake trigger from a MIDI note or CC (footswitch/pad).
 * The matcher is rebuilt whenever the trigger settings change; with the
 * trigger set to Off the bound action simply ignores presses.
 */
public class MidiTrigger {

    private final MidiIn midiIn;
    private final HardwareButton button;
    private final RetakeSettings settings;

    public MidiTrigger(ControllerHost host, HardwareSurface surface, RetakeSettings settings,
            Runnable onTrigger) {
        this.settings = settings;
        this.midiIn = host.getMidiInPort(0);

        button = surface.createHardwareButton("TL_RETAKE_TRIGGER");
        final HardwareActionBindable action = host.createAction(() -> {
            if (!RetakeSettings.TRIGGER_OFF.equals(settings.triggerType())) {
                onTrigger.run();
            }
        }, () -> "TakeLab: retake");
        action.addBinding(button.pressedAction());

        settings.triggerTypeSetting().addValueObserver(v -> updateMatcher());
        settings.triggerNumberSetting().addValueObserver(v -> updateMatcher());
        settings.triggerChannelSetting().addValueObserver(v -> updateMatcher());
        updateMatcher();
    }

    private void updateMatcher() {
        final int number = settings.triggerNumber();
        final int channel = settings.triggerChannel();
        if (RetakeSettings.TRIGGER_CC.equals(settings.triggerType())) {
            button.pressedAction().setActionMatcher(midiIn.createCCActionMatcher(channel, number, 127));
        } else {
            button.pressedAction().setActionMatcher(midiIn.createNoteOnActionMatcher(channel, number));
        }
    }
}
