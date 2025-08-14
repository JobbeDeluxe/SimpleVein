# SimpleVein

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Server](https://img.shields.io/badge/Paper%2FSpigot-1.20--1.21-blue.svg)](https://papermc.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](#-license)

A tiny, server-side **Paper/Spigot** plugin that adds **Vein Mining** for ores – designed to be **fast**, **safe** (respects protection plugins), and **simple to configure**.

> Default behavior: VeinMining is **always ON**. If a player **sneaks**, VeinMining is **disabled** for that break (invert control).

---

## ✨ Features

- **Vein Mining for ores** (configurable whitelist)
- **Invert control**: VeinMining on by default, **hold Sneak to disable**
- **Tool check** (limit to specific pickaxes; can be disabled)
- **Safety/compatibility**: For each additional block, a normal `BlockBreakEvent` is fired → WorldGuard, GriefPrevention, etc. can cancel as usual
- **Natural breaking** via `breakNaturally(tool)` → respects Fortune/Silk/Unbreaking, drops & XP as usual
- **Performance guard** with `max-blocks` (default 64)
- **Optional diagonals** (off by default)
- Works fine with **Geyser/Floodgate** (server-side only)

Tested with:
- **Paper/Spigot 1.20–1.21**
- **Java 17+**

---

## 📦 Installation

1. Build the plugin JAR (see below) or download your release artifact.
2. Put the JAR into your server’s `plugins/` folder (or your script’s `plugins/manuell/`).
3. Start the server once to generate the config: `plugins/SimpleVein/config.yml`.

---

## ⚙️ Configuration

**`plugins/SimpleVein/config.yml`**

```yml
# SimpleVein – default configuration (VeinMining ON; Sneak = OFF switch)
enabled-by-default: true         # Player has VeinMining enabled unless they toggle it off via /vein
disable-when-sneaking: true      # Holding Sneak disables VeinMining for that break
max-blocks: 64                   # Safety limit per vein
diagonals: false                 # Use 6-neighbors (no diagonals)

allowed-tools:                   # Only these tools trigger VeinMining; leave list empty to allow any tool
  - WOODEN_PICKAXE
  - STONE_PICKAXE
  - IRON_PICKAXE
  - GOLDEN_PICKAXE
  - DIAMOND_PICKAXE
  - NETHERITE_PICKAXE

whitelist:                       # Blocks that vein-mine together (feel free to edit)
  - COAL_ORE
  - IRON_ORE
  - COPPER_ORE
  - GOLD_ORE
  - LAPIS_ORE
  - REDSTONE_ORE
  - DIAMOND_ORE
  - EMERALD_ORE
  - NETHER_QUARTZ_ORE
  - NETHER_GOLD_ORE
  - ANCIENT_DEBRIS
  - DEEPSLATE_COAL_ORE
  - DEEPSLATE_IRON_ORE
  - DEEPSLATE_COPPER_ORE
  - DEEPSLATE_GOLD_ORE
  - DEEPSLATE_LAPIS_ORE
  - DEEPSLATE_REDSTONE_ORE
  - DEEPSLATE_DIAMOND_ORE
  - DEEPSLATE_EMERALD_ORE
```

Tips:
- To include logs/wood too, add e.g. `OAK_LOG`, `BIRCH_LOG` … to the whitelist (but be mindful if you already run a tree feller plugin).
- Increase `max-blocks` to 128 for very large veins, but 64 is a safe default for survival servers.

---

## 🕹 Commands & Permissions

**Command**
```
/vein [toggle|on|off|reload]
```
- `/vein` or `/vein toggle` – toggles VeinMining for yourself
- `/vein on` / `/vein off` – explicit enable/disable
- `/vein reload` – reloads the config (console or players with permission)

**Permissions**
```yaml
simplevein.use: true         # allow players to use /vein and the feature
simplevein.reload: op        # allow /vein reload
simplevein.bypass-limit: op  # ignore max-blocks
```

---

## 🔧 Building from source

The project uses **Maven**. You can build with your local Maven or via Docker (no local Java/Maven needed).

### Option A: Build with Docker (recommended on servers)
```bash
# from the project root
docker run --rm -u $(id -u):$(id -g) -v "$PWD":/src -w /src   maven:3.9-eclipse-temurin-21 mvn -DskipTests package

# Result: target/SimpleVein-<version>.jar
```

### Option B: Local Maven
```bash
mvn -DskipTests package
```

**Dependencies**
- `spigot-api` as `provided`
- Java 17 target

---

## 🧠 How it works (technical)

- When a player breaks a **whitelisted** block with an **allowed tool**, the plugin performs a fast BFS starting from the initial block and gathers adjacent blocks of the same type (optionally 26-neighborhood if `diagonals: true`).
- The **first** block is left to Minecraft itself (vanilla break). Every **additional** block is broken using `breakNaturally(tool)` after emitting a **`BlockBreakEvent`** – allowing other plugins to veto per block (claims/regions/anticheat).
- The loop stops once `max-blocks` is reached (unless the player has `simplevein.bypass-limit`).

---

## ✅ Compatibility Notes

- **Protection plugins** (WorldGuard, GriefPrevention, etc.): respected via `BlockBreakEvent` per block.
- **Enchantments/Drops**: handled by vanilla (`breakNaturally`) – Silk Touch, Fortune, Unbreaking work as expected.
- **Geyser/Floodgate**: fully server-side; Bedrock players benefit too.
- **Performance**: Keep `max-blocks` reasonable (64–128). The set/queue is short-lived and per action.

---

## 📄 License

This project is licensed under the **MIT License** – see [`LICENSE`](LICENSE) for details.

---

## 📜 Changelog

**1.0.1**
- Inverted control: **Sneak disables** VeinMining (`disable-when-sneaking: true`)
- Safer start block handling (first block broken by vanilla, only additional blocks by plugin)
- Minor refactor & docs

**1.0.0**
- Initial release
- Vein Mining for ores with whitelist, tool checks, max-blocks, diagonals
- Protection compatibility via `BlockBreakEvent`

---

## 🤝 Contributing

PRs welcome! Please keep the plugin small and focused. Testing on Paper latest is appreciated.

---

## 🆘 Support

Open an issue with your server version, Java version, config, and a short reproduction clip if possible.
