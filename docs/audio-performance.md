# Audio and performance validation

API 36 x86_64 emulator playback is functionally verified. JUCE opened `System Default (Output)` at 48,000 Hz and reported a 4,800-sample device block. The default drum pattern produced non-silent AudioFlinger output; playback continued after screen-off while the `mediaPlayback` foreground service was active. A new API 29 instrumentation test also confirmed transport and its foreground service stay active for two seconds after Home. These are emulator lifecycle/service checks, not latency measurements. A physical Samsung SM-X218U (Android 16/API 36, ARM64) opened the default speaker output through AAudio at 48 kHz with a reported 1,920-frame device block (~40 ms of audio per block), and a short transport start/stop smoke check passed. Callback timing, underruns, long-run performance, recording samples, and alternate routes remain unmeasured.

Microphone permission and foreground-service wiring pass on fresh API 29 and API 36 emulators. API 29 displays its “Allow” prompt and starts/stops the generic foreground service as input is armed/disarmed. API 36 displays “While using the app” and adds/removes the microphone service type. These checks did not validate captured audio samples. No physical microphone, USB input, or recorded take has been tested.

The existing engine callback is `AudioEngine::audioDeviceIOCallbackWithContext`. It receives the device block size and contains a loop over the remaining samples; the code does not assume every callback is exactly 256 frames. Existing `SnapshotExchange` uses atomic immutable snapshots, and `WorkerPool` uses a semaphore-backed pool. These are audit observations, not proof that all callback paths are allocation-free or lock-free on Android.

## Required measurements

| Measurement | Status | Evidence to record |
|---|---|---|
| Callback duration and deadline margin | Not measured | Sample rate, actual callback sizes, p50/p95/p99/max, project/plugin load. |
| Underruns | Not measured | Backend/device counter and sustained run length. |
| Initial physical output route | Smoke-tested | Samsung SM-X218U, Android 16/API 36, AAudio default speaker, 48 kHz, reported block 1,920 frames (~40 ms); short transport check only. |
| Sample-rate and device changes | Not measured | Device, route, requested and actual rate/buffer across route changes. |
| Background playback | Emulator lifecycle/service check only | API 29 and API 36 instrumentation confirm transport and the foreground `mediaPlayback` service remain active after Home; API 36 output also remained active after screen-off. No physical background playback or endurance test has been performed. API 37 hardening still needs runtime coverage. |
| Audio focus interruptions | Not measured | Verify phone call/other app focus loss, gain, and playback resume behavior on API 29+ and physical devices. |
| Recording/input routing | Permission/service path tested; capture unverified | Fresh API 29 and API 36 permission prompts and foreground-service arm/disarm passed. Captured samples, a saved take, physical microphone, USB input, and route changes remain unverified. |
| CPU, memory, battery, thermals | Not measured | Physical device model, Android build, project, duration, profiler. |
| ARM64 numerical regression | Not measured | Render project, desktop baseline, tolerance and differences. |

Keep file I/O, JNI, UI work, unbounded locks, and logging out of the real-time callback. The physical smoke check establishes only that the speaker route opens and short playback responds to transport; it does not represent latency, underrun, or sustained-load validation.
