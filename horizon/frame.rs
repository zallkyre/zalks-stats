// HEAD of native_frame: once a frame, in a world or not.
fn on_frame(game: &mut crate::game::Game<'_>, elapsed: std::time::Duration) {
    let id = crate::game::zalks_stats::MOD_ID;
    if !game.native_on(id) {
        return;
    }
    let (fps, _slowest, low1) = game.native_frame_rate();
    for line in crate::game::zalks_stats::frame(fps, low1, elapsed) {
        crate::game::zalks_stats::emit(game, &line);
    }
}
