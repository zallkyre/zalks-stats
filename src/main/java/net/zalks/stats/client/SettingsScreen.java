package net.zalks.stats.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.zalks.stats.Settings;
import net.zalks.stats.ZalksStats;

import java.util.Locale;

/**
 * The Mod Menu config screen.
 *
 * <p>Written against the Minecraft 26.3 GUI API, where screens render through
 * {@code extractRenderState}/{@code extractBackground} on a
 * {@link GuiGraphicsExtractor} rather than the old {@code render}/{@code renderBackground}
 * pair, and widgets are built with {@code Button.builder(...).bounds(...).build()}.
 */
public class SettingsScreen extends Screen {
	private static final int COLUMN_WIDTH = 250;
	private static final int COLUMN_GAP = 16;
	private static final int ROW_HEIGHT = 24;
	private static final int ROW_SPACING = 6;

	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Component.literal("zalk's stats"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Settings settings = Settings.get();

		int blockWidth = COLUMN_WIDTH * 2 + COLUMN_GAP;
		int leftX = (this.width - blockWidth) / 2;
		int rightX = leftX + COLUMN_WIDTH + COLUMN_GAP;
		int startY = 52;

		int row = 0;

		addRenderableWidget(toggle(leftX, startY + row++ * (ROW_HEIGHT + ROW_SPACING),
				"Hardware report", "logHardware", settings.logHardware));
		addRenderableWidget(toggle(leftX, startY + row++ * (ROW_HEIGHT + ROW_SPACING),
				"Environment report", "logEnvironment", settings.logEnvironment));
		addRenderableWidget(toggle(leftX, startY + row++ * (ROW_HEIGHT + ROW_SPACING),
				"FPS monitor", "logFps", settings.logFps));
		addRenderableWidget(toggle(leftX, startY + row++ * (ROW_HEIGHT + ROW_SPACING),
				"TPS monitor", "logTps", settings.logTps));
		addRenderableWidget(toggle(leftX, startY + row++ * (ROW_HEIGHT + ROW_SPACING),
				"Heap monitor", "logHeap", settings.logHeap));
		addRenderableWidget(toggle(leftX, startY + row++ * (ROW_HEIGHT + ROW_SPACING),
				"Player join/leave", "logPlayerJoinLeave", settings.logPlayerJoinLeave));
		addRenderableWidget(toggle(leftX, startY + row++ * (ROW_HEIGHT + ROW_SPACING),
				"Server lifecycle", "logServerLifecycle", settings.logServerLifecycle));

		row = 0;
		addRenderableWidget(choice(rightX, startY + row++ * (ROW_HEIGHT + ROW_SPACING),
				"FPS warn below", Settings.FPS_THRESHOLD_CHOICES, settings.fpsWarnThreshold,
				v -> String.valueOf(v) + " fps",
				v -> settings.fpsWarnThreshold = v));
		addRenderableWidget(choice(rightX, startY + row++ * (ROW_HEIGHT + ROW_SPACING),
				"FPS recover above", Settings.FPS_THRESHOLD_CHOICES, settings.fpsRecoverThreshold,
				v -> String.valueOf(v) + " fps",
				v -> settings.fpsRecoverThreshold = v));
		addRenderableWidget(choice(rightX, startY + row++ * (ROW_HEIGHT + ROW_SPACING),
				"FPS summary every", Settings.FPS_SUMMARY_CHOICES, settings.fpsSummaryInterval,
				Settings::formatInterval,
				v -> settings.fpsSummaryInterval = v));
		addRenderableWidget(choice(rightX, startY + row++ * (ROW_HEIGHT + ROW_SPACING),
				"TPS report every", Settings.TPS_CHOICES, settings.tpsInterval,
				Settings::formatInterval,
				v -> settings.tpsInterval = v));
		addRenderableWidget(choice(rightX, startY + row++ * (ROW_HEIGHT + ROW_SPACING),
				"Heap report every", Settings.HEAP_CHOICES, settings.heapInterval,
				Settings::formatInterval,
				v -> settings.heapInterval = v));

		int doneWidth = 200;
		int doneX = (this.width - doneWidth) / 2;
		int doneY = this.height - 36;
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(doneX, doneY, doneWidth, ROW_HEIGHT)
				.build());
	}

	/** A two-state button that writes straight through to the field named by {@code key}. */
	private Button toggle(int x, int y, String label, String key, boolean current) {
		Settings settings = Settings.get();
		return Button.builder(Component.literal(label + ": " + (current ? "ON" : "OFF")), button -> {
			boolean next = !current;
			switch (key) {
				case "logHardware" -> settings.logHardware = next;
				case "logEnvironment" -> settings.logEnvironment = next;
				case "logFps" -> settings.logFps = next;
				case "logTps" -> settings.logTps = next;
				case "logHeap" -> settings.logHeap = next;
				case "logPlayerJoinLeave" -> settings.logPlayerJoinLeave = next;
				case "logServerLifecycle" -> settings.logServerLifecycle = next;
				default -> throw new IllegalArgumentException("unknown setting: " + key);
			}
			Settings.save();
			rebuildWidgets();
		}).bounds(x, y, COLUMN_WIDTH, ROW_HEIGHT).build();
	}

	/** A button that cycles through a fixed set of integer values. */
	private Button choice(int x, int y, String label, int[] options, int current,
							java.util.function.IntFunction<String> format,
							java.util.function.IntConsumer setter) {
		return Button.builder(Component.literal(label + ": " + format.apply(current)), button -> {
			int index = 0;
			for (int i = 0; i < options.length; i++) {
				if (options[i] == current) {
					index = (i + 1) % options.length;
					break;
				}
			}
			int next = options[index];
			setter.accept(next);
			Settings.save();
			rebuildWidgets();
		}).bounds(x, y, COLUMN_WIDTH, ROW_HEIGHT).build();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int blockWidth = COLUMN_WIDTH * 2 + COLUMN_GAP;
		int leftX = (this.width - blockWidth) / 2;

		graphics.text(this.font, this.title, leftX, 18, 0xFFFFFFFF);

		Settings settings = Settings.get();
		graphics.text(this.font, Component.literal(
						String.format(Locale.ROOT, "uptime %ds | cpus %d | %s",
								ZalksStats.uptimeSeconds(),
								Runtime.getRuntime().availableProcessors(),
								Settings.formatInterval(settings.heapInterval) + " heap reports")),
				leftX, 34, 0xFFA0A0A0);

		graphics.text(this.font, Component.literal("Reports"), leftX, 44, 0xFF808080);
		graphics.text(this.font, Component.literal("Intervals & thresholds"), leftX + COLUMN_WIDTH + COLUMN_GAP,
				44, 0xFF808080);
	}

	@Override
	public void onClose() {
		Settings.save();
		this.minecraft.setScreenAndShow(this.parent);
	}
}