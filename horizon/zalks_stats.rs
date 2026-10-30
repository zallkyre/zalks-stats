// zalk's stats for Horizon: state, thresholds and the lines it writes.
// Injected as `crate::game::zalks_stats` (docs/native-mods.md).

use std::collections::VecDeque;
use std::sync::Mutex;
use std::time::{Duration, Instant};

pub const MOD_ID: &str = "zalks_stats";
pub const VERSION: &str = "0.1.0";

pub const WARN_BELOW: u32 = 20;
pub const RECOVER_AT: u32 = 30;
pub const WARN_AFTER: Duration = Duration::from_secs(10);
pub const FPS_INTERVAL: Duration = Duration::from_secs(300);
pub const TPS_INTERVAL: Duration = Duration::from_secs(5);

/// Ticks kept for the TPS windows: 15 minutes at 20 ticks a second.
pub const TPS_WINDOW: usize = 18_000;

struct State {
    low_for: Duration,
    warned: bool,
    fps_sum: u64,
    fps_count: u64,
    fps_min: u32,
    last_fps: u32,
    last_low: u32,
    fps_next: Instant,
    tps_next: Instant,
    tps_samples: VecDeque<f64>,
    started: Instant,
}

static STATE: Mutex<Option<State>> = Mutex::new(None);

fn with<R>(f: impl FnOnce(&mut State) -> R) -> R {
    let mut guard = STATE.lock().unwrap_or_else(|poison| poison.into_inner());
    let state = guard.get_or_insert_with(|| {
        let now = Instant::now();
        State {
            low_for: Duration::ZERO,
            warned: false,
            fps_sum: 0,
            fps_count: 0,
            fps_min: u32::MAX,
            last_fps: 0,
            last_low: 0,
            fps_next: now + FPS_INTERVAL,
            tps_next: now + TPS_INTERVAL,
            tps_samples: VecDeque::with_capacity(TPS_WINDOW),
            started: now,
        }
    });
    f(state)
}

/// A line of the mod's log: the game's stderr and its own file.
pub fn emit(game: &mut crate::game::Game<'_>, line: &str) {
    eprintln!("[{}] {}", MOD_ID, line);
    let dir = game.native_data_dir(MOD_ID);
    let _ = std::fs::create_dir_all(&dir);
    if let Ok(mut file) = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(dir.join("zalks-stats.log"))
    {
        use std::io::Write;
        let _ = writeln!(file, "{}", line);
    }
}

/// Once a frame: the low-FPS warning, its recovery, and the interval summary.
pub fn frame(fps: u32, low1: u32, elapsed: Duration) -> Vec<String> {
    let mut out = Vec::new();
    with(|s| {
        s.last_fps = fps;
        s.last_low = low1;
        s.fps_sum += u64::from(fps);
        s.fps_count += 1;
        s.fps_min = s.fps_min.min(fps);

        if fps <= WARN_BELOW {
            s.low_for += elapsed;
            if s.low_for >= WARN_AFTER && !s.warned {
                s.warned = true;
                out.push(format!(
                    "fps: at or below {} fps for {}s (now {}, 1% low {})",
                    WARN_BELOW,
                    s.low_for.as_secs(),
                    fps,
                    low1
                ));
            }
        } else {
            s.low_for = Duration::ZERO;
            if s.warned && fps >= RECOVER_AT {
                s.warned = false;
                out.push(format!("fps: recovered to {} (1% low {})", fps, low1));
            }
        }

        let now = Instant::now();
        if now >= s.fps_next {
            let average = if s.fps_count == 0 {
                0
            } else {
                (s.fps_sum / s.fps_count) as u32
            };
            let minimum = if s.fps_min == u32::MAX { 0 } else { s.fps_min };
            out.push(format!("fps: average {}, minimum {}", average, minimum));
            s.fps_sum = 0;
            s.fps_count = 0;
            s.fps_min = u32::MAX;
            s.fps_next = now + FPS_INTERVAL;
        }
    });
    out
}

/// Once a server tick ends, with how long it took: the TPS report, on its interval.
pub fn tps(mspt: f64) -> Option<String> {
    with(|s| {
        if s.tps_samples.len() == TPS_WINDOW {
            s.tps_samples.pop_front();
        }
        s.tps_samples.push_back(mspt);

        let now = Instant::now();
        if now < s.tps_next {
            return None;
        }
        s.tps_next = now + TPS_INTERVAL;
        Some(format!(
            "tps: 1m={} 5m={} 15m={}",
            window_tps(&s.tps_samples, 1_200),
            window_tps(&s.tps_samples, 6_000),
            window_tps(&s.tps_samples, TPS_WINDOW)
        ))
    })
}

