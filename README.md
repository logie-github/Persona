# Persona

An Android launcher for the Persona games, built like the Blizzard launcher (`logie-github/Blizzard`): a shelf of boxes,
a patched Winlator (Wine and Box64) for the Windows games, touch controls, mods, and install and repair handled by the app.
Made for the Galaxy Z Fold.

**Status: launcher shell and installer.** The Android app builds (debug APK, `com.logie.persona`): a shelf of five boxes with
version chooser, a one-time sign-in, and background installs through a foreground service. Not built yet: the Winlator runtime
(Play is disabled) and the app side of the PSP box (the backend and the emulator module are in; see "Persona 1 on the PSP emulator"). Nothing here has run on a phone or an emulator;
the sign-in and downloads have not been tried against a real account.

## The boxes

| Box | Versions | Comes from | Runs on |
| --- | --- | --- | --- |
| Shin Megami Tensei: Persona (Persona 1) | PSP only | your own disc image | PSP emulator |
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

`sh scripts/prepare-ppsspp.sh` once (it clones the PSP emulator's source at the tag in `ppsspp-version.env` into `build/ppsspp-src` and applies
`patches/ppsspp`), then `./gradlew :backend:test :app:testDebugUnitTest :app:assembleDebug` (JDK 17, Android SDK 35, NDK 29.0.14206865 and CMake 3.22.1
for the emulator's native code; `local.properties` needs `sdk.dir`). `-PpspNative=false` leaves the emulator's native code out for quick checks.
CI (`.github/workflows/build.yml`) does the same and keeps the APK as an artifact.

## App layout

`app/.../ui` (Launcher, SignInDialog), `app/.../install` (InstallCenter state, InstallService, KeystoreLoginStore,
Wording: the only text about installs, tested to never name the store).

## Releases and updates

Every push to the default branch (and to `main`, once there is one) builds a release APK, signed with the app's own key, and
publishes it as the GitHub release `persona-build-N` (N is the version code) with `Persona.apk` attached and the patch notes
in its description. The app looks at the releases each time it comes to the front (`update/Updater`, at most every five
minutes), downloads a newer build by itself (resuming after a dropped connection) and opens Android's installer; the first time,
Android asks to allow installing from this app, and each update needs its one tap. Builds install over each other.

## If signing in fails

The sign-in dialog has two tabs. **Approve** shows a QR code (and a button that opens the Steam app on the same phone): approving there finishes the
sign-in with nothing typed (`SignIn.signInWithApproval`). **Account** takes the name and password; if the account has Steam Guard on a phone, the dialog
waits for approval in the Steam app and offers "Use a code instead" (it signs in again asking for the code); email codes are typed in the same way.
This is the same set of ways GameNative offers (QR, password with approval or code); the library is the same (JavaSteam) with SpongyCastle bundled.

Connecting (`openConnection`, `CmDirectory`): web servers on **port 443** first, from the service's own server list (with a built-in list of host names when the
list can't be fetched), six tries of six seconds, then the direct route. The library's own pick often lands on ports 27017 to 27019, which mobile and public
networks block, so it is not used first. When nothing connects, the dialog says how each server failed. The whole story (the library's log lines and the
error's stack) is in the app's private `files/signin.log`. The saved sign-in logs on with a login id of this app's own, so another Steam client on the
same account doesn't push it off.

Run against the real service from the build machine (no account needed): a made-up account is refused by the service itself with `InvalidPassword` and the
approval link comes back (`LiveSteamTest`, with `PERSONA_LIVE_STEAM=1`). Not run on a phone.

## Persona 1's screen

The app opens on it. Back goes to the shelf of boxes; a box opens its game's screen (Persona 2 to 5 have a plain list until their own skins are made).
`ui/SkinScreen` hosts the page in a WebView, served from the app's assets (`skins/` is added to them by `app/build.gradle`) over an https address that exists only
inside the app (`WebViewAssetLoader`); the app sends it the menu rows with `window.Skin.setState` (kept in `window.__pending` until the page has loaded) and the page
calls back `Android.select(i)` and `Android.activate(i)`. The rows are `MenuSpec`'s, built by `ui/GameState`: PLAY, INSTALL or UNINSTALL, MODS, OPTIONS (greyed ones
can't be activated; the chosen word starts on the first usable one). Checked here in a desktop browser with the same address layout and an `Android` stand-in
(screens look as the reference); not run in a phone's WebView.

After the reference picture of the girl with the moon. All of it is in `skins/violet` (the page) and `tools/art` (how her art is made):

- **Her layers** (`tools/art/build_layers_sr.py`): the high-resolution reference is cut into layers by colour and place (body, pendant, face,
  ribbon, one continuous hair layer, a small seam filler), each keeping the picture's own pixels, with edge pixels re-coloured from the inside
  so no old background colour fringes. What the artist's signature and the small figure hid is drawn in by hand as flat shapes in colours
  sampled from the picture (the sleeve and its fold line, the torso, the tie ending in a point), and nothing of her passes the disc's edge.
- **Her animation** (`tools/art/blender/p1_puppet.py`, Blender's bpy under Xvfb): each layer is a gridded mesh on a bone rig in the picture's
  own units; the 4-second loop is keyed from waves that start and end at rest, parts lag the ones they hang from, and the hair curtains, bangs,
  ribbon tails and pendant are baked from spring simulations. `--export-rig` writes every bone's matrix 60 times a second (`girl/rig.json`).
  At rest she matches the picture to about 1 part in 200; the live page matches Blender at moving frames to 0.3 in 255.
- **The page** (`skins/violet/girl.js`, `skin.js`): WebGL2 draws her layers as meshes skinned on the GPU by those matrices, together with the
  moon, its ring and the drifting squares, at the screen's own resolution and frame rate: per frame it writes two small uniform arrays and draws
  about thirty primitives (measured: 1.2 ms of the page's own time per frame, 3 ms at the 95th percentile, against 16.7 ms at 60 fps; not
  measured on a phone). The disc and rim are static SVG; the lettering (opentype.js outlines warped onto quads that follow the rim, the chosen
  word on a skewed white band and gold exactly where the band falls) is redrawn only while it moves.
- **The words** (`backend/.../catalog/MenuSpec.kt`, tested): PLAY (starts the game), INSTALL (UNINSTALL once installed), VERSION (only where a
  box has more than one), MODS (PC versions only), OPTIONS. There is no help text anywhere.

Run the page with any static server and `index.html?mock=1&set=p3&sel=1` (`set=p1` for Persona 1, `t=` to freeze the time). The page talks to
the app through `window.Skin.setState` and the `Android.select` / `Android.activate` bridge. Other games get their own skins the same way.

## Persona 1 on the PSP emulator

The box plays the PSP game (*Shin Megami Tensei: Persona*, ULUS10432) from the person's own disc image (ISO or CSO) through PPSSPP, built from its own
source as the `:ppsspp` module (its Android activity, Java classes, assets and native code; `patches/ppsspp/0001` removes the crash-report
dependency). None of PPSSPP's own screens are shown; its settings are the launcher's OPTIONS.

- **Import** (`backend/.../psp`): `Disc` reads ISO and CSO images, `ParamSfo` reads the game's id and title, `PspDiscs.identify` accepts only Persona 1
  (the other PSP Persona games are refused).
- **Options** (`PspOptions`, `PspSettingsFiles`): sharpness (internal resolution), smoothing, slanted textures, edge smoothing, texture upscaling,
  frame rate, frame skip, Vulkan, mods, sound, touch controls and their opacity, vibration. They are written as PPSSPP's own ini keys (global and
  per game) before each start.
- **Mods** (`backend/.../mods`, like Blizzard's addon importer): `GameBanana` lists, searches and reads the PSP game's mods (game 12961); `ModArchive`
  opens zip and 7z; `ModPlanner` sorts what an archive holds (texture packs, with the variants a `textures.ini` offers; cheats; disc files; EBOOT
  patches in xdelta form, applied by `Vcdiff`); `PspMods` installs, switches and removes them under the game's memory stick
  (`PSP/TEXTURES/<id>/m<modId>/` with a merged `textures.ini`, `PSP/Cheats/<id>.ini`, `mods/m<id>/`) and rebuilds a patched copy of the game from
  the untouched image when a mod changes disc files, so removing a mod restores the original.
- **Touch** (`backend/.../touch/Gestures`, a pure state machine with explicit time): a held swipe anywhere is the d-pad or left stick; one swipe left or
  right is L1 or R1; a held tap is Circle (it combines with a held swipe to run); one tap is Cross; a double tap is Triangle; a swipe in from the right
  edge is Start; a tap followed by a held tap is one press of Square; a tap with two fingers opens the menu below. PPSSPP's own on-screen buttons are turned off.
  The gestures reach the game as a game pad (device 10) pressing Android key codes that `controls.ini` maps to the PSP's buttons (`PspControls`).
- **In the app** (`app/.../psp`): `PspStore` (the disc and its saves under the game's folder; INSTALL copies a chosen ISO or CSO in and checks it is
  Persona 1; UNINSTALL removes the disc and the mods and keeps the saves), `PspLauncher` (writes the memory-stick pointer, `ppsspp.ini`, the game's ini and
  `controls.ini` from the saved OPTIONS, then starts the game), `PspActivity` (the emulator's activity in its own process, `:psp`, so leaving frees all of its
  memory), `TouchOverlay` (the gestures over the whole screen, nothing drawn; the right edge is excluded from Android's back gesture where Android allows),
  `PspMenuView` (the menu). The launcher opens on Persona 1.
- **The menu a two-finger tap opens** (Back opens it too): the game pauses with no emulator screen; rows are RESUME, SAVE STATE, LOAD STATE, then the same OPTIONS
  rows the launcher has (`PspOptionRows`), and EXIT; once an option has been changed a RESTART row appears. Options are saved at once; the game picks up the
  per-game ones on RESTART and the renderer (Vulkan or OpenGL) at the next start from the launcher.
- Nothing of this has run on a phone. The PPSSPP native library builds for arm64 here (about 220 MB unstripped); the gestures, disc reading,
  options and mod planning are unit-tested; the app compiles and links with the emulator's Java and resources. Not verified: the gestures on a real screen, the
  emulator accepting the key codes and the pause key, the restart message, and the edge-swipe exclusion. The MODS screen (`ui/ModsScreen`) lists the most downloaded mods of game 12961 or a search, asks which file or texture pack when there is a choice, downloads, installs, and lists what is installed with a switch and REMOVE; its calls were checked against the live GameBanana API from here (list and profile shapes, e.g. mod 309876 has two zips of about 200 MB), not on a phone.

## The app's icon

`branding/igor.jpg` with a white blocky P in the lower right, as an adaptive icon (`app/src/main/res/drawable-nodpi/ic_launcher_*.png`,
`mipmap-anydpi-v26`) and legacy PNGs. The P stays inside the middle 66% so round masks keep it whole.

## Box art

A long press on a box on the shelf opens BOX ART (`ui/ArtDialogs`): each version of a game has its own picture. SteamGridDB (`backend/.../art/SteamGrid`, ported from Blizzard:
search the game by title and version, a grid of its box pictures, a tap downloads one; only steamgriddb.com addresses are fetched), MY FILES (the phone's picker), then
a crop to the box's 2:3 shape (drag, pinch or slide; `CropMath`, tested) and the result is kept as `files/art/<version>-box.jpg` (the source stays in `art-src` so CROP can adjust it later).
RESET puts the numbered box back. The SteamGridDB key is asked for in the picker the first time and kept in the app's preferences (the KEY button forgets it); it is sent only to steamgriddb.com.
Not yet ported from Blizzard: the music picker, the loading screen maker and the icon picker.
