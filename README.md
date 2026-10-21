# Ready AI — an Android chat app (built by GitHub Actions)

A tiny Android chat client. It talks to **any OpenAI-compatible endpoint** — a hosted API
(OpenAI, Gemini-compatible gateways, etc.) or your own box running Ollama.

No libraries, no accounts, no tracking. The app is a thin client: the heavy model lives
on a server, your phone just shows the chat. The APK is built in the cloud by GitHub
Actions — no Android Studio needed.

## Get the APK

1. Go to the **Actions** tab → **Build APK** → **Run workflow**.
2. When it finishes (about 2–3 minutes), open the run and download the **ready-ai-apk**
   artifact. It's a zip containing `app-debug.apk`.
3. Copy the APK to your phone and install it (you'll need to allow "install unknown apps"
   for your file manager once).

The debug APK is self-signed, which is fine for personal use.

## Use it

Open the app → **Settings** and fill in three things:

| Field | Example | Notes |
|---|---|---|
| API base URL | `https://api.openai.com/v1` | hosted API |
| | `http://192.168.1.5:8080/v1` | your own box (Ollama) |
| API key | `sk-...` | leave blank for a local box |
| Model | `gpt-4o-mini` or `qwen3:8b` | whatever your endpoint serves |

Then chat. It replies in whatever language/script you write in — Hinglish included.

### Pointing it at your own box

Run [Ollama](https://ollama.com) on any always-on computer (or a free Oracle Cloud VM):

```bash
ollama serve                      # listens on 127.0.0.1:11434
export OLLAMA_HOST=0.0.0.0:11434  # if you want other devices on your Wi-Fi to reach it
ollama pull qwen3:8b
```

Then set the app's base URL to `http://<box-ip>:11434/v1`, key blank, model `qwen3:8b`.
(The app allows cleartext HTTP so it can reach a box on your LAN.)

## What's in here

| File | What it is |
|---|---|
| `app/src/main/java/com/readyai/app/MainActivity.java` | the whole app — UI + the HTTP call |
| `app/src/main/AndroidManifest.xml` | permissions and the activity |
| `app/build.gradle`, `build.gradle`, `settings.gradle` | the Gradle build |
| `.github/workflows/build-apk.yml` | the cloud APK builder |

## Build it yourself

```bash
gradle assembleDebug        # output: app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and the Android SDK (compileSdk 34).

## Limits (honest)

- It is a **client**, not a model. It needs an endpoint — an API or your own box. It does
  not run a model on the phone.
- One shared configuration; no multi-account support.
- Debug-signed build (fine for personal sideloading, not for the Play Store).
- Hinglish quality depends entirely on the model you point it at.
