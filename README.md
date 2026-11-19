# Persona

An Android launcher for the Persona games, built like the Blizzard launcher (`logie-github/Blizzard`): a shelf of boxes,
a patched Winlator (Wine and Box64) for the Windows games, touch controls, mods, and install and repair handled by the app.
Made for the Galaxy Z Fold.

**Status: launcher shell and installer.** The Android app builds (debug APK, `com.logie.persona`): a shelf of five boxes with
version chooser, a one-time sign-in, and background installs through a foreground service. Not built yet: the Winlator runtime
(Play is disabled), the touch overlay, the mod manager and the emulator boxes. Nothing here has run on a phone or an emulator;
the sign-in and downloads have not been tried against a real account.

## The boxes

| Box | Versions | Comes from | Runs on |
| --- | --- | --- | --- |
| Persona | PSP, PS1 | your own disc image | emulator |
| Persona 2 | Innocent Sin (PSP), Eternal Punishment (PS1) | your own disc image | emulator |
| Persona 3 | Portable, Reload | downloaded by the app | Winlator |
| Persona 4 | Golden | downloaded by the app | Winlator |
| Persona 5 | Royal, Strikers, Tactica | downloaded by the app | Winlator |

## Signing in once, installing on the backend

The first time a downloaded game is installed the app asks for the account's name, password and Steam Guard approval, once.
It keeps a refresh token (never the password). From then on **Install** does everything in the background: it signs in with
the token, checks the account has the game, downloads the Windows files (resumable, verified) and writes them to the game's
folder, showing only the game's name, a step and a progress bar. The store is named nowhere else. If the saved sign-in is
ever refused, the sign-in screen comes back once.

Code: `backend/src/main/kotlin/com/logie/persona/backend/steam` (JavaSteam 1.8.0 and its depot downloader).
`SignIn` (one time), `Installer` (headless), `LoginStore` (the app wraps it with encrypted storage).

## Next

1. Bring in the Winlator runtime through `patches/winlator` (Blizzard's way) and make Play start the game.
2. Run Persona 4 Golden on the desktop Wine rig, then a phone: copy protection of each title is untested.
3. Gamepad overlay per game; mod manager (GameBanana source, merge step); emulator boxes for 1 and 2.

## Building

`./gradlew :backend:test :app:testDebugUnitTest :app:assembleDebug` (JDK 17, Android SDK 35; `local.properties` needs `sdk.dir`).
CI (`.github/workflows/build.yml`) does the same and keeps the debug APK as an artifact.

## App layout

`app/.../ui` (Launcher, SignInDialog), `app/.../install` (InstallCenter state, InstallService, KeystoreLoginStore,
Wording: the only text about installs, tested to never name the store).
