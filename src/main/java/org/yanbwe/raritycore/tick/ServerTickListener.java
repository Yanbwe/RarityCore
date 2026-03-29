package org.yanbwe.raritycore.tick;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.calc.AutoRarityCalculator;
import org.yanbwe.raritycore.service.ServiceFactory;

import java.nio.file.Path;

public class ServerTickListener {

    private int tickCounter = 0;
    private int startDelayCounter = 0;
    private static final int SYNC_INTERVAL = 40;
    private static final int CALC_INTERVAL = 1;
    private static final int START_DELAY_TICKS = 100;
    private boolean hasCheckedAutoCalculation = false;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;

        if (!hasCheckedAutoCalculation) {
            startDelayCounter++;
            if (startDelayCounter >= START_DELAY_TICKS) {
                hasCheckedAutoCalculation = true;
                checkAndStartAutoCalculation();
            }
        }

        if (tickCounter % SYNC_INTERVAL == 0) {
            try {
                ServiceFactory factory = ServiceFactory.getInstance();
                int pendingCount = factory.getSyncBatchManager().getPendingOperationCount();
                if (pendingCount > 0) {
                    factory.getSyncManager().syncIncrementalChangesToClients();
                }
            } catch (Exception e) {
                RarityCore.LOGGER.error("增量同步过程中发生错误", e);
            }
        }

        if (tickCounter % CALC_INTERVAL == 0) {
            try {
                AutoRarityCalculator.tick();
            } catch (Exception e) {
                RarityCore.LOGGER.error("自动稀有度计算tick发生错误", e);
            }
        }
    }

    private void checkAndStartAutoCalculation() {
        Path autoRarityFile = ServiceFactory.getInstance().getAutoRarityConfigManager().getAutoRarityFilePath();
        if (!java.nio.file.Files.exists(autoRarityFile)) {
            RarityCore.LOGGER.info("自动稀有度配置文件不存在,开始自动计算");
            AutoRarityCalculator.startAutoCalculation();
        } else {
            RarityCore.LOGGER.debug("自动稀有度配置已存在,跳过计算: {}", autoRarityFile);
        }
    }
}