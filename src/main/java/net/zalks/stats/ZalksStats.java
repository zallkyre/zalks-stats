package net.zalks.stats;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Common entrypoint. Everything here is safe to load on both the client and a
 * dedicated server.
 *
 * <p>Client-only concerns (FPS monitoring and the Mod Menu screen) live in
 * {@code net.zalks.stats.client.ZalksStatsClient}.
 */
public class ZalksStats implements ModInitializer {
	public static final String MOD_ID = "zalks-stats";
	public static final Logger LOGGER = LoggerFactory.getLogger("zalk's stats");

	/**
	 * Single daemon thread for background probes. Daemon so it can never keep the
	 * game (or the server) alive, and single-threaded so probes cannot overlap.
	 */
	private static final ThreadFactory BACKGROUND = runnable -> {
		Thread thread = new Thread(runnable, MOD_ID + "-background");
		thread.setDaemon(true);
		return thread;
	};

	private static final java.util.concurrent.ExecutorService PROBE =
			Executors.newSingleThreadExecutor(BACKGROUND);

	private static long startNanos = System.nanoTime();
	private static long[] lastTps = {0L, 0L, 0L};
	private static long lastTpsInterval = 0L;

	@Override
	public void onInitialize() {
		Settings.load();

		logEnvironment();
		startHardwareProbe();

		startHeapLogging();
		startTpsLogging();
		startPlayerLogging();
	}

	/**
	 * Queues the hardware probe on a background thread.
	 *
	 * <p>This is the whole point of the optimisation: {@code getSystemCpuLoad(1000)}
	 * samples for a full second, which used to stall game start-up for a second.
	 */
	private void startHardwareProbe() {
		if (!Settings.get().logHardware) {
			return;
		}
		long began = System.nanoTime();
		PROBE.submit(() -> {
			try {
				SystemInfo info = new SystemInfo();
				HardwareAbstractionLayer hardware = info.getHardware();
				CentralProcessor processor = hardware.getProcessor();
				GlobalMemory memory = hardware.getMemory();

				LOGGER.info("=== hardware ===");
				LOGGER.info("os: {}", info.getOperatingSystem());
				LOGGER.info("cpu: {}", processor.getProcessorIdentifier().getName());
				LOGGER.info("cores (physical/logical): {}/{}",
						processor.getPhysicalProcessorCount(), processor.getLogicalProcessorCount());
				LOGGER.info("cpu load: {}%", round(processor.getSystemCpuLoad(1000) * 100.0));
				LOGGER.info("ram: {} / {}", formatBytes(memory.getTotal()), formatBytes(memory.getAvailable()));
				LOGGER.info("=== hardware end ({} ms off-thread) ===",
						(System.nanoTime() - began) / 1_000_000L);
			} catch (Throwable t) {
				LOGGER.warn("hardware probe failed", t);
			}
		});
	}

	private void logEnvironment() {
		if (!Settings.get().logEnvironment) {
			return;
		}
		Runtime runtime = Runtime.getRuntime();
		long max = runtime.maxMemory();
		long total = runtime.totalMemory();
		long used = total - runtime.freeMemory();

		LOGGER.info("=== environment ===");
		LOGGER.info("env: {}", FabricLoader.getInstance().getEnvironmentType().name().toLowerCase(Locale.ROOT));
		LOGGER.info("loader: {}", FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
		LOGGER.info("java: {} ({})", System.getProperty("java.version"), System.getProperty("java.vm.name"));
		LOGGER.info("os: {} {} ({} arch)", System.getProperty("os.name"),
				System.getProperty("os.version"), System.getProperty("os.arch"));
		LOGGER.info("memory: {} / {}", formatBytes(used), max > 0 ? formatBytes(max) : "unbounded");
		LOGGER.info("cpus: {}", ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors());
		LOGGER.info("=== environment end ===");
	}

	private void startHeapLogging() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> PROBE.submit(() -> {
			if (!Settings.get().logHeap) {
				return;
			}
			long last = 0L;
			while (server.isRunning()) {
				try {
					Thread.sleep(Settings.get().heapInterval * 1000L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
				if (!Settings.get().logHeap || !server.isRunning()) {
					continue;
				}
				Runtime runtime = Runtime.getRuntime();
				long used = runtime.totalMemory() - runtime.freeMemory();
				long delta = used - last;
				last = used;
				LOGGER.info("heap: {} used ({} max), {} since last report",
						formatBytes(used), formatBytes(runtime.maxMemory()), signedBytes(delta));
			}
		}));
	}

	private void startTpsLogging() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!Settings.get().logTps) {
				return;
			}
			// Only touch the expensive TPS window once per interval, not per tick.
			long now = System.currentTimeMillis();
			if (now - lastTpsInterval < Settings.get().tpsInterval * 1000L) {
				return;
			}
			lastTpsInterval = now;
			lastTps = server.getTickTimesNanos();
			LOGGER.info("tps: 1m={} 5m={} 15m={}",
					tpsFromNanos(lastTps[0]), tpsFromNanos(lastTps[1]), tpsFromNanos(lastTps[2]));
		});
	}

	/** Converts an average tick duration in nanoseconds into ticks per second. */
	private static double tpsFromNanos(long nanosPerTick) {
		if (nanosPerTick <= 0L) {
			return 20.0;
		}
		return Math.min(20.0, 1_000_000_000.0 / nanosPerTick);
	}

	private void startPlayerLogging() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (Settings.get().logServerLifecycle) {
				LOGGER.info("server starting (tick {} after {}s uptime)",
						server.getTickCount(), uptimeSeconds());
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (Settings.get().logServerLifecycle) {
				LOGGER.info("server stopping after {}s uptime",
						(System.nanoTime() - startNanos) / 1_000_000_000L);
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (Settings.get().logPlayerJoinLeave) {
				LOGGER.info("{} joined", handler.getPlayer().getName().getString());
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (Settings.get().logPlayerJoinLeave) {
				LOGGER.info("{} disconnected", handler.getPlayer().getName().getString());
			}
		});
	}

	private static String round(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static String formatBytes(long bytes) {
		if (bytes <= 0) {
			return "unknown";
		}
		String[] units = {"B", "KB", "MB", "GB", "TB"};
		double value = bytes;
		int unit = 0;
		while (value >= 1024 && unit < units.length - 1) {
			value /= 1024;
			unit++;
		}
		return String.format(Locale.ROOT, "%.2f %s", value, units[unit]);
	}

	private static String signedBytes(long bytes) {
		return (bytes >= 0 ? "+" : "-") + formatBytes(Math.abs(bytes));
	}

	/** Exposed for the client entrypoint's uptime line. */
	public static long uptimeSeconds() {
		return (System.nanoTime() - startNanos) / 1_000_000_000L;
	}
}