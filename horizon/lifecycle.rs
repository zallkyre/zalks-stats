// HEAD of native_world_lifecycle: open and close, with the engine's own error text.
use crate::game::native_lifecycle::WorldPhase;

fn on_world(game: &mut crate::game::Game<'_>, phase: WorldPhase, error_text: &str) {
    let id = crate::game::zalks_stats::MOD_ID;
    if !game.native_on(id) {
        return;
    }
    let line = match phase {
        WorldPhase::WorldOpenReady => format!(
            "world: open ({}s uptime)",
            crate::game::zalks_stats::uptime().as_secs()
        ),
        WorldPhase::WorldOpenFailed => format!("world: open failed: {}", error_text),
        WorldPhase::WorldCloseFinal => format!(
            "world: closed ({}s uptime)",
            crate::game::zalks_stats::uptime().as_secs()
        ),
        WorldPhase::WorldCloseFailed => format!("world: close failed: {}", error_text),
    };
    crate::game::zalks_stats::emit(game, &line);
}
