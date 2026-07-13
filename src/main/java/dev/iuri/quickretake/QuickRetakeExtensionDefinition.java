package dev.iuri.quickretake;

import java.util.UUID;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

public class QuickRetakeExtensionDefinition extends ControllerExtensionDefinition {

    private static final UUID DRIVER_ID = UUID.fromString("7a4f3c1e-9b2d-4e8a-b6c5-2d1f0a8e6b43");

    @Override
    public String getName() {
        return "QuickRetake";
    }

    @Override
    public String getAuthor() {
        return "Iuri";
    }

    @Override
    public String getVersion() {
        return "0.1.0";
    }

    @Override
    public UUID getId() {
        return DRIVER_ID;
    }

    @Override
    public String getHardwareVendor() {
        return "QuickRetake";
    }

    @Override
    public String getHardwareModel() {
        return "QuickRetake";
    }

    @Override
    public int getRequiredAPIVersion() {
        return 25;
    }

    @Override
    public int getNumMidiInPorts() {
        // Optional footswitch/button trigger. The port may be left unassigned;
        // the transport-gesture detection works without any MIDI input.
        return 1;
    }

    @Override
    public int getNumMidiOutPorts() {
        return 0;
    }

    @Override
    public void listAutoDetectionMidiPortNames(AutoDetectionMidiPortNamesList list, PlatformType platformType) {
        // No autodetection: the user adds the extension manually and may assign any MIDI input.
    }

    @Override
    public QuickRetakeExtension createInstance(ControllerHost host) {
        return new QuickRetakeExtension(this, host);
    }
}
