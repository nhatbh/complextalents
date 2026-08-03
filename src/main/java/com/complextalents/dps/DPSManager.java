package com.complextalents.dps;

import com.complextalents.TalentsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global manager for DPS logging sessions and event subscribers.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DPSManager {

    private static final DPSManager INSTANCE = new DPSManager();

    public static DPSManager getInstance() {
        return INSTANCE;
    }

    private final Map<UUID, DPSSession> activeSessions = new ConcurrentHashMap<>();
    public static final long INACTIVITY_TIMEOUT_MS = 3000L; // 3 seconds timeout

    private DPSManager() {}

    /**
     * Starts or resets a DPS tracking session for the player.
     */
    public void startSession(Player player) {
        DPSSession existing = activeSessions.get(player.getUUID());
        if (existing != null && existing.getState() != DPSSession.State.FINISHED) {
            existing.reportAndSend();
        }

        DPSSession newSession = new DPSSession(player);
        activeSessions.put(player.getUUID(), newSession);

        MutableComponent msg = Component.literal("[DPS Meter] ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("DPS meter ARMED! Deal damage to begin logging. Auto-finishes after 3s of inactivity.")
                        .withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(msg);
    }

    /**
     * Manually stops the active DPS session and displays report.
     */
    public void stopSession(Player player) {
        DPSSession session = activeSessions.remove(player.getUUID());
        if (session != null && session.getState() != DPSSession.State.FINISHED) {
            String log = session.reportAndSend();
            TalentsMod.LOGGER.info(log);
        } else {
            MutableComponent msg = Component.literal("[DPS Meter] ")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("No active DPS session running.").withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(msg);
        }
    }

    /**
     * Returns true if player currently has an armed or active DPS session.
     */
    public boolean isTracking(Player player) {
        DPSSession session = activeSessions.get(player.getUUID());
        return session != null && session.getState() != DPSSession.State.FINISHED;
    }

    /**
     * Sends the current session status to the player.
     */
    public void sendStatus(Player player) {
        DPSSession session = activeSessions.get(player.getUUID());
        if (session == null || session.getState() == DPSSession.State.FINISHED) {
            MutableComponent msg = Component.literal("[DPS Meter] ")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("Status: IDLE (Use '/dps start' to begin)").withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(msg);
            return;
        }

        if (session.getState() == DPSSession.State.ARMED) {
            MutableComponent msg = Component.literal("[DPS Meter] ")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("Status: ARMED (Waiting for first damage hit)").withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(msg);
        } else {
            long durationMs = System.currentTimeMillis() - session.getStartTimeMs();
            float durationSec = durationMs / 1000.0f;
            float currentDps = durationSec > 0 ? session.getTotalDamage() / durationSec : session.getTotalDamage();

            MutableComponent msg = Component.literal("[DPS Meter] ")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal(String.format("Status: ACTIVE (Time: %.1fs | Total Dmg: %.1f | Current DPS: %.1f)",
                            durationSec, session.getTotalDamage(), currentDps)).withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(msg);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) {
            attacker = event.getSource().getDirectEntity();
        }

        if (attacker instanceof Player player) {
            DPSSession session = INSTANCE.activeSessions.get(player.getUUID());
            if (session != null && session.getState() != DPSSession.State.FINISHED) {
                session.recordDamage(event.getAmount(), System.currentTimeMillis());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }

        Player player = event.player;
        DPSSession session = INSTANCE.activeSessions.get(player.getUUID());

        if (session != null && session.getState() == DPSSession.State.ACTIVE) {
            long currentTime = System.currentTimeMillis();
            if (session.isExpired(currentTime, INACTIVITY_TIMEOUT_MS)) {
                INSTANCE.activeSessions.remove(player.getUUID());
                String logMsg = session.reportAndSend();
                TalentsMod.LOGGER.info(logMsg);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        INSTANCE.activeSessions.remove(event.getEntity().getUUID());
    }
}
