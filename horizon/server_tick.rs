// HEAD of native_server_tick: the authoritative server's own ticks.
use crate::game::native_lifecycle::ServerTickPhase;
use std::sync::Mutex;
use std::time::Instant;

static LAST_START: Mutex<Option<Instant>> = Mutex::new(None);

fn on_server_tick(game: &mut crate::game::Game<'_>, phase: ServerTickPhase) {
    let id = crate::game::zalks_stats::MOD_ID;
    if !game.native_on(id) {
        return;
    }
    let mut slot = LAST_START
        .lock()
        .unwrap_or_else(|poison| poison.into_inner());
    let mspt = match phase {
        ServerTickPhase::ServerTickStart { .. } => {
            *slot = Some(Instant::now());
            return;
        }
        ServerTickPhase::ServerTickEnd { .. } => match slot.take() {
            Some(started) => started.elapsed().as_secs_f64() * 1_000.0,
            None => return,
        },
    };
    drop(slot);
    if let Some(line) = crate::game::zalks_stats::tps(mspt) {
        crate::game::zalks_stats::emit(game, &line);
    }
}
