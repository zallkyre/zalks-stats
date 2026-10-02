package net.zalks.stats.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.zalks.stats.Settings;
import net.zalks.stats.ZalksStats;

import java.util.Locale;

/**
 * Client entrypoint.
 *
 * <p>Holds the FPS monitor (previously reached through a {@code Class.forName}
 * reflection hack from the common entrypoint) and exposes the Mod Menu config
 * screen. Because this is a real {@code client} entrypoint, nothing here is
 * loaded on a dedicated server and no reflection is needed.
 */
public class ZalksStatsClient implements ClientModInitializer, ModMenuApi {
	private static int lowFpsTicks = 0;
	private static int okFpsTicks = 0;
	private static boolean warned = false;

	private static long summaryStart = System.nanoTime();
	private static long summaryFrames = 0;
	private static long summaryMinFps = Long.MAX_VALUE;

	@Override
	public void onInitializeClient() {
		Settings.load();

		ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));

		ZalksStats.LOGGER.info("client hooks installed (uptime {}s)", ZalksStats.uptimeSeconds());
	}

	private static void tick(Minecraft client) {
		Settings settings = Settings.get();
		if (!settings.logFps) {
			// Reset the counters so re-enabling does not report a bogus average.
			resetSummary();
			warned = false;
			lowFpsTicks = 0;
			okFpsTicks = 0;
			return;
		}

		int fps = client.getFps();
		summaryFrames++;

		if (fps > 0 && fps < summaryMinFps) {
			summaryMinFps = fps;
		}

		// Low-FPS warning: fires once when we cross under the threshold, then
		// stays quiet until the client climbs back above the recovery line.
		// Ticks (not wall-clock) are used so the check cannot drift.
		if (fps <= settings.fpsWarnThreshold) {
			lowFpsTicks++;
			okFpsTicks = 0;
			if (!warned && lowFpsTicks >= 200) {
				warned = true;
				ZalksStats.LOGGER.warn("low fps: {} sustained for 10s (threshold {}). see the hardware report above.",
						fps, settings.fpsWarnThreshold);
			}
		} else {
			okFpsTicks++;
			lowFpsTicks = 0;
			if (warned && okFpsTicks >= 200 && fps >= settings.fpsRecoverThreshold) {
				warned = false;
				ZalksStats.LOGGER.info("fps recovered: {}", fps);
			}
		}

		long elapsedNanos = System.nanoTime() - summaryStart;
		long intervalNanos = settings.fpsSummaryInterval * 1_000_000_000L;
		if (elapsedNanos >= intervalNanos) {
			double seconds = elapsedNanos / 1_000_000_000.0;
			double average = summaryFrames / seconds;
			int min = summaryMinFps == Long.MAX_VALUE ? 0 : (int) summaryMinFps;

			ZalksStats.LOGGER.info("fps summary: avg {} / min {} over {}s",
					String.format(Locale.ROOT, "%.1f", average), min, settings.fpsSummaryInterval);

			resetSummary();
		}
	}

	private static void resetSummary() {
		summaryStart = System.nanoTime();
		summaryFrames = 0;
		summaryMinFps = Long.MAX_VALUE;
	}

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}