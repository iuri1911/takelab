package dev.iuri.quickretake;

import java.util.function.Consumer;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;

import dev.iuri.quickretake.engine.ArrangerRetake;
import dev.iuri.quickretake.engine.LauncherRetake;
import dev.iuri.quickretake.engine.RecordingContext;
import dev.iuri.quickretake.engine.RecordingMonitor;
import dev.iuri.quickretake.engine.StepRunner;
import dev.iuri.quickretake.gesture.TapGestureDetector;
import dev.iuri.quickretake.settings.RetakeSettings;
import dev.iuri.quickretake.trigger.MidiTrigger;

public class QuickRetakeExtension extends ControllerExtension {

    private static final int NUM_TRACKS = 32;
    private static final int NUM_SCENES = 32;

    private final int numMidiInPorts;
    private HardwareSurface surface;
    private ArrangerRetake arrangerRetake;
    private LauncherRetake launcherRetake;

    protected QuickRetakeExtension(QuickRetakeExtensionDefinition definition, ControllerHost host) {
        super(definition, host);
        numMidiInPorts = definition.getNumMidiInPorts();
    }

    @Override
    public void init() {
        final ControllerHost host = getHost();

        final RetakeSettings settings = new RetakeSettings(host.getPreferences());

        final Transport transport = host.createTransport();
        transport.isPlaying().markInterested();
        transport.isArrangerRecordEnabled().markInterested();
        transport.playStartPosition().markInterested();
        transport.defaultLaunchQuantization().markInterested();

        final Application application = host.createApplication();
        application.canUndo().markInterested();

        final TrackBank trackBank = host.createTrackBank(NUM_TRACKS, 0, NUM_SCENES, true);

        final RecordingMonitor monitor = new RecordingMonitor(transport, trackBank);
        final StepRunner runner = new StepRunner(host);
        arrangerRetake = new ArrangerRetake(host, transport, application, monitor, settings, runner);
        launcherRetake = new LauncherRetake(host, transport, monitor, settings, runner);

        final TapGestureDetector detector =
                new TapGestureDetector(host, transport, settings, monitor, this::executeRetake);

        if (numMidiInPorts > 0) {
            surface = host.createHardwareSurface();
            new MidiTrigger(host, surface, settings, detector::onExternalTrigger);
        }

        host.println("[QR] QuickRetake 0.1.0 ready");
        host.showPopupNotification("QuickRetake loaded");
    }

    private void executeRetake(RecordingContext ctx, Consumer<Boolean> onDone) {
        switch (ctx.mode()) {
            case ARRANGER -> arrangerRetake.execute(ctx, onDone);
            case LAUNCHER -> launcherRetake.execute(ctx, onDone);
            default -> onDone.accept(false);
        }
    }

    @Override
    public void exit() {
        getHost().println("[QR] exit");
    }

    @Override
    public void flush() {
        if (surface != null) {
            surface.updateHardware();
        }
    }
}
