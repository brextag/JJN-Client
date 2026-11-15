# JJN Client

A custom **Minecraft Launcher & Built-In Fabric Client Mod Suite** built with Java 25 and JavaFX 27, featuring dual bundled Fabric client mods for **Minecraft `1.21.4`** and **Minecraft `26.2`**, a **Friends & World Hosting Relay**, **Profiles with 3D Skin/Cape/Cosmetic Previews**, **Named Waypoints**, **Quests & JJN Coins (`◈`)**, a **3D Cosmetics Shop**, and an **Emotes Wheel**.

---

## Features

### Launcher (`JJNLauncher`)
- **Microsoft Account Sign-In & Switcher** — Persistent Microsoft OAuth + Xbox Live/Minecraft token caching and multi-account switching stored in `~/.jjnclient/accounts.json`.
- **Instance & Loader Management** — Create and launch **Fabric**, **Quilt**, **Forge**, or Vanilla instances, inspect/install mods from Modrinth, and auto-bundle the matching built-in `jjn-client` mod jar on Fabric `1.21.4` and `26.2`.
- **Profiles & 3D Preview** — Create custom profiles with Skin/Slim variant, custom Capes, and live interactive 3D preview (`SkinPreviewCanvas`), mirrored on the in-game Title Screen.
- **Friends & Live Activity** — Add friends, view what world/server they are currently playing, and configure Friends Relay settings.
- **Shop, Quests & Dedicated Emotes Tab** — Earn **JJN Coins (`◈`)** from Daily & Lifetime Quests, unlock Capes, 3D Wings/Aero-Packs, 3D Headgear, Particle Auras, and 3D Emotes, and bind emotes to your 8-slot wheel.
- **Standalone Windows `.exe` Packaging** — Packages via `jpackage` into `JJN Client.exe` with custom icon and Desktop shortcut support.

### In-Game Client Mods (`:client-mod` for `1.21.4` & `:client-mod-26` for `26.2`)
- **Custom Title Screen & Badges** — JJN-themed front screen with left-side 3D character preview and the JJN Creeper emblem shown before player names in chat, nametags, and the tab list.
- **Customizable HUD (`Right Shift` → `HUD`)** — Toggleable FPS, Coordinates & Direction, Toggle Sprint, Ping & Server, Biome, and Keystrokes HUD with drag-to-position editor, opacity, scale, and accent presets.
- **Named Waypoints (`U`)** — Create, edit, and color-code named waypoints with world markers, beacon beams, distance labels, and off-screen edge arrows.
- **Emotes & 8-Slot Wheel (`B`)** — Trigger 8 animated 3D emotes (*Friendly Wave*, *Commander Salute*, *Applause*, *Ascended T-Pose*, *Facepalm*, *Classic Dab*, *Starlight Spin*, *Breakdance Groove*) with optional auto third-person camera switching.
- **3D Cosmetics & Particle Auras** — Custom Shop Capes, animated 3D Seraph/Dragon Wings & Aero-Pack, Celestial Halo, Monarch Crown, Oni Horns, Kitsune Ears, and live particle auras synced cleanly via `~/.jjnclient/economy.json`.
- **Friends & Pause Menu World Hosting** — Host Singleplayer worlds to friends (`HOST TO FRIENDS` in Pause Menu) with UPnP / Friends Relay TCP tunneling and join friend invites in-game (`[J]`).

---

## Default In-Game Keybinds

| Key | Action |
| --- | --- |
| `Right Shift` | Open **JJN Control Center** (Modules, HUD, Emotes, Shop & Quests, Installed Mods) |
| `U` | Quick-add a **Named Waypoint** at your current coordinates |
| `B` | Open the **8-Slot Emote Wheel** (`1`–`8` or click to perform) |
| `J` | Accept & join the latest incoming **Friend World Invite** toast |

---

## Repository Structure

```text
JJNLauncher/
├── src/                # JavaFX Launcher, Auth, Installers, Profiles, Friends, Shop & Emotes UI
├── client-mod/         # Bundled Fabric client mod for Minecraft 1.21.4 (Yarn mappings)
├── client-mod-26/      # Bundled Fabric client mod for Minecraft 26.2 (Mojang mappings)
├── friends-relay/      # Java 21 TCP & Friends presence/world-tunnel relay server
└── packaging/          # Windows .ico & PowerShell install / Desktop shortcut script
```

---

## Build & Run

### Prerequisites
- **JDK 25** (configured via Gradle Java toolchain; `:client-mod` and `:friends-relay` target Java 21 bytecode).

### Run Launcher from Source
```powershell
.\gradlew.bat run
```

### Build Everything & Package `JJN Client.exe` (Windows)
```powershell
.\gradlew.bat clean build packageExe :friends-relay:installDist --console=plain
```
Install the packaged app image to `%LOCALAPPDATA%\JJN Client\JJN Client.exe` and create the Desktop shortcut:
```powershell
powershell -ExecutionPolicy Bypass -File "packaging\create-desktop-shortcut.ps1"
```

### Run the Friends Relay Server
```powershell
.\friends-relay\build\install\friends-relay\bin\friends-relay.bat
```
*(Main class: `com.jjn.relay.JjnRelayServer`)*

---

## Local Data Locations
All persistent data is stored outside the repository under `%USERPROFILE%\.jjnclient\`:
- `instances/<instance>/saves/` — Singleplayer worlds
- `accounts.json` — Saved Microsoft launcher sessions
- `profiles.json` — Launcher profiles & custom skin/cape selections
- `friends.json` — Friends list & relay configuration
- `economy.json` — JJN Coins (`◈`), unlocked cosmetics/emotes, wheel slots, and quest progress
