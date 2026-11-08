# vibepass — Voicefork Android

A fully working **Android APK** port of [Voicefork](https://github.com/dank-zz/voicefork) by dank-zz.

## Features

- **11 DSP effect presets** (Clean, Deep, Chipmunk, Demon, Giant, Robot, Alien, Radio, Megaphone, Cave, 8-bit)
- Noise gate, gain, limiter in the effect chain
- **Live preview**: mic → effect → headphones (real-time processing)
- **Record & Apply**: record your voice, apply the preset, export/share a WAV
- **Soundboard**: import audio clips, play, delete
- **Blend** (DSP): mix two presets into one recording
- Voicefork-style dark UI with tabs: Effects, Soundboard, AI Voices, Blend, Make my voice, About
- Privacy/responsible-use messaging included

## Android-adapted

Desktop Voicefork's real-time RVC AI voices, Hugging Face in-app model workflow, on-device RVC training, system virtual-mic routing (VB-CABLE), and global hotkeys depend on desktop/PyTorch/GPU capabilities and are not fully replicated in this Android build. The AI Voices tab explains this and links to community RVC voices on Hugging Face.

## Build

```sh
sh build-apk.sh android-app
```

Requires: `aapt2`, `javac`, `jar`, `d8`, `apksigner`, `keytool` (install with `android_sdk_install`).

## Install

Download `Voicefork-Android.apk` and install on Android (allow "Install unknown apps" for the source).

## License

MIT. Voicefork Android is not affiliated with Voicemod or Voicefork.
