# zalk's stats

A Fabric mod that logs hardware, performance, and player stats to the game log.

## What it logs

At startup:

- OS name, version, architecture
- Java version
- CPU model, physical and logical core count, current load
- RAM total and used
- GPU name and VRAM
- Disk free and total space per drive
- Minecraft version, Fabric loader version, loaded mod count

Client side:

- Warning when FPS drops below 20 (logged once, not spammed)
- Notice when FPS recovers above 30
- Average FPS every 5 minutes
- JVM heap usage every 5 minutes

Server side:

- TPS every 5 seconds, warning below 18
- Player join and leave events
- Server start message
- Level load message (mixin)

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Java 25
- Fabric API

## Installation

Place `zalks-stats-1.3.0.jar` in your `mods` folder and launch the game.

## Building

Clone the repository, then run the Gradle wrapper:

    git clone https://github.com/zallkyre/zalks-stats.git
    cd zalks-stats
    gradlew build          (Windows)
    ./gradlew build        (Linux/macOS)

The built jar appears in `build/libs/`. Java 25 is required.

## License

Released into the public domain under the Unlicense. See the LICENSE file.