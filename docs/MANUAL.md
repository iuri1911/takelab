# TakeLab Manual

*Version 0.3.0 — for Bitwig Studio 6.x. The [README](../README.md) is the overview; this is the full reference.*

---

## Contents

1. [Concepts](#1-concepts)
2. [Installation & activation](#2-installation--activation)
3. [The retake gesture](#3-the-retake-gesture)
4. [Retake in the Arranger](#4-retake-in-the-arranger)
5. [Retake in the Clip Launcher](#5-retake-in-the-clip-launcher)
6. [Late undo](#6-late-undo)
7. [Always record](#7-always-record)
8. [MIDI comping with take lanes](#8-midi-comping-with-take-lanes)
9. [MIDI footswitch trigger](#9-midi-footswitch-trigger)
10. [Every setting explained](#10-every-setting-explained)
11. [Troubleshooting](#11-troubleshooting)
12. [Design notes & API limitations](#12-design-notes--api-limitations)

---

## 1. Concepts

TakeLab is a Bitwig **controller extension** (a `.bwextension`), not a plugin. It never touches your audio or note streams; it watches and drives Bitwig's transport, tracks and clips through the Controller API.

Two ideas run through everything:

- **A retake is one gesture.** You are recording, you flub it, you tap twice — the bad take is gone and you are recording again from the same spot. No mouse, no re-arming, no cleanup.
- **A take region is the arranger loop.** For comping, the loop braces define where takes begin and end; every loop pass becomes one take on its own lane.

## 2. Installation & activation

1. Copy `TakeLab.bwextension` into your Bitwig extensions folder:
   - macOS: `~/Documents/Bitwig Studio/Extensions/`
   - Windows: `%USERPROFILE%\Documents\Bitwig Studio\Extensions\`
   - Linux: `~/Bitwig Studio/Extensions/`
2. Bitwig → **Settings → Controllers**. If nothing appears, click the orange refresh icon.
3. **Add Controller → iuri.io → TakeLab → Add.**

Two products exist under the iuri.io vendor:

| Product | MIDI ports | When to use |
|---|---|---|
| **TakeLab** | none | Default. Activates immediately. |
| **TakeLab + MIDI Trigger** | 1 in | Only if you use a footswitch/pad trigger. Bitwig will not activate it until you assign the MIDI input. |

On activation you get the popup *"TakeLab loaded"*. The extension's console (speech-bubble icon next to the controller) logs every state change with a `[TL]` prefix — your first stop when debugging.

## 3. The retake gesture

Bitwig's API exposes no computer-keyboard events, so TakeLab infers your taps from **transport state changes**. Any play/stop source counts: spacebar, a controller's transport buttons, clicking in the transport bar.

**Double tap (default):** while recording, *stop* then *play* within the **tap window** (default 400 ms, Preferences → Gesture Tuning).

```
recording ──stop──▶ [window open 400ms] ──play──▶ RETAKE
                          └── window expires ──▶ nothing (normal stop)
```

**Triple tap (strict):** *stop, play, stop*, each within the window. The middle *play* alone keeps playing — so quickly stopping and listening back never triggers a retake. Zero false positives, one more tap. Pick it in the Studio I/O panel → Retake gesture.

Guards that prevent accidents:

- The gesture only arms **coming out of a recording**. Stopping/starting plain playback never triggers anything.
- *Suppress re-record during tap window* (default on) briefly disarms the arranger record toggle while the window is open, so your second tap can't start a junk recording before TakeLab reacts.
- *Ignore takes shorter than N beats* (default 0 = off) ignores the gesture when almost nothing was recorded.
- Every retake shows a popup, so a false trigger is visible — and reversible with a single `Ctrl+Z`/`Cmd+Z`.

## 4. Retake in the Arranger

What happens on the gesture, in order (steps spaced by the *Engine step delay*):

1. Transport stops.
2. The recorded take is discarded with **one undo**. If you retake during a count-in/pre-roll — before the playhead ever crossed the take start — nothing was committed, and no undo is consumed.
3. The playhead jumps back to where the take started (`play start position`, captured when recording began).
4. Record is re-armed and playback restarts — your own count-in/metronome settings apply.

The console line for a healthy retake looks like:

```
[TL] ARMED gen=7 mode=ARRANGER anchor=17.0
[TL] EXECUTING mode=ARRANGER
[TL] arranger retake: anchor=17.0 recorded=8.2 beats
[TL] undoing take
[TL] sequence done
```

**Why undo?** The Controller API cannot list or delete arranger clips (see §12). Undo of a just-finished record pass is exact and — crucially — means a mistaken retake is always recoverable.

## 5. Retake in the Clip Launcher

Launcher slots are fully scriptable, so the flow is direct: the recording clip(s) are deleted as **one named undo step** ("TakeLab discard"), then each slot records again. Launch quantization applies to the restart; enable *Bypass launch quantization on retake* (Preferences → Advanced) to restart instantly.

**Keep takes** (Studio I/O panel → Retake): instead of deleting, the flubbed take stays in its slot and recording moves to the **first empty slot below** in the same track. Three retakes = takes stacked in slots 1–3, slot 4 recording. With no empty slot left, TakeLab warns and overwrites. Launcher only — see §12 for why the arranger can't have this.

Recording in the arranger **and** a launcher slot at the same time is not retakeable (the undo would swallow both); TakeLab pops a warning and leaves everything untouched.

## 6. Late undo

Off by default — enable in Studio I/O panel → Retake.

The scenario: you stopped recording, meant to retake, but tapped too slowly; the window closed and the bad take is committed. Instead of reaching for the mouse: **three quick transport taps** (each within the tap window, outside any recording) stop the transport and fire a single undo — the take is gone.

It never triggers while recording (that's the retake path) and never counts taps while a comping session runs.

**With always record on** (§7), every tap of play is technically a recording — TakeLab handles it: a recording pass shorter than the tap window is treated as gesture tapping, its junk crumb is discarded immediately, and record is suppressed for one tap window so the remaining taps play clean. The three-tap undo then behaves exactly as described above.

## 7. Always record

Off by default — enable in Studio I/O panel → Retake → **Always record (Arranger)**.

Bitwig clears the arranger record toggle after every stop; if you are in a "record everything, sort it out later" flow, re-arming before each pass is friction. While this mode is on, TakeLab guarantees the record toggle stays armed: record, stop, click around, edit, come back — press play and you are recording. A watchdog re-arms within half a second of any clear.

**Pausing without leaving the mode:** manually disarming record (the transport record button, or a key bound to it) *pauses* enforcement — the popup says so, and the panel toggle stays on. Arming record again resumes. Pausing is how you play back without recording; resuming is one press away.

**Recommended setup:** Bitwig's *Toggle Record* action already has a default shortcut, `F9` — that alone is enough to pause/resume the mode with one key. If you'd rather use `R`, bind it in **Dashboard → Settings → Shortcuts** (search "record", assign `R` to *Toggle Record*) — a personal preference, nothing about the mode changes either way. Either key becomes a one-press switch: press to pause, press again to resume — the panel toggle remains the on/off switch for the feature itself. (Extensions cannot register shortcuts of their own — see §12.)

Retakes (§3–§4) and late undo (§6) work normally while the mode is active. Comping sessions (§8) manage the record toggle themselves; always record stands back until the session ends.

## 8. MIDI comping with take lanes

Bitwig's take lanes and comping editor are audio-only. TakeLab implements the MIDI equivalent that *is* reachable through the API: **one full take per pass, each on its own track**, hands-free.

### One-time track setup

1. Create your instrument track as usual (synth/sampler loaded).
2. Create 3–8 empty **note tracks** — your lanes. Grouping them with the instrument track keeps things tidy.
3. On each lane, route the **note output to the instrument track** (track inspector → note/MIDI routing). Every lane now plays the same sound.

### Recording takes

1. Drag the **arranger loop braces** over the passage. The loop *is* the take region.
2. **Record-arm every lane** you want to use.
3. Studio I/O panel (right edge of Bitwig) → **MIDI Comping → Start**. TakeLab snapshots the state of everything it is about to touch, enables the loop, jumps to its start, arms only lane 1 and starts recording.
4. Play. At every loop wrap the record-arm rotates to the next lane — popups track it ("Comping: lane 2/4 …"). You always hear **only the active lane**; the other lanes are muted, the rest of your project plays on (mute-based, never solo).
5. Past the last lane it cycles back to lane 1 and **replaces** that take (arranger overdub is force-disabled during the session — still playing means you haven't found the take yet).
6. Done? Press **Stop** in the panel — or just stop the transport any way you like. Both end the session and **restore everything**: arms, mutes, overdub, loop toggle, and any automation-override flags are cleared.

### Picking the take

- **Audition next lane** cycles which lane is audible, in musical context.
- **Unmute all** opens every lane and clears automation overrides.
- The final comp — splicing the best bars from different lanes — is manual editing (the API cannot move notes between arranger clips): audition, pick, cut/paste in the note editor.

## 9. MIDI footswitch trigger

Use the **TakeLab + MIDI Trigger** product, assign its MIDI input, then in Preferences → MIDI Trigger set the type (Note or CC), number and channel.

One press while recording = stop + retake in a single motion. A press during the tap window also fires the retake. CC triggers match value 127 (a momentary switch's "press"), so a pedal release won't double-fire.

## 10. Every setting explained

### Studio I/O panel (per project — quick access)

| Setting | Default | Meaning |
|---|---|---|
| Retake in Arranger | on | Enable the arranger retake path |
| Retake in Launcher | on | Enable the launcher retake path |
| Retake gesture | Double tap | `Double tap (stop, play)` or `Triple tap (stop, play, stop)` — strict mode, zero false positives |
| Late undo (3 quick taps) | off | §6 |
| Keep discarded takes (Launcher only) | off | §5 |
| Always record (Arranger) | off | §7 |
| MIDI Comping: Start / Stop / Audition next lane / Unmute all | — | §8 |

### Controller Preferences (global — setup & calibration)

| Category | Setting | Default | Meaning |
|---|---|---|---|
| Gesture Tuning | Tap window | 400 ms | Max gap between taps (150–1000). Calibrate to your reflexes: too long and quick stop-then-listen may read as a retake; too short and you'll miss it |
| Gesture Tuning | Suppress re-record during tap window | on | Blocks the junk-recording the 2nd tap could start (§3) |
| Gesture Tuning | Ignore takes shorter than | 0 beats | Gesture ignored below this recorded length |
| MIDI Trigger | Trigger type / Note-CC number / Channel | Off / 64 / 1 | §9 |
| Advanced | Engine step delay | 100 ms | Spacing between queued engine actions (stop→undo→jump→record). Raise on very large projects if retakes misfire |
| Advanced | Bypass launch quantization on retake | off | Launcher retakes restart instantly instead of on the next quantum |
| Advanced | Show notifications | on | Popup on every retake/comping event |

## 11. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Extension not in the Add Controller list | Wrong folder, or Bitwig older than 6.x. Click the orange refresh icon; restart Bitwig |
| Controller won't activate (power button won't stay on) | You added **TakeLab + MIDI Trigger** without assigning its MIDI input. Assign one, or use plain **TakeLab** |
| "TakeLab did something wrong" dialog | Open the console (speech-bubble icon), copy the message, open an issue |
| Retake fires when I only wanted to stop and listen | Shorten the tap window, or switch to the **triple tap** gesture |
| Retake doesn't fire | Tap faster than the window; check the scope toggles (panel → Retake); watch the `[TL] ARMED` line in the console |
| Retake failed popup | The engine didn't restart recording within the timeout — raise *Engine step delay* |
| Wrong thing undone by a retake | You weren't recording (or recorded elsewhere). Everything TakeLab discards is one `Ctrl+Z` away |
| Notes at loop boundary land on the wrong lane | Lane switching happens exactly at the wrap; notes held across it stay on the previous lane. Leave a beat of slack in the loop, or play inside the region |
| Blue automation-override icon stays lit | Fixed in 0.2.0 — comping end and *Unmute all* clear overrides. If you see it elsewhere, hit Bitwig's restore-automation button and report |
| Record toggle keeps turning itself back on | *Always record* is on (panel → Retake). Disarm record by hand to pause it, or turn the toggle off |
| Paused always record won't resume | Arm record and leave it armed for a second — the watchdog resumes even if the quick path missed the press. Check the `[TL] always-record` console lines |

## 12. Design notes & API limitations

For the curious and for contributors:

- **Arranger clips are invisible to the API.** No enumeration, no deletion, no note editing without selection. This dictates the undo-based arranger retake and rules out keep-takes and automatic comp-splicing on the timeline.
- **No keyboard input, no registrable shortcuts.** Controller extensions can invoke Bitwig actions but cannot appear in the shortcut settings, and never see key presses. Hence transport-inferred gestures and Studio I/O panel buttons (`DocumentState` settings, which Bitwig saves per project).
- **Deprecated API calls throw.** Bitwig 6 hard-fails extensions calling deprecated methods (e.g. `Transport.addIsRecordingObserver`); "is recording" is derived from `isPlaying && isArrangerRecordEnabled`.
- **The engine queues actions.** Consecutive transport/undo calls in one callback are unreliable; TakeLab chains steps via `scheduleTask` with the configurable step delay, and timers carry generation tokens because they cannot be cancelled.
- **Required API version is 18** even though newer levels exist: Bitwig 6.0 rejects extensions requiring the 6.1-cycle API, and nothing TakeLab uses is newer than API 15.
- Monitoring covers a fixed **32 tracks × 32 scenes** window.

---

*TakeLab is MIT-licensed open source by [iuri.io](https://iuri.io). Bug reports and PRs: keep the README and this manual in sync with any behavior change.*
