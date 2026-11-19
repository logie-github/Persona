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

**Why it failed on the phone** (builds up to 17): the sign-in screen started the sign-in on Android's main thread, and the library looks up Steam's servers (a web
request, then a name lookup per server) on the thread that connects. Android refuses network use on the main thread (NetworkOnMainThreadException); the library
caught that and was left with no servers, which is the "No CM servers available to connect to" the phone showed (the desktop has no such rule, which is why it
signed in from here). GameNative notes the same thing beside its own connect call. `SignIn` and `openConnection` now move all of their work to the IO threads,
whoever calls them. The sign-in also names the device ("Persona on <model>") and presents itself as a Windows client, as GameNative's does.

**"Failed to load QR info" in the Steam app** (build 18): approving on the same phone means leaving this app for the Steam app, and Android freezes an app that
has left the screen with nothing running in the foreground; the sign-in's connection went quiet and Steam dropped the session before the Steam app could read it.
The sign-in now runs in `InstallCenter`'s own scope (not the screen's) and `SignInService` holds the app in the foreground with a quiet "Signing in" notification
until the sign-in ends (approved, refused, cancelled), as GameNative's Steam service does. The same keeps a password sign-in alive while it waits for approval in
the Steam app. Tested: the flow and the service under Robolectric (`SignInFlowTest`); not on a phone.

Connecting (`openConnection`, `CmDirectory`): web servers on **port 443** first, from the service's own server list (with a built-in list of host names when the
list can't be fetched), six tries of six seconds, then the direct route. The library's own pick often lands on ports 27017 to 27019, which mobile and public
networks block, so it is not used first. When nothing connects, the dialog says how each server failed. The whole story (the library's log lines and the
error's stack) is in the app's private `files/signin.log`. The saved sign-in logs on with a login id of this app's own, so another Steam client on the
same account doesn't push it off.

Run against the real service from the build machine (no account needed): a made-up account is refused by the service itself with `InvalidPassword` and the
approval link comes back (`LiveSteamTest`, with `PERSONA_LIVE_STEAM=1`). Not run on a phone.

## The layout

On the open Fold (600 dp wide and more) the app is laid out as the Blizzard launcher is: every game in a list on the left (its box art or numbered box, its
name, its versions, a green dot when one is installed, blue while one installs; a long press opens BOX ART) and the chosen game's own screen on the right.
Persona 1's screen is its skin (below); Persona 2 to 5 have themed screens in the game's colours until their own skins are made (Persona 2 crimson, 3 blue,
4 yellow, 5 red and black: the name large and slanted, the version chips, the menu words with the chosen one on a band, the box's number huge and faint
behind). On the narrow cover screen the app opens on Persona 1 and Back goes to the shelf of boxes, as before. OPTIONS for a Windows game: the game's screen
size and the on-screen gamepad (`WindowsOptions`). Pictures from the layout test: `LayoutShotsTest` (Robolectric; the WebView of Persona 1's skin draws
nothing there).

## Persona 1's screen

The app opens on it on the narrow screen; on the open Fold it is the right-hand side when Persona 1 is chosen.
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
- **Where the face meets the hair** (`refine_head` in the same script, after the user's marks on the phone's recording): the picture's lavender glow along each
  strand is moved from the face to the hair layer, unmixed over the face's own flat colour so the still picture is unchanged, and the face under and near the hair
  is that flat colour only. When the bangs move, the glow goes with them (before, it stayed on the face as a second outline, with dark slivers between). The black
  wedge between the left curtain and the collar is hair now (it broke into black and white pieces as the curtain swung), light specks at the strands' tips take the
  hair's colour, and the pendant's cord stays as drawn. Checked by rendering the live page at eight moments of the loop before and after; not on a phone.
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
  Checked against real archives from the site (`LiveModsTest`, `RealModsTest`): packs of pictures named by their texture hash with no textures.ini (one folder per
  choice, e.g. the PS5 buttons) are offered as choices and listed in the merged textures.ini (PPSSPP only finds hash-named files by itself in the texture folder's
  own root, and refuses an ini that names no hash type, so the merged one always does); a mod made for several releases (folders or file names marked US, PAL, JP)
  keeps only this disc's files; an xdelta patch goes to the disc file of the same name (`SE_BOX.xdelta` to `SE_BOX.*`, `ENDALL_Patch_US.xdelta` to `ENDALL.*`), not
  only to EBOOT.BIN. RAR archives (RAR5, about one file in twelve on the site) can't be opened yet: where a mod also has a zip or 7z, that is offered instead.
  Loose pictures with names of their own and raw game files (`.gim`) have no place without the author's instructions and are listed as skipped.
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

## The Windows games (Persona 3, 4, 5): PLAY

Installed through the account (above), they run on **Winlator** (Wine, Box64, its graphics drivers and its X server), built into the app the Blizzard
launcher's way: `scripts/prepare-winlator.sh` checks out Winlator's commit (`winlator-version.env`) into `build/winlator-app`, applies `patches/winlator`
and copies it to `app/winlator` (git-ignored), rewriting its runtime's install path for this app (`/data/data/com.logie.persona/winroot`, the same length as
Winlator's own). Its Java is compiled into the app module with the app's R; its native code with the app's CMake (the NDK's newer errors for old C are
warnings again: `cFlags` in `app/build.gradle`); its prebuilt C++ runtime is made the NDK's own so it is the same file as the PSP emulator's.

