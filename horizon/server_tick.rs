// HEAD of native_server_tick: the authoritative server's own ticks.
fn on_server_tick(game: &mut crate::game::Game<'_>, phase: crate::game::native_lifecycle::ServerTickPhase) {
    let id = crate::game::zalks_stats::MOD_ID;
    if !game.native_on(id) {
        return;
    }
    match phase {
        crate::game::native_lifecycle::ServerTickPhase::ServerTickStart { .. } => {
            crate::game::zalks_stats::tick_start();
        }
        crate::game::native_lifecycle::ServerTickPhase::ServerTickEnd { .. } => {
            if let Some(line) = crate::game::zalks_stats::tick_end() {
                crate::game::zalks_stats::emit(game, &line);
            }
        }
    }
}