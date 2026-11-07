# Release checklist

No public release is authorized or ready. This list tracks requirements before a future release.

- [ ] Reproducible ARM64 APK build with pinned toolchain.
- [ ] Core C++ tests and Android instrumentation tests pass.
- [ ] API 29 and current stable emulator launch/lifecycle tests recorded.
- [ ] Physical-device playback, recording, MIDI/audio routing, latency, battery, and thermal checks pass.
- [ ] Playlist, Piano Roll, Channel Rack, Mixer, touch gestures, phone/tablet layouts, and export validated.
- [ ] `.lynx`, `.radiant`, FLP import, SAF, autosave/recovery, and missing-plugin state preservation validated.
- [ ] Foreground-service declarations and audio-focus behavior match current Android requirements.
- [ ] License, notices, bundled assets, and AGPL Corresponding Source obligations reviewed.
- [ ] CI builds ARM64 and runs portable tests; debug artifacts uploaded on CI.
- [ ] Release signing credentials remain in protected secrets and outside the repository.
- [ ] Version, release notes, compatibility, known limitations, and source availability prepared.
- [ ] Repository owner explicitly authorizes publication before any public release is created.
