package net.zalks.stats;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;

public class ZalksStats implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("zalks-stats");
    private static final SystemInfo SI = new SystemInfo();
    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        HardwareAbstractionLayer hal = SI.getHardware();
        CentralProcessor cpu = hal.getProcessor();
        
        // 1. hardware specs (works on both server and client)
        LOGGER.info("ram: {} gb", Math.round(hal.getMemory().getTotal() / 1e+9));
        LOGGER.info("cpu: {}", cpu.getProcessorIdentifier().getName());
        
        // servers usually don't have gpus, but oshi will just return an empty list instead of crashing
        for (GraphicsCard gpu : hal.getGraphicsCards()) {
            LOGGER.info("gpu: {}", gpu.getName());
        }

        // 2. monitoring logic (wrapped to prevent server crashes)
        try {
            Class.forName("net.minecraft.client.MinecraftClient");
            registerClientEvents();
        } catch (ClassNotFoundException e) {
            LOGGER.info("server detected: skipping fps monitor.");
        }
    }

    private void registerClientEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null) {
                tickCounter++;
                if (tickCounter >= 200) { 
                    int fps = client.getFps();
                    if (fps < 20 && fps > 0) {
                        LOGGER.warn("struggling! fps: {}", fps);
                    }
                    tickCounter = 0;
                }
            }
        });
    }
}