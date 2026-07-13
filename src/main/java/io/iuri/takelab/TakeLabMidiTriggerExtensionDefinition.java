package io.iuri.takelab;

import java.util.UUID;

/**
 * Variant with one MIDI input for the footswitch/button trigger. Bitwig only
 * activates a controller once all declared ports are assigned, so this is a
 * separate entry from the port-less default.
 */
public class TakeLabMidiTriggerExtensionDefinition extends TakeLabExtensionDefinition {

    private static final UUID DRIVER_ID = UUID.fromString("3e8b6d92-51c7-4f0a-9d34-8a7e5c20fd18");

    @Override
    public String getName() {
        return "TakeLab + MIDI Trigger";
    }

    @Override
    public UUID getId() {
        return DRIVER_ID;
    }

    @Override
    public String getHardwareModel() {
        return "TakeLab + MIDI Trigger";
    }

    @Override
    public int getNumMidiInPorts() {
        return 1;
    }
}
