# JJN Client Architecture

## Overview

JJN Client is split into two main parts:

1. **Launcher** (`launcher/`) – Electron + React application providing the premium Liquid Glass UI.
2. **Client Mod** (`client/`) – Fabric client-side mod that injects HUD, mods, and cosmetics into Minecraft.

## Design Principles

- UI is completely separated from gameplay systems.
- Modular mod system: each feature is a self-contained `Mod` class.
- Settings are persisted to disk.
- HUD elements are independently configurable.

## Launcher Stack

- React 18 + TypeScript
- Vite
- Tailwind CSS
- Framer Motion (animations)
- Electron (desktop shell)

## Client Stack

- Fabric Loader
- Fabric API
- Yarn mappings
- Java 21

## Future Work

- Full 3D skin renderer in the launcher
- Actual Minecraft process launching with selected version + mods
- Cosmetics rendering pipeline
- Config GUI in-game (ImGui or custom)
- Multi-version support via intermediary mappings
