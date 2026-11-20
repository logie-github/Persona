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

1. Persona 4 Golden: its session ended 3 seconds after PLAY on the phone (issues #1 to #4); the keeper's log (exit codes, dialogs) says next why.
2. Run Persona 4 Golden on the desktop Wine rig (needs the game's files here), then a phone: copy protection of each title is untested.
3. Gamepad overlay per game; mod manager (GameBanana source, merge step); emulator boxes for 1 and 2.

## Building

`sh scripts/prepare-ppsspp.sh`, `scripts/prepare-winlator.sh`, `scripts/prepare-steamemu.sh` and `scripts/build-keeper.sh` once (it clones the PSP emulator's source at the tag in `ppsspp-version.env` into `build/ppsspp-src` and applies
`patches/ppsspp`), then `./gradlew :backend:test :app:testDebugUnitTest :app:assembleDebug` (JDK 17, Android SDK 35, NDK 29.0.14206865 and CMake 3.22.1
for the emulator's native code; `local.properties` needs `sdk.dir`). `-PpspNative=false` leaves the emulator's native code out for quick checks.
CI (`.github/workflows/build.yml`) does the same and keeps the APK as an artifact.

## App layout

`app/.../ui` (Launcher, Dialogs, Look, SignInDialog), `app/.../reports` and `app/.../game` (the Blizzard launcher's reports, logs and session
keeper), `app/.../install` (InstallCenter state, InstallService, KeystoreLoginStore,
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

The launcher is the Blizzard launcher's (its `ModernHome`, `Launcher`, `HoldMenu`, `SettingsDialog`, `UpdateBanner` and runtime loading screen, ported
to `ui/Launcher.kt`, `ui/Dialogs.kt` and `ui/Look.kt`), in each game's Persona colours:

- **The home.** On the open Fold (600 dp and more) every game is in a list on the left (its box art or numbered box, its name, its versions, a green
  dot when one is installed, the accent's while one installs) and the chosen game's own screen on the right; on the cover screen the game's screen with a
  row of boxes under it (the chosen one raised). The game chosen, and each game's version, are kept between starts.
- **A game's screen** is its own menu (below): its menu words (`MenuSpec`: PLAY, INSTALL or UNINSTALL, VERSION, MODS, OPTIONS), tap a word to choose it,
  tap it again to do it.
- **The games' menus** (`ui/PersonaMenu.kt`), after the user's reference (Persona 5's pause menu over Persona 3): the game's colours with a moving
  background of its own, a slanted panel on the right with the game's character in front of it (breathing), the menu's words stacked and zig-zagging on
  the left in the game's lettering (sized to fit the screen's height), the chosen one lit and moving, the title and versions above, and a line of the
  game's kind at the bottom; everything comes in on a staggered entrance when a game is chosen.
  - Persona 1: violet night, the moon with a glow and a ring of runes turning, squares drifting up, stars; the chosen word on a white band. The moon's
    phase at the bottom (counted in eighths, as Persona 1 and 2 do, from a known new moon).
  - Persona 2: crimson, a jagged sigil turning, slashes of light, ash falling; a black panel with embers; the chosen word on gold; the moon's phase.
  - Persona 3: deep water with light moving through it and bubbles; a white panel with a clock's rings turning, its hands moving and cracks across it;
    slanted rounded lettering, the chosen word on a cyan band with a sweep of light; the date, the day and the time of day (the Dark Hour at midnight).
  - Persona 4: yellow, fog drifting, hazard stripes running; a black TV panel with static, scan lines and a rolling band; the chosen word in a black box
    that jitters like a bad signal; the date and the time of day.
  - Persona 5: red, a sunburst turning, halftone dots; a black panel with stars; every word a ransom note (each letter its own face, box and tilt, fixed
    by the word), the chosen one red and white and shaking; the date in the same letters.
- **Lettering.** The fonts the user named (gamefontlibrary.com's pages for Final Fantasy XV, Persona 3 Reload, Persona 4 Golden and Persona 5) are
  commercial (ITC Avant Garde Gothic, Condensed Runic MT, SKIP Std B, New Rodin Pro, FOT-Rodin Pro, an AsiaFont face) apart from BM Space (Persona 3 Reload's,
  BitmapMania's freeware). The app carries free stand-ins (`res/font`, licences in `assets/licenses/fonts.txt`): TeX Gyre Adventor (an Avant Garde
  clone, GUST licence) for Persona 1 and 2; Rounded M+ 1c ExtraBold and Black (OFL, cut to Latin) for SKIP Std B in Persona 3 and 4; BM Space for Persona
  3's date; Anton, Archivo Black, Abril Fatface, Bowlby One, Rubik Mono One and Bebas Neue (OFL) mixed for Persona 5's ransom notes. A font file the
  person has (the game's own) is set per game in Settings, Games: **Font** (checked by the phone's font loader, kept in `files/fonts/pN.ttf`), and used
  in place of the stand-in (in Persona 5's ransom notes beside the others).
- **Characters.** Settings, Games: **Character** sets a game's picture (best with a transparent background; kept as `files/art/character-pN.png`); else the
  app's own (`assets/characters/pN.png`: Persona 2's is the picture the user sent, 20 November 2026); else a stand-in figure in the game's colours.
- **The accent** (buttons, switches, the list's marks) follows the chosen game's colours (`PersonaColors`); Persona 4's yellow carries black type.
- **More** (or holding a game's row or box) opens its menu: Options, Mods (Persona 1), Box art, Send the log, Uninstall (asks first; saves stay).
- **The gear** opens Settings: **Launcher** (the account signed in, the SteamGridDB key for Box art, Reports: what became of each report and Send the log
  now, the app's version) and **Games** (the patch notes, here and nowhere else, then each version's box art and options).
- **PLAY** on a Windows game shows its loading screen in the game's colours (the runtime's install with its percent, setting up, starting) until the game's
  screen is up; a game that fails, or closes by itself, says so in a pop-up with what became of its report.
- **Updates** download by themselves when the app comes to the front; a banner at the bottom shows the download, then Android's installer.

The Persona 1 skin (`skins/violet`, the WebView page with the girl and the moon) is no longer in the app (the user, 20 November 2026: "Use the persona 2-5
theme and do away with the persona 1 look"); its files stay in the repository. Pictures from the layout test: `LayoutShotsTest` (Robolectric).

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
written to the file named by the `session_log` extra; 0010 the session's keeper (the Blizzard launcher's patch 0009: `exec_wrapper`,
`FileUtils.getDirname` of a bare name, a grabbed pointer's stretch) and the game's session (`game_id`: `GameSession`); 0008 the session's own events in that log (`[persona]` lines: the command started with
its graphics driver, Direct3D layer and Box64 preset, the status its program ended with, the game screen closing); 0009 the player's Exit from
Winlator's menu noted there (a session that ends without it ended by itself). 0001 to 0005 are the Blizzard launcher's.

PLAY (`windows/WindowsRuntime`): installs the runtime the first time (a gauge on the game's screen), makes the games' Windows once (one container,
"Persona", the games' folder as drive D:), finds the game's program (`GameExe`: P4G.exe, P3P.exe, P3R.exe, P5R.exe... else the largest program that isn't an
installer), swaps the game's steam_api libraries for the **Goldberg emulator's** (`SteamEmu`, gbe_fork's Windows release, fetched by
`scripts/prepare-steamemu.sh` into `app/steamemu`; the originals kept as `.orig`), so the game runs without the Steam client, as GameNative does, and starts
Winlator's game screen with its "Virtual Gamepad" on screen. Back opens Winlator's menu (keyboard, controls, exit). A program wrapped by Steam's own DRM
(a `.bind` section) is noted in `files/windows.log`; it may not start without the Steam client.

**The loader (ColdClient).** PLAY starts a store game the way GameNative does by default: the game keeps its own `steam_api64.dll` (a swapped one is
put back from its `.orig`), and gbe_fork's loader (`steamclient_loader_x64.exe`, or `_x86` for a 32-bit program: `ColdClient`) starts it from the games'
Windows at `C:\Program Files (x86)\Steam`, with the emulator's experimental client libraries (`steamclient(64).dll`) where the store's client would be.
Its settings: `ColdClientLoader.ini` (the program as `D:\<folder>\<exe>`, its folder, the app's number) and the emulator's `steam_settings` beside the
libraries (account, SteamID, ticket). Games that check their own library, as Denuvo's do, see theirs: Persona 4 Golden closed by itself 3 seconds after
PLAY with its library swapped (issues #1 and #2). `scripts/prepare-steamemu.sh` puts the loader's files in `assets/steamemu/coldclient` from the
release's `steamclient_experimental`; a build without them swaps the library as before. The app copies the emulator's files out again whenever the
build's differ. Run here (19 November 2026) under Winlator's own Wine (x86_64, natively, Xvfb), with this release's files and the app's settings:
the loader, started on the game desktop (`explorer /desktop=shell,1280x720 C:\Program Files (x86)\Steam\steamclient_loader_x64.exe`), started the
program at `D:\testgame\Game.exe` (a stand-in: no game files on that machine) on the same desktop with no message box, and wrote
`HKCU\Software\Valve\Steam\ActiveProcess` (`SteamClientDll64` = the emulator's `steamclient64.dll`), where a game's own library looks for its client.
Persona 4 Golden itself has not been run that way here.

**The session's keeper.** Every Windows game is started through the Blizzard launcher's keeper, `borderless.exe` (`tools/borderless/borderless.c`,
built with Zig by `scripts/build-keeper.sh` into `app/keeper/assets`; `keeper.env` pins Zig; `game/Borderless`): Winlator's game screen starts it
before `winhandler.exe` in the games' desktop (patch 0010's `exec_wrapper` extra). It makes the games' desktop the one input goes to, takes the title
bars off the game's windows and fits them to the screen, ends the session when no program of the game's is left, and writes its log
(`C:\windows\borderless.log`: each program's start and end with its exit code, the game's windows, a dialog box's words, why the session ended), which
goes into the run's log and the reports. Wine's crash box is off (`WineCrashDialog`): a program that dies of a fault ends, and the app reports it. Run
here under Winlator's Wine with the loader and a stand-in game (a window for five seconds, exit code 7): the keeper logged both programs, the window,
the frame taken off, "program ended: Game.exe (pid 324, code 0x00000007)" and ended the session three seconds later.

**The game's ticket.** Games with Denuvo (Persona 4 Golden, Persona 5 Royal...) check the owner's encrypted app ticket when they start and close without it.
PLAY signs in with the saved sign-in (`steam/GameTickets`), asks for the game's ticket and writes it, with the account's name and SteamID, into the emulator's
`steam_settings/configs.user.ini` (`ticket=`, `account_steamid=`: gbe_fork's settings, as GameNative writes them), beside `configs.main.ini`
(`disable_lan_only=1`). The ticket is fetched at every start (it is short-lived), so PLAY needs the network; without it the game starts with no ticket,
which a Denuvo game refuses. The result (not the ticket) is noted in the run's log.

**Graphics.** The games' Windows is set at every start: on an Adreno GPU (Snapdragon: the Fold's) the Turnip driver with Gladio, and DXVK naming a
desktop card (an AMD Radeon RX 6800 XT, the same at every start), as the Blizzard launcher does; elsewhere Winlator's default, Vortek. Winlator extracts
a changed driver or DXVK by itself.

**Logs.** Wine's `err,seh,loaddll` channels go to the run's log through `GameLog` (Reports and logs, below) and, whole, to
`files/windows-session.log` (the one before kept as `.1`; patches 0007 and 0008). Box64's own log is off (it repeats its settings for every program).

Tested here: the build (Winlator's Java and native code in the app), the program finder, the emulator swap and its settings (`WindowsGamesTest`), the loader's
settings (`ColdClientTest`), the reports (`BugReportsTest`, `SessionLogTest`, `DiagnosticsTest`, `GameLogTest`). The
ticket request has not been run against the service (it needs a signed-in account that owns the game). Not run on a phone:
whether Persona 4 Golden starts and plays under Winlator on the Fold has not been seen.

## Reports and logs

The Blizzard launcher's reports, ported whole (`reports/BugReports`, `SessionLog`, `Diagnostics`, `LogFilter`, `game/GameLog`, `game/GameSession`):

- **The run's log** (`files/logs-app/run-<start>.log`, UTC to the millisecond, a kind on each line): what the app decided (`app`, `windows`), Wine's and
  Box64's lines without the noise (`wine`: a repeated line said three times then counted; a longjmp's or a thread-naming exception's trace dropped),
  the libraries loaded (`lib`), the session keeper's log (`keeper`), memory, processes, heat and battery every few seconds while a game runs (`res`),
  how the last run ended (`exit`), what became of each report (`report`). Written line by line; the last six runs kept.
- **A game's session** (`GameSession`, made by Winlator's game screen with the extra `game_id`, patch 0010): no game window in 90 seconds is a report; a
  minute into the game its log goes once a day; a game that ends by itself (Wine's program ended without the player's Exit) within five minutes or with a
  failing status is a report, and the launcher's pop-up says so with the issue it became. Each carries the game's log (the libraries loaded, Wine's
  lines, the keeper's log: each program's start and end with its exit code, the game's windows, a dialog box's words, why the session ended; the app's
  notes), the phone's state, and as comments the run's log and Android's log of the app.
- **The app crashing**: a Java crash is written as a report as the process ends and sent at the next start, with the run's log; a native crash, an ANR,
  Android ending the app for memory (ApplicationExitInfo) are reported at the next start with the log of the run that ended.
- **PLAY failing** before the game's screen, and an update's download failing: a report with the stack.
- **Send the log**: in a game's menu (More) and in Settings' Reports; a toast says the issue it became.

Each report is written to `files/reports` first and sent at once, then every minute while any wait, kept until GitHub takes it; a long log goes as
several comments, the issue opened once. The same failure goes once a day per build (a new build reports again). A report never names the account
(`<account>`) or the store (its name reads "store": the emulator's libraries appear as `store_api64.dll`), and never carries a key or a token.

The token is built in from CI's **`PERSONA_REPORT_TOKEN`** secret (a fine-grained token with Issues: read and write on this repository only), as
the Blizzard launcher carries `BLIZZ_REPORT_TOKEN`. A build without it keeps its reports on the phone and sends nothing. After the secret is added or
changed, a new build carries it: a push, or **Run workflow** on the Actions tab (`workflow_dispatch`, released the same way).
