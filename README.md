# Opencraft4

Opencraft4 is a **Minecraft-style open sandbox game** written in Java. The goal is a voxel world you can explore, build in, and survive in — with a custom engine stack:

- **LWJGL** for windowing and **Vulkan** rendering (GLSL shaders compiled to SPIR-V via Shaderc)
- **Ultralight** for HTML/CSS/JS menus and HUD

This is an independent fan-style project inspired by classic voxel sandbox gameplay. It is not affiliated with Mojang or Microsoft.

## Requirements

- JDK 21+
- Maven 3.9+
- A Vulkan-capable GPU and drivers
- Ultralight SDK natives under `natives/ultralight-legacy/` (preferred) or `natives/ultralight/`

Fetch the Ultralight SDK (revision matching `ultralight-java` 0.4.12):

```bash
./tools/fetch-ultralight-sdk.sh
```

On Apple Silicon, Ultralight’s published macOS binaries are **x86_64 only**. Use a macOS x64 JDK under Rosetta (a Temurin 21 x64 install can live in `.jdk/temurin-21-x64/`) and:

```bash
./tools/run-macos.sh
```

## Run

From the project root (Intel Mac / Windows / Linux):

```bash
mvn -q compile exec:exec
```

On Windows, `exec:java` also works:

```bash
mvn -q compile exec:java
```

Or with an explicit classpath (after `mvn compile` and `mvn dependency:build-classpath -Dmdep.outputFile=cp.txt`):

```bash
java -cp "target/classes;$(Get-Content cp.txt)" opencraft.Main
```

On macOS, pass `-XstartOnFirstThread` when launching `java` directly.
## Controls (in world)

- **WASD** — move
- **Mouse** — look
- **Space / Shift** — jump / sneak (or ascend / descend while flying)
- **F** — toggle fly mode (flight still collides with solid blocks)
- **Escape** — save the world, write `screenshot.png` for the menu icon, return to main menu
- Close the window — also saves and writes `screenshot.png`

Worlds are stored under `%APPDATA%/opencraft/worlds` on Windows, `~/Library/Application Support/opencraft/worlds` on macOS, or `~/opencraft/worlds` elsewhere. The singleplayer list shows each world's last screenshot as its icon when present.

## Feature checklist

### Done

- [x] Main menu (Singleplayer / Multiplayer / Options)
- [x] Singleplayer menu with world list + screenshot icons
- [x] Create world menu (world name + optional seed; duplicate names blocked)
- [x] World generation progress UI
- [x] Seeded Perlin climate biomes (ocean, river, lake, plains, forest, desert, hills)
- [x] Continuous climate (domain-warped Perlin; no square biome grid)
- [x] Blocks: grass, dirt, stone, sand, wood, leaves, water
- [x] Chunks `16×256×16`, region saves under the platform worlds directory (`%APPDATA%/opencraft/worlds/<name>/` on Windows, `~/Library/Application Support/opencraft/worlds/<name>/` on macOS)
- [x] Greedy meshing + async mesh builds + merged region draws
- [x] Vulkan world renderer + GLSL shaders (`world.vert` / `world.frag`)
- [x] Uncapped present when available (`IMMEDIATE` swapchain mode)
- [x] Walk / swim / fly exploration with solid collision (no auto step-up)
- [x] Escape (or close) saves world + screenshot; Escape returns to main menu
- [x] FPS / chunk HUD while playing
- [x] Fog scaled to render distance

### Planned

- [ ] **Placing blocks** — place the selected block against a targeted face
- [ ] **Breaking blocks** — mine / remove targeted blocks with appropriate tools and timing
- [ ] **Survival mode**
  - [ ] Health and hearts
  - [ ] Hunger / food
  - [ ] Player damage (fall, mobs, drowning, void, etc.)
  - [ ] Respawn
  - [ ] Day / night cycle affecting gameplay
- [ ] **Creative mode**
  - [ ] Infinite blocks / free placement
  - [ ] Instant break
  - [ ] Creative inventory / block picker
- [ ] **Water** — flowing fluids, drowning, and waterlogged interaction
- [ ] **Inventory** — hotbar, full inventory screen, item stacks, picking up / dropping items
- [ ] **Caves** — underground generation beyond surface columns
## Art credit

All artwork shown in this project — including UI components, logos, dirt/background textures, block textures, and other generated images — is **purely AI-generated**.

## Project structure

| Package / type | Responsibility |
| --- | --- |
| `opencraft.Main` | Application entry and main loop |
| `opencraft.game.*` | Menu / generate / play phases and session |
| `opencraft.graphics.Display` | Façade over window + Vulkan + BGRA present |
| `opencraft.graphics.render.*` | World Vulkan pipeline, shaders, chunk meshes, atlas |
| `opencraft.world.*` | Blocks, biomes, chunks, regions, Perlin terrain |
| `opencraft.player.Player` | Camera, walk/fly, simple collision |
| `opencraft.ui.*` | Ultralight HTML menus and JS bridge |

## Code style

Java sources follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html): naming, one top-level class per file, Javadoc on public APIs, and Google Java Format via Spotless. Apply with:

```bash
mvn -q spotless:apply
```
