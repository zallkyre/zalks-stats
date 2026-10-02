package net.zalks.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent user settings.
 *
 * <p>Deliberately free of any client-only import so it is safe to touch from the
 * common/server entrypoint. Values are clamped on load and on save so a corrupted
 * or hand-edited config file can never produce a nonsensical (or crash-inducing) value.
 */
public final class Settings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE =
			FabricLoader.getInstance().getConfigDir().resolve("zalks-stats.json");

	private static final Settings INSTANCE = new Settings();
	private static boolean loaded = false;

	// --- logging toggles ---
	public boolean logHardware = true;
	public boolean logEnvironment = true;
	public boolean logFps = true;
	public boolean logTps = true;
	public boolean logHeap = true;
	public boolean logPlayerJoinLeave = true;
	public boolean logServerLifecycle = true;

	// --- FPS thresholds ---
	/** FPS at or below this triggers a single low-FPS warning. */
	public int fpsWarnThreshold = 20;
	/** FPS must climb back to this before another low-FPS warning can fire. */
	public int fpsRecoverThreshold = 30;

	// --- report intervals, in seconds ---
	public int fpsSummaryInterval = 300;
	public int tpsInterval = 5;
	public int heapInterval = 300;

	private Settings() {
	}

	public static Settings get() {
		return INSTANCE;
	}

	/** Idempotent. Safe to call from multiple entrypoints. */
	public static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (!Files.exists(FILE)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
			Settings parsed = GSON.fromJson(reader, Settings.class);
			if (parsed != null) {
				INSTANCE.applyFrom(parsed);
			}
		} catch (Exception e) {
			System.err.println("[zalks-stats] Could not read config, using defaults: " + e.getMessage());
		}
		INSTANCE.clamp();
	}

	public static synchronized void save() {
		INSTANCE.clamp();
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(INSTANCE, writer);
			}
		} catch (IOException e) {
			System.err.println("[zalks-stats] Could not write config: " + e.getMessage());
		}
	}

	private void applyFrom(Settings other) {
		this.logHardware = other.logHardware;
		this.logEnvironment = other.logEnvironment;
		this.logFps = other.logFps;
		this.logTps = other.logTps;
		this.logHeap = other.logHeap;
		this.logPlayerJoinLeave = other.logPlayerJoinLeave;
		this.logServerLifecycle = other.logServerLifecycle;
		this.fpsWarnThreshold = other.fpsWarnThreshold;
		this.fpsRecoverThreshold = other.fpsRecoverThreshold;
		this.fpsSummaryInterval = other.fpsSummaryInterval;
		this.tpsInterval = other.tpsInterval;
		this.heapInterval = other.heapInterval;
	}

	public void clamp() {
		fpsWarnThreshold = clampChoice(fpsWarnThreshold, FPS_WARN_CHOICES, 20);
		fpsRecoverThreshold = clampChoice(fpsRecoverThreshold, FPS_THRESHOLD_CHOICES, 30);
		// Recovery must sit above the warn line, otherwise the FPS warning re-arms
		// as soon as the client dips under warn again. Snap to the next offered
		// threshold instead of adding a fixed offset: an off-list value such as
		// warn+5 can never be cycled back to by the settings screen.
		if (fpsRecoverThreshold <= fpsWarnThreshold) {
			fpsRecoverThreshold = nextAbove(fpsWarnThreshold, FPS_THRESHOLD_CHOICES);
		}
		fpsSummaryInterval = clampChoice(fpsSummaryInterval, FPS_SUMMARY_CHOICES, 300);
		tpsInterval = clampChoice(tpsInterval, TPS_CHOICES, 5);
		heapInterval = clampChoice(heapInterval, HEAP_CHOICES, 300);
	}

	/**
	 * The smallest value in {@code allowed} that is strictly greater than {@code value},
	 * or the last entry if {@code value} is at or above every option.
	 *
	 * <p>Invariant relied on by {@link #clamp()}: {@link #FPS_WARN_CHOICES} tops out
	 * below {@link #FPS_THRESHOLD_CHOICES}, so a recovery value above any legal warn
	 * value always exists.
	 */
	private static int nextAbove(int value, int[] allowed) {
		for (int option : allowed) {
			if (option > value) {
				return option;
			}
		}
		return allowed[allowed.length - 1];
	}

	private static int clampChoice(int value, int[] allowed, int fallback) {
		for (int option : allowed) {
			if (option == value) {
				return value;
			}
		}
		return fallback;
	}

	/** Value sets offered by the settings screen. */
	public static final int[] FPS_SUMMARY_CHOICES = {60, 120, 300, 600, 900};
	public static final int[] TPS_CHOICES = {1, 2, 5, 10, 20, 30};
	public static final int[] HEAP_CHOICES = {60, 120, 300, 600, 900};
	public static final int[] FPS_THRESHOLD_CHOICES = {10, 15, 20, 25, 30, 40, 50, 60, 75, 90, 120};
	/** Warn stops one below the top so a strictly higher recovery threshold always exists. */
	public static final int[] FPS_WARN_CHOICES = {10, 15, 20, 25, 30, 40, 50, 60, 75, 90};

	/** Renders an interval in seconds as something readable. */
	public static String formatInterval(int seconds) {
		if (seconds % 60 == 0) {
			int minutes = seconds / 60;
			return minutes == 1 ? "1 minute" : minutes + " minutes";
		}
		return seconds + " seconds";
	}
}