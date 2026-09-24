package net.zalks.stats;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSFileStore;

public class ZalksStats implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("zalks-stats");
    private static final SystemInfo SI = new SystemInfo();

    // client fps monitor state
    private int tickCounter = 0;
    private boolean struggling = false;
    private int fpsSum = 0;
    private int fpsSamples = 0;
    private long lastFpsReport = 0;

    // server tps monitor state
    private int serverTickCount = 0;
    private long lastTpsCheck = 0;
    private int tpsChecks = 0;

    @Override
    public void onInitialize() {
        logHardwareInfo();
        logEnvironmentInfo();

        // 1. client-side monitoring (wrapped to prevent server crashes)
        try {
            Class.forName("net.minecraft.client.MinecraftClient");
            registerClientEvents();
        } catch (ClassNotFoundException e) {
            LOGGER.info("server detected: skipping fps monitor.");
        }

        // 2. server-side monitoring (no-op on a pure client)
        registerServerEvents();
    }

    private void logHardwareInfo() {
        HardwareAbstractionLayer hal = SI.getHardware();
        CentralProcessor cpu = hal.getProcessor();

        LOGGER.info("os: {} {} ({})", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
        LOGGER.info("java: {}", System.getProperty("java.version"));
        LOGGER.info("cpu: {}", cpu.getProcessorIdentifier().getName());
        LOGGER.info("cpu cores: {} physical / {} logical", cpu.getPhysicalProcessorCount(), cpu.getLogicalProcessorCount());
        double cpuLoad = cpu.getSystemCpuLoad(1000);
        if (cpuLoad >= 0) {
            LOGGER.info("cpu load: {}%", Math.round(cpuLoad * 100));
        }
        LOGGER.info("ram: {} GB total / {} GB used", Math.round(hal.getMemory().getTotal() / 1e9), Math.round((hal.getMemory().getTotal() - hal.getMemory().getAvailable()) / 1e9));

        // servers usually don't have gpus, but oshi will just return an empty list instead of crashing
        for (GraphicsCard gpu : hal.getGraphicsCards()) {
            LOGGER.info("gpu: {} ({} MB vram)", gpu.getName(), Math.round(gpu.getVRam() / 1048576.0));
        }

        for (OSFileStore fs : SI.getOperatingSystem().getFileSystem().getFileStores()) {
            LOGGER.info("disk: {} - {} GB free / {} GB total", fs.getName(), Math.round(fs.getFreeSpace() / 1e9), Math.round(fs.getTotalSpace() / 1e9));
        }
    }

    private void logEnvironmentInfo() {
        FabricLoader loader = FabricLoader.getInstance();
        String mcVersion = loader.getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        String loaderVersion = loader.getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        LOGGER.info("minecraft: {}", mcVersion);
        LOGGER.info("fabric loader: {}", loaderVersion);
        LOGGER.info("mods loaded: {}", loader.getAllMods().size());
    }

    private void registerClientEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null) {
                tickCounter++;
                int fps = client.getFps();
                if (fps > 0) {
                    fpsSum += fps;
                    fpsSamples++;
                }

                // every 10 seconds: warn on low fps, log recovery
                if (tickCounter >= 200) {
                    if (fps < 20) {
                        if (!struggling) {
                            LOGGER.warn("struggling! fps: {}", fps);
                            struggling = true;
                        }
                    } else if (struggling && fps >= 30) {
                        LOGGER.info("fps recovered: {}", fps);
                        struggling = false;
                    }
                    tickCounter = 0;
                }

                // every 5 minutes: average fps + heap usage
                long now = System.currentTimeMillis();
                if (now - lastFpsReport >= 300000) {
                    if (fpsSamples > 0) {
                        LOGGER.info("avg fps (5 min): {}", Math.round(fpsSum / (double) fpsSamples));
                    }
                    fpsSum = 0;
                    fpsSamples = 0;
                    lastFpsReport = now;
                    logHeap();
                }
            }
        });
    }

    private void registerServerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("server started: {}", server.getMotd());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            serverTickCount++;
            long now = System.currentTimeMillis();
            if (now - lastTpsCheck >= 5000) {
                double tps = serverTickCount * 1000.0 / (now - lastTpsCheck);
                serverTickCount = 0;
                lastTpsCheck = now;
                if (tps < 18) {
                    LOGGER.warn("server struggling! tps: {}", String.format("%.1f", tps));
                } else {
                    LOGGER.info("tps: {}", String.format("%.1f", tps));
                }

                // every 5 minutes: heap usage
                tpsChecks++;
                if (tpsChecks >= 60) {
                    logHeap();
                    tpsChecks = 0;
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            LOGGER.info("player joined: {}", handler.getPlayer().getName().getString());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LOGGER.info("player left: {}", handler.getPlayer().getName().getString());
        });
    }

    private void logHeap() {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        LOGGER.info("heap: {} MB used / {} MB max", used / 1048576, rt.maxMemory() / 1048576);
    }
}