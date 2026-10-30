// HEAD of native_hud: three lines in the bottom corner, only while F3 is open.
fn on_hud(
    game: &mut crate::game::Game<'_>,
    batch: &mut horizon_client::hud::HudBatch,
    corners: &mut horizon_client::extras_hud::Corners,
) {
    if !game.debug {
        return;
    }
    let id = crate::game::zalks_stats::MOD_ID;
    if !game.native_on(id) {
        return;
    }
    let (fps, slowest, low1) = game.native_frame_rate();
    let mspt = crate::game::zalks_stats::current_mspt();
    let lines = crate::game::zalks_stats::overlay(fps, slowest, low1, mspt);

    let mut width = 0.0_f32;
    for line in lines.iter() {
        width = width.max(game.native_text_width(line));
    }
    width += 6.0;
    let height = lines.len() as f32 * 10.0 + 4.0;

    let corner = if game.native_corner_shown(horizon_client::extras::Corner::BottomLeft) {
        horizon_client::extras::Corner::BottomLeft
    } else {
        horizon_client::extras::Corner::BottomRight
    };
    let (x, y) = corners.place(corner, width, height);
    game.native_fill(batch, [x - 2.0, y - 2.0, width, height], 0x7800_0000);
    for (i, line) in lines.iter().enumerate() {
        let at = [x + 1.0, y + 1.0 + i as f32 * 10.0];
        game.native_text(batch, line, at, 0x00FF_FF, true);
    }
}