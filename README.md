# TakeLab

**Instant MIDI retakes and take-lane comping for Bitwig Studio** — by [iuri.io](https://iuri.io)

Bitwig has comping for audio, but nothing equivalent for MIDI. Flub a take and you stop, delete, re-arm and start over by hand — a workflow gap the community has been asking about [since 2021](https://bitwish.top/t/midi-comping/12). TakeLab closes it as a controller extension, 100% inside Bitwig, no companion app.

> **Alpha** — this is the first public version. Expect bugs and rough edges; improvements and bug reports are very welcome (see [Status & contributing](#status--contributing)).

## Features

- **Double-tap retake** — recording and messed up? Tap play/stop twice, fast. TakeLab discards the take, rewinds to where it started and records again. Works in the **Arranger** and in the **Clip Launcher**.
- **MIDI comping with take lanes** — loop-record a passage; every loop pass lands on its own track, arm rotates automatically, only the active lane is audible. Audition lanes one at a time without soloing away the rest of your song. Controlled from Bitwig's **Studio I/O panel**.
- **Always record** — persistent record mode: the arranger record toggle stays armed through stops, clicks and edits — press play and you are recording, always. Disarming record by hand pauses it, arming resumes; Bitwig's default *Toggle Record* shortcut (`F9`), or `R` if you rebind it, becomes a one-key pause/resume switch (see [manual §7](docs/MANUAL.md#7-always-record)).
- **Late undo** — missed the tap window? Three quick taps discard the last take, no mouse needed. (Off by default.)
- **Keep takes (Launcher)** — instead of discarding, park each take in the slot and re-record into the next empty slot below.
- **MIDI footswitch trigger** — one pedal press = stop + retake, hands never leave the instrument.

## Requirements

- Bitwig Studio **6.x**
- To build from source: **JDK 21** and Maven

## Install

Grab `TakeLab.bwextension` from the [releases page](../../releases) and drop it into:

| OS | Path |
|---|---|
| macOS | `~/Documents/Bitwig Studio/Extensions/` |
| Windows | `%USERPROFILE%\Documents\Bitwig Studio\Extensions\` |
| Linux | `~/Bitwig Studio/Extensions/` |

Then in Bitwig: **Settings → Controllers → Add Controller → iuri.io** and pick:

- **TakeLab** — the default. Declares no MIDI ports, activates immediately.
- **TakeLab + MIDI Trigger** — only if you want the footswitch/pad trigger; requires assigning the MIDI input (Bitwig refuses to activate a controller with unassigned declared ports).

You should see the popup *"TakeLab loaded"*.

## Quick start: retake

1. Arm an instrument track, hit record, play.
2. Messed up? **Tap spacebar twice** (within 400 ms). Notes are discarded, the playhead jumps back, recording restarts — your count-in settings apply.
3. Happy? Stop with a **single** tap.

TakeLab can't see your keyboard (Bitwig's API has no keyboard access), so the gesture is inferred from the transport: *stopped while recording, then playing again inside the tap window*. Anything that stops/starts the transport counts — spacebar, controller button, mouse.

## Quick start: MIDI comping

One-time setup: an instrument track plus 3–8 empty **note tracks** ("lanes") with their **note output routed to the instrument track** — every lane plays the same sound. Then:

1. Set the **arranger loop** over the passage — the loop selection *is* the take region.
2. **Record-arm all lanes.**
3. Studio I/O panel (right edge) → **MIDI Comping → Start**.
4. Play. Each loop pass records to the next lane automatically; you always hear only the lane you're playing. Past the last lane it cycles back and **replaces** (never overdubs).
5. **Stop** — or just stop the transport; either way every arm/mute/overdub/loop state is restored.
6. **Audition next lane** cycles which lane is audible (mute-based — the rest of your song keeps playing). **Unmute all** clears everything.

Full walkthrough with screenshots-level detail: **[docs/MANUAL.md](docs/MANUAL.md)**.

## Controls

**Studio I/O panel** (per-project, the stuff you touch while working):

| Section | Control | What it does |
|---|---|---|
| Retake | Retake in Arranger / in Launcher | Enable each scope independently |
| Retake | Retake gesture | Double tap, or strict triple tap (zero false positives) |
| Retake | Late undo (3 quick taps) | Discard the last take after the window was missed |
| Retake | Always record (Arranger) | Keep the record toggle armed at all times; the record button pauses/resumes |
| Retake | Keep discarded takes (Launcher only) | Park takes in slots instead of deleting |
| MIDI Comping | Start / Stop | Begin & end a lane-recording session |
| MIDI Comping | Audition next lane / Unmute all | Cycle the audible lane / restore all lanes |

**Controller Preferences** (one-time setup & calibration): tap window (150–1000 ms), re-record suppression, minimum take length, MIDI trigger mapping (note/CC + channel), engine step delay, launch-quantization bypass, notifications. Every setting is documented in the [manual](docs/MANUAL.md).

## How it works (and the API walls it works around)

- Bitwig's Controller API cannot enumerate or delete **arranger clips**, so the arranger retake discards via a single **undo** — which is why an accidental retake is always one `Ctrl+Z` away from recovery.
- **Launcher** slots are fully scriptable, so there the clip is deleted (one named undo step) and the slot re-records — and keep-takes mode exists only there.
- The API has no computer-keyboard access and no way to register shortcuts, so gestures are inferred from transport state, and the panel buttons live in the Studio I/O panel (`DocumentState`).
- Actions are queued by the audio engine; TakeLab sequences stop → undo → rewind → record with a configurable delay (default 100 ms).

## Building from source

```bash
git clone https://github.com/iuri1911/takelab.git
cd takelab
mvn package   # needs JDK 21; deploys straight into your Bitwig Extensions folder
```

Bitwig hot-reloads the extension on every build.

## Limitations

- Tracks/scenes beyond a 32×32 window are not monitored.
- Simultaneous arranger + launcher recording can't be retaken (undo would swallow both); TakeLab warns and does nothing.
- Cutting the best fragments *between* lanes into a final comp is manual — the API can't move notes across arranger clips.

## Status & contributing

TakeLab is in **alpha**: it works — early users are recording and comping with it — but expect bugs, rough edges and fast iteration. Improvements are welcome and PRs even more so. House rules: everything in English; behavior changes must update **both** this README and [docs/MANUAL.md](docs/MANUAL.md).

Found a bug, have an idea, or just want to talk? Open an [issue](../../issues) or write to **ribeiro@iuri.io**.

## License

[MIT](LICENSE) — © [iuri.io](https://iuri.io)