Patches (`patches/winlator`): 0001 the runtime in the app's data folder and leaving the game returns to the launcher; 0002 resource ids as variables (the
app's R isn't final); 0003 the runtime installed without Winlator's screens; 0004 the program's arguments apart from its path; 0005 no window picture without
a GL context (a crash fix); 0006 a game started with on-screen controls (`controls_profile`) and full-screen windows (`force_fullscreen`); 0007 the session's programs' output (Wine, Box64, the game)
written to the file named by the `session_log` extra; 0008 the session's own events in that log (`[persona]` lines: the command started with
its graphics driver, Direct3D layer and Box64 preset, the status its program ended with, the game screen closing); 0009 the player's Exit from
Winlator's menu noted there (a session that ends without it ended by itself). 0001 to 0005 are the Blizzard launcher's.

PLAY (`windows/WindowsRuntime`): installs the runtime the first time (a gauge on the game's screen), makes the games' Windows once (one container,
"Persona", the games' folder as drive D:), finds the game's program (`GameExe`: P4G.exe, P3P.exe, P3R.exe, P5R.exe... else the largest program that isn't an
installer), swaps the game's steam_api libraries for the **Goldberg emulator's** (`SteamEmu`, gbe_fork's Windows release, fetched by
`scripts/prepare-steamemu.sh` into `app/steamemu`; the originals kept as `.orig`), so the game runs without the Steam client, as GameNative does, and starts
Winlator's game screen with its "Virtual Gamepad" on screen. Back opens Winlator's menu (keyboard, controls, exit). A program wrapped by Steam's own DRM
(a `.bind` section) is noted in `files/windows.log`; it may not start without the Steam client.

**The loader (ColdClient).** PLAY starts a store game the way GameNative does by default: the game keeps its own `steam_api64.dll` (a swapped one is
put back from its `.orig`), and gbe_fork's loader (`steamclient_loader_x64.exe`, or `_x32` for a 32-bit program: `ColdClient`) starts it from the games'
Windows at `C:\Program Files (x86)\Steam`, with the emulator's experimental client libraries (`steamclient(64).dll`) where the store's client would be.
Its settings: `ColdClientLoader.ini` (the program as `D:\<folder>\<exe>`, its folder, the app's number) and the emulator's `steam_settings` beside the
libraries (account, SteamID, ticket). Games that check their own library, as Denuvo's do, see theirs: Persona 4 Golden closed by itself 3 seconds after
PLAY with its library swapped (issues #1 and #2). `scripts/prepare-steamemu.sh` puts the loader's files in `assets/steamemu/coldclient` from the
release's `steamclient_experimental`; a build without them swaps the library as before. The app copies the emulator's files out again whenever the
build's differ.

**The game's ticket.** Games with Denuvo (Persona 4 Golden, Persona 5 Royal...) check the owner's encrypted app ticket when they start and close without it.
PLAY signs in with the saved sign-in (`steam/GameTickets`), asks for the game's ticket and writes it, with the account's name and SteamID, into the emulator's
`steam_settings/configs.user.ini` (`ticket=`, `account_steamid=`: gbe_fork's settings, as GameNative writes them), beside `configs.main.ini`
(`disable_lan_only=1`). The ticket is fetched at every start (it is short-lived), so PLAY needs the network; without it the game starts with no ticket,
which a Denuvo game refuses. The result (not the ticket) is noted in `files/windows.log`.

**Graphics.** The games' Windows is set at every start: on an Adreno GPU (Snapdragon: the Fold's) the Turnip driver with Gladio, and DXVK naming a
desktop card (an AMD Radeon RX 6800 XT, the same at every start), as the Blizzard launcher does; elsewhere Winlator's default, Vortek. Winlator extracts
a changed driver or DXVK by itself.

**Logs.** Each start writes `files/windows-session.log` (the one before kept as `.1`): Wine's `err,seh,loaddll` channels (patch 0007) and the session's
own events (patch 0008), beside the app's notes (`files/windows.log`) and any crash of the app (`files/crash.log`, `PersonaApp`). Box64's own log is
off (it repeats its settings for every program). When there are logs, the game's screen shows **SHARE LOG**: a short digest as text that pastes whole
(`LogDigest`: Box64's settings, Windows' own libraries and Wine's exception registers left out, Wine's own RPC_S_SERVER_UNAVAILABLE at start
once, repeats counted, the end kept), how Android says the app's last runs ended
(ApplicationExitInfo: a native crash with its tombstone's readable words, the system ending it, memory), and the full logs as a file beside it.

Tested here: the build (Winlator's Java and native code in the app), the program finder, the emulator swap and its settings (`WindowsGamesTest`), the log digest (`LogDigestTest`). The
ticket request has not been run against the service (it needs a signed-in account that owns the game). Not run on a phone:
whether Persona 4 Golden starts and plays under Winlator on the Fold has not been seen.

## Reports: crashes sent as issues

The app's failures go to this repository as GitHub issues by themselves (labels `bug`, `auto-report`), as the Blizzard launcher's do (`reports/Reports`,
`backend/reports/Issues`):

- **A pop-up** on the launcher, as the Blizzard launcher shows a failed start: what happened ("Persona 4 Golden closed while starting (3 seconds after
  PLAY)") and what became of the report ("A report with its log was sent (issue #2)", or why not yet), with OK and SHARE LOG.
- **A Windows game's session that ends by itself**: Winlator's game screen closes, its program ended (patch 0008) without the player's Exit (patch
  0009), within five minutes of PLAY or with a failing status. The issue holds SHARE LOG's short report (notes, how Android says the app ended, the
  session log's digest) and, as comments, the end of the session's whole log. The game's screen says "It closed while starting" and that a report goes.
- **The app crashing**: a Java crash (its stack, written as the process ends, sent at the next start); a native crash, an ANR, Android ending the app
  (ApplicationExitInfo, read at the next start: a native crash's tombstone as its readable words), with the Windows logs when a game's session ran
  just before.
- **PLAY failing** before the game's screen (the runtime, the games' Windows, the game's program): its stack.

Each is written to `files/reports` first and sent at once, then every minute while any wait (offline, GitHub busy), kept until GitHub takes it; a
step that failed goes on from where it stopped (the issue is opened once). The same failure of the same build goes once a day; a new build reports
again. A report never names the account (its name is replaced by `<account>`) or the store (its name reads "store": the emulator's libraries appear
as `store_api64.dll`); SHARE LOG's text and file are the same.

The token is built in from CI's **`PERSONA_REPORT_TOKEN`** secret (a fine-grained token with Issues: read and write on this repository only), as
the Blizzard launcher carries `BLIZZ_REPORT_TOKEN`. A build without it keeps its reports on the phone and sends nothing; SHARE LOG still works.
After the secret is added or changed, a new build carries it: a push, or **Run workflow** on the Actions tab (`workflow_dispatch`, released the same way).
