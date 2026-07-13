package io.iuri.takelab;

import java.util.UUID;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

public class TakeLabExtensionDefinition extends ControllerExtensionDefinition {

    private static final UUID DRIVER_ID = UUID.fromString("7a4f3c1e-9b2d-4e8a-b6c5-2d1f0a8e6b43");

    @Override
    public String getName() {
        return "TakeLab";
    }

    @Override
    public String getAuthor() {
        return "iuri.io";
    }

    @Override
    public String getVersion() {
        return "0.3.0";
    }

    @Override
    public UUID getId() {
        return DRIVER_ID;
    }

    @Override
    public String getHardwareVendor() {
        return "iuri.io";
    }

    @Override
    public String getHelpFilePath() {
        return "https://github.com/iuri1911/takelab/blob/main/docs/MANUAL.md";
    }

    @Override
    public String getHardwareModel() {
        return "TakeLab";
    }

    @Override
    public int getRequiredAPIVersion() {
        // Bitwig 6.0.x does not accept the 6.1-cycle API 25; every method used exists by API 18.
        return 18;
    }

    @Override
    public int getNumMidiInPorts() {
        // Bitwig refuses to activate a controller until every declared MIDI port is
        // assigned, and the transport-gesture detection needs none. The footswitch
        // variant (TakeLabMidiTriggerExtensionDefinition) declares the port.
        return 0;
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
    public TakeLabExtension createInstance(ControllerHost host) {
        return new TakeLabExtension(this, host);
    }
}
