package io.iuri.takelab;

import java.util.List;
import java.util.function.Consumer;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.DocumentState;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;

import io.iuri.takelab.engine.AlwaysRecord;
import io.iuri.takelab.engine.ArrangerRetake;
import io.iuri.takelab.engine.CompingMode;
import io.iuri.takelab.engine.LauncherRetake;
import io.iuri.takelab.engine.RecordingContext;
import io.iuri.takelab.engine.RecordingMonitor;
import io.iuri.takelab.engine.StepRunner;
import io.iuri.takelab.gesture.TapGestureDetector;
import io.iuri.takelab.settings.RetakeSettings;
import io.iuri.takelab.trigger.MidiTrigger;

public class TakeLabExtension extends ControllerExtension {

    private static final int NUM_TRACKS = 32;
    private static final int NUM_SCENES = 32;

    private final int numMidiInPorts;
    private HardwareSurface surface;
    private ArrangerRetake arrangerRetake;
    private LauncherRetake launcherRetake;

    protected TakeLabExtension(TakeLabExtensionDefinition definition, ControllerHost host) {
        super(definition, host);
        numMidiInPorts = definition.getNumMidiInPorts();
    }

    @Override
    public void init() {
        final ControllerHost host = getHost();

        final DocumentState documentState = host.getDocumentState();
        final RetakeSettings settings = new RetakeSettings(host.getPreferences(), documentState);

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

        final CompingMode comping =
                new CompingMode(host, transport, trackBank, settings, runner, documentState);

        final Runnable lateUndo = () -> runner.run(settings.stepDelayMs(),
                List.of(transport::stop, application::undo), () -> {
                    if (settings.showNotifications()) {
                        host.showPopupNotification("TakeLab: late undo (last take removed)");
                    }
                });
        // A tap's junk crumb commits when its pass stops; give the engine one
        // step of room before undoing it.
        final Runnable discardMicroPass = () -> runner.run(settings.stepDelayMs(),
                List.of((Runnable) application::undo), null);
        final TapGestureDetector detector = new TapGestureDetector(host, transport, settings,
                monitor, this::executeRetake, comping::isActive, lateUndo, discardMicroPass);

        new AlwaysRecord(host, transport, settings,
                () -> detector.isRearmSuppressed() || comping.isActive());

        if (numMidiInPorts > 0) {
            surface = host.createHardwareSurface();
            new MidiTrigger(host, surface, settings, detector::onExternalTrigger);
        }

        host.println("[TL] TakeLab " + getExtensionDefinition().getVersion() + " ready");
        host.showPopupNotification("TakeLab loaded");
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
        getHost().println("[TL] exit");
    }

    @Override
    public void flush() {
        if (surface != null) {
            surface.updateHardware();
        }
    }
}
