// HEAD of native_command: `/zstats` prints the report in chat and the log.
fn on_command(game: &mut crate::game::Game<'_>, command: &str, ran: &mut bool) {
    let trimmed = command.trim();
    if trimmed != "zstats" && !trimmed.starts_with("zstats ") {
        return;
    }
    *ran = true;

    let id = crate::game::zalks_stats::MOD_ID;
    if !game.native_on(id) {
        game.native_chat("zalk's stats is turned off in Options > Mods");
        return;
    }

    let (fps, slowest, low1) = game.native_frame_rate();
    let mspt = crate::game::zalks_stats::current_mspt();
    for line in crate::game::zalks_stats::report(fps, low1, slowest, mspt) {
        game.native_chat(&line);
        crate::game::zalks_stats::emit(game, &line);
    }
}
