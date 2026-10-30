// HEAD of native_hud: three lines in the bottom corner, only while F3 is open.
use horizon_client::extras::Corner;
use horizon_client::extras_hud::Corners;
use horizon_client::hud::HudBatch;

fn on_hud(
    game: &mut crate::game::Game<'_>,
    batch: &mut HudBatch,
    corners: &mut Corners,
) {
    if !game.debug {
        return;
    }
    let id = crate::game::zalks_stats::MOD_ID;
    if !game.native_on(id) {
        return;
    }

    let (fps, _slowest, low1) = game.native_frame_rate();
    let mspt = crate::game::zalks_stats::current_mspt();
    let rss = crate::game::zalks_stats::rss_mb();
    let lines = crate::game::zalks_stats::overlay(fps, low1, mspt, rss);

    let mut width = 0.0_f32;
    for line in lines.iter() {
        width = width.max(game.native_text_width(line));
    }
    width += 6.0;
    let height = lines.len() as f32 * 10.0 + 4.0;

    let corner = if game.native_corner_shown(Corner::BottomLeft) {
        Corner::BottomLeft
    } else {
        Corner::BottomRight
    };
    let (x, y) = corners.place(corner, width, height);
    game.native_fill(batch, [x - 2.0, y - 2.0, width, height], 0x7800_0000);
    for (i, line) in lines.iter().enumerate() {
        let at = [x + 1.0, y + 1.0 + i as f32 * 10.0];
        game.native_text(batch, line, at, 0x00FF_FF, true);
    }
}
