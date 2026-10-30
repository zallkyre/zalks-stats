# zalk's stats — Horizon native mod

The Horizon build of `zalk's stats`. Same name, same idea: log hardware, FPS,
TPS and world events to a file so bugs are easier to trace.

The Fabric build in the repository root is a separate implementation. They share
the log wording, not the code: Horizon has no JVM, no Fabric API and no mixins
into Java classes.

## What it does

- **FPS** — a warning after 10 s at or below 20 fps, a recovery notice at 30,
  and an average/minimum summary every 300 s.
- **TPS** — real server ticks. 1m / 5m / 15m windows, every 5 s, measured from
  Horizon's authoritative server thread (single player and LAN host included).
- **World** — open, close, and Horizon's own error text when either fails.
- **`/zstats`** — the full report, in chat and in the log.
- **F3 overlay** — three lines (FPS, TPS, RAM) in the bottom corner, **only while
  the debug screen is open**.

Every line also goes to `horizon-mods/.horizon-mod-data/zalks_stats/zalks-stats.log`
next to the game's own log, so a player can attach it to a bug report.

## What it cannot do

- **No JVM statistics.** There is no heap, no GC, no Java version. The report
  shows the process's resident memory instead.
- **No dedicated server.** Horizon does not build native mods for `horizon-server`
  yet; this runs in the client, in single player and as a LAN host.
- **No hardware probe yet.** OSHI does not exist in Rust, so CPU model and load
  need their own platform code. Not in 0.1.0.

## Build

```sh
horizon-launcher native-build horizon --trust-native-code
```

The launcher compiles it with its own Rust on the pinned Nightly and installs the
result as its own instance. The first build downloads the toolchain (~1.7 GB on
Windows) and takes about 8 minutes; later builds take 2 to 3.

`--trust-native-code` is not optional and not something a tool can pass for you:
native mods run with your rights on your computer.

## Pin

`horizon_revision` in `horizon-mixin.toml` is Nightly `2026-10-30` (`587f81f`).
Every native mod in one build must pin the same commit. Only stable hook points
(`native_frame`, `native_server_tick`, `native_world_lifecycle`, `native_command`,
`native_hud`) are hooked, so moving to a later Nightly is a matter of changing
the pin — the hook names do not change.

## Layout

```
horizon-mixin.toml   the mod's id, its pin and its five hooks
zalks_stats.rs       the state, thresholds and log lines
frame.rs             low-FPS warning, recovery, interval summary
server_tick.rs       tick timing for TPS
lifecycle.rs         world open and close
command.rs           /zstats
hud.rs               the F3 overlay
```

Source of the hook API: `crates/horizon-client/src/game/native_hooks.rs` in
`horizon-rust/horizon`, documented in `docs/native-mods.md`.
