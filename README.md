# zalk's stats

A Fabric mod that logs hardware, performance, and player events to the game log, so bugs and performance problems are easier to trace.

## What it logs

At startup, once:

- Environment type (client or server) and mod version
- Fabric Loader version
- Java version and VM name
- OS name, version, and architecture
- Available processors
- JVM memory used vs max
- CPU model, physical/logical core count, and a 1-second CPU load sample
- Total and available RAM

The hardware probe runs on a background daemon thread, so the one-second CPU load sample no longer stalls game start-up.

Client only:

- Low-FPS warning when FPS stays at or below your threshold for 10 seconds. Logged once, not spammed.
- Recovery notice once FPS climbs back to your recovery threshold.
- FPS summary (average and minimum) on your chosen interval.

Server only:

- TPS 1m / 5m / 15m on your chosen interval
- JVM heap usage with per-report delta, on a dedicated server
- Player join and disconnect events
- Server start and stop, with uptime

## Configuration

Settings live in `.minecraft/config/zalks-stats.json` and can be edited in-game from the Mod Menu screen. Mod Menu is suggested, not required.

| Setting | Options | Default |
| --- | --- | --- |
| Low-FPS warning threshold | 10-90 FPS | 20 |
| FPS recovery threshold | 10-120 FPS | 30 |
| FPS summary interval | 60, 120, 300, 600, 900 s | 300 |
| TPS interval | 1, 2, 5, 10, 20, 30 s | 5 |
| Heap interval | 60, 120, 300, 600, 900 s | 300 |
| Log toggles | hardware, environment, FPS, TPS, heap, player join/leave, server lifecycle | all on |

Values are clamped on load and save, so a hand-edited or corrupted config can never leave the mod in a nonsensical state. The recovery threshold is always kept above the warning threshold, so the warning cannot immediately re-arm itself.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Java 25 or newer
- Fabric API
- Mod Menu (optional, for the settings screen)

Works on both the client and a dedicated server. Client-only code is not loaded on a server.

## Installation

1. Install Fabric Loader and Fabric API for Minecraft 26.3.
2. Download `zalks-stats-1.4.2.jar`.
3. Put it in your `mods` folder.
4. Launch the game.

## Building

    git clone https://github.com/zallkyre/zalks-stats.git
    cd zalks-stats
    ./gradlew build

The built jar appears in `build/libs/`. Java 25 or newer is required.

## License

Released into the public domain. See the LICENSE file.