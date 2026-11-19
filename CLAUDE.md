# Persona launcher: working rules

Built like `logie-github/Blizzard` (same Kotlin launcher, patched Winlator runtime, shelf, art picker, reset and repair,
recipe numbers, patch notes). Many Blizzard rules carry over; the ones that matter here:

- **Steam is seen once.** The person signs in a single time (name, password, Steam Guard). After that no screen, notification,
  button, status line, log line shown to the player or report names the store: a game says "Install", "Installing", a step and a
  fraction. Downloads and installs run on the backend (`backend/steam`, a foreground service in the app) from the saved
  refresh token. The sign-in screen returns only when the saved sign-in is refused (`SignInRequired`). The password is never kept.
- **Five boxes**: Persona 1 to 5 (`backend/catalog`). Boxes 1 and 2 are not on Steam: the person's own disc images through
  emulators. Persona 3 has Portable and Reload, Persona 5 has Royal, Strikers and Tactica, as version choosers.
- **Mods and touch controls** are first-class: a gamepad overlay per game with saved layouts, and a mod manager that keeps
  original files apart so Reset and Repair restore them.
- `README.md` is the manual: keep it current with every change.
- **A one-line patch note with every change** in `notes/` (`notes/README.md`); shown only on Settings' Games page.
- A change to how a game is installed raises its recipe number (installs again by itself, saves kept).
- Winlator is changed only through `patches/winlator` (Blizzard's way); a game that fails on the phone is reproduced on the
  desktop Wine rig first. Anything that was not run is said so; nothing has run on a real phone unless the user says so.
- Run the tests before pushing: `gradle :backend:test` (a Maven mirror init script helps when Maven Central answers 429).
- Never commit build outputs, downloaded tools or any game files.
- Push to the session's branch; merge into `main` only when the user says so.