/// The mean milliseconds per tick of the last `ticks` ticks, as ticks a second.
fn window_tps(samples: &VecDeque<f64>, ticks: usize) -> String {
    if samples.is_empty() {
        return "20.00".to_owned();
    }
    let take = ticks.min(samples.len());
    let sum: f64 = samples.iter().rev().take(take).sum();
    let average = sum / take as f64;
    let tps = if average <= 0.0 {
        20.0
    } else {
        (1_000.0 / average).min(20.0)
    };
    format!("{:.2}", tps)
}

/// The last tick's mean milliseconds, for the overlay and the report.
pub fn current_mspt() -> f64 {
    with(|s| {
        if s.tps_samples.is_empty() {
            0.0
        } else {
            s.tps_samples.iter().sum::<f64>() / s.tps_samples.len() as f64
        }
    })
}

pub fn uptime() -> Duration {
    with(|s| s.started.elapsed())
}

pub fn last_fps() -> (u32, u32) {
    with(|s| (s.last_fps, s.last_low))
}

/// The three lines of the F3 overlay.
pub fn overlay(fps: u32, low1: u32, mspt: f64, rss_mb: f64) -> [String; 3] {
    let tps = if mspt <= 0.0 {
        20.0
    } else {
        (1_000.0 / mspt).min(20.0)
    };
    [
        format!("zalk's stats  fps {}  low {}", fps, low1),
        format!("tps {:.2}", tps),
        format!("ram {:.0} MB", rss_mb),
    ]
}

/// The `/zstats` report.
pub fn report(fps: u32, low1: u32, mspt: f64, rss_mb: f64) -> Vec<String> {
    let tps = if mspt <= 0.0 {
        20.0
    } else {
        (1_000.0 / mspt).min(20.0)
    };
    vec![
        format!("zalk's stats {} (Horizon)", VERSION),
        format!("uptime: {}s", uptime().as_secs()),
        format!("fps: {} (1% low {})", fps, low1),
        format!("tps: {:.2} ({:.2} mspt)", tps, mspt),
        format!("ram: {:.0} MB", rss_mb),
    ]
}

/// The process's resident memory, the closest thing to a heap figure here.
pub fn rss_mb() -> f64 {
    platform_rss().unwrap_or(0) as f64 / (1024.0 * 1024.0)
}

#[cfg(windows)]
fn platform_rss() -> Option<u64> {
    #[repr(C)]
    struct ProcessMemoryCounters {
        cb: u32,
        page_fault_count: u32,
        peak_working_set_size: usize,
        working_set_size: usize,
        quota_peak_paged_pool_usage: usize,
        quota_paged_pool_usage: usize,
        quota_peak_non_paged_pool_usage: usize,
        quota_non_paged_pool_usage: usize,
        pagefile_usage: usize,
        peak_pagefile_usage: usize,
        private_usage: usize,
    }

    extern "system" {
        fn GetCurrentProcess() -> isize;
        fn K32GetProcessMemoryInfo(
            process: isize,
            counters: *mut ProcessMemoryCounters,
            size: u32,
        ) -> i32;
    }

    let mut counters: ProcessMemoryCounters = unsafe { std::mem::zeroed() };
    counters.cb = std::mem::size_of::<ProcessMemoryCounters>() as u32;
    let size = counters.cb;
    let ok = unsafe { K32GetProcessMemoryInfo(GetCurrentProcess(), &mut counters, size) };
    (ok != 0).then_some(counters.working_set_size as u64)
}

#[cfg(target_os = "linux")]
fn platform_rss() -> Option<u64> {
    let status = std::fs::read_to_string("/proc/self/statm").ok()?;
    let pages: u64 = status.split_whitespace().nth(1)?.parse().ok()?;
    Some(pages * 4096)
}

#[cfg(target_os = "macos")]
fn platform_rss() -> Option<u64> {
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tps_is_clamped_and_rounded() {
        let mut samples = VecDeque::new();
        samples.push_back(50.0);
        assert_eq!(window_tps(&samples, 1_200), "20.00");
        samples.push_back(100.0);
        assert_eq!(window_tps(&samples, 1_200), "13.33");
    }

    #[test]
    fn the_overlay_is_three_lines() {
        let lines = overlay(144, 91, 50.0, 2048.0);
        assert_eq!(lines.len(), 3);
        assert!(lines[0].contains("fps 144"));
        assert!(lines[1].starts_with("tps "));
        assert!(lines[2].contains("2048"));
    }

    #[test]
    fn the_report_names_the_version() {
        let lines = report(60, 45, 50.0, 1024.0);
        assert!(lines[0].contains(VERSION));
    }
}
