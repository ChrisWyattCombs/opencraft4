# Opencraft4

Opencraft4 is a **Minecraft-style open sandbox game** written in Java. The goal is a voxel world you can explore, build in, and survive in — with a custom engine stack:

- **LWJGL** for windowing and **Vulkan** rendering
- **Ultralight** for HTML/CSS/JS menus and HUD

This is an independent fan-style project inspired by classic voxel sandbox gameplay. It is not affiliated with Mojang or Microsoft.

## Requirements

- JDK 21+
- Maven 3.9+
- A Vulkan-capable GPU and drivers
- Ultralight SDK natives under `natives/ultralight-legacy/` (preferred) or `natives/ultralight/`

## Run

From the project root:

```bash
mvn -q compile exec:java
```

Or with an explicit classpath (after `mvn compile` and `mvn dependency:build-classpath -Dmdep.outputFile=cp.txt`):

```bash
java -cp "target/classes;$(Get-Content cp.txt)" opencraft.Main
```

## Feature checklist

### Done

- [x] Main menu (Singleplayer / Multiplayer / Options)
- [x] Singleplayer menu
- [x] Create world menu (world name + seed; Create is wired but does not generate a world yet)

### Planned

- [ ] **World generation** — seeded terrain, biomes, caves, and surface decoration
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
  - [ ] Flight
  - [ ] Instant break
  - [ ] Creative inventory / block picker
- [ ] **Water** — flowing fluids, swimming, drowning, and waterlogged interaction
- [ ] **Inventory** — hotbar, full inventory screen, item stacks, picking up / dropping items

## Art credit

All artwork shown in this project — including UI components, logos, dirt/background textures, and other generated images — is **purely AI-generated**.

## Project structure

| Package / type | Responsibility |
| --- | --- |
| `opencraft.Main` | Application entry and main loop |
| `opencraft.graphics.Display` | Façade over window + Vulkan + BGRA present |
| `opencraft.graphics.GameWindow` | GLFW window lifecycle and resize tracking |
| `opencraft.graphics.VulkanContext` | Vulkan instance, device, surface, queues |
| `opencraft.graphics.BgraFramePresenter` | Swapchain + CPU BGRA frame presentation |
| `opencraft.ui.*` | Ultralight HTML menus and JS bridge |

## Code style

Java sources follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html): naming, one top-level class per file, Javadoc on public APIs, and Google Java Format via Spotless. Apply with:

```bash
mvn -q spotless:apply
```
