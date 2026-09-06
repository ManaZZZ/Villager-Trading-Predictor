package net.manaz.vtp.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.Objects;
import java.util.UUID;

/** Presentation-only rules. Hiding the HUD never clears tracking, targets or predictions. */
public final class HudVisibility {
    public enum Mode {
        NEVER("Never hide"), TIME("After time"), RANGE("Out of range"), LOOKING("Looking only");
        public final String label;
        Mode(String label) { this.label = label; }
        public static Mode parse(String name) {
            for (Mode mode : values()) if (mode.name().equalsIgnoreCase(name)) return mode;
            return NEVER;
        }
    }
    private static Mode mode = Mode.NEVER;
    private static int seconds = 10, range = 16;
    private static Object world;
    private static UUID selected;
    private static Villager entity;
    private static long lastAttention;
    private static boolean looking;
    private static double distanceSquared = Double.POSITIVE_INFINITY;
    private HudVisibility() {}
    public static Mode mode() { return mode; }
    public static int seconds() { return seconds; }
    public static int range() { return range; }
    public static void configure(Mode value, int timeoutSeconds, int rangeBlocks) {
        mode = value == null ? Mode.NEVER : value;
        seconds = Math.clamp(timeoutSeconds, 1, 3600);
        range = Math.clamp(rangeBlocks, 1, 256);
    }
    public static void reset() {
        world = null; selected = null; entity = null; lastAttention = 0;
        looking = false; distanceSquared = Double.POSITIVE_INFINITY;
    }
    public static void tick(Minecraft mc) {
        UUID uuid = VillagerTracker.getLastVillagerUuid();
        if (mc.level == null || mc.player == null || uuid == null) { reset(); return; }
        long now = System.nanoTime();
        if (world != mc.level || !Objects.equals(selected, uuid)) {
            reset(); world = mc.level; selected = uuid; lastAttention = now;
        }
        looking = mc.gui.screen() == null && mc.crosshairPickEntity instanceof Villager villager
                && villager.isAlive() && uuid.equals(villager.getUUID());
        if (looking) entity = (Villager) mc.crosshairPickEntity;
        boolean trading = mc.gui.screen() instanceof MerchantScreen && uuid.equals(VillagerTracker.getMenuVillagerUuid());
        if (looking || trading) lastAttention = now;
        if (entity != null && (entity.isRemoved() || !entity.isAlive())) entity = null;
        if (mode == Mode.RANGE && entity == null) {
            for (var candidate : mc.level.entitiesForRendering()) {
                if (candidate instanceof Villager villager && villager.isAlive() && uuid.equals(villager.getUUID())) {
                    entity = villager; break;
                }
            }
        }
        distanceSquared = entity == null ? Double.POSITIVE_INFINITY : mc.player.distanceToSqr(entity);
    }
    public static boolean visible(UUID uuid) {
        return uuid != null && uuid.equals(selected)
                && permits(mode, looking, System.nanoTime() - lastAttention, distanceSquared, seconds, range);
    }
    public static boolean permits(Mode mode, boolean looking, long elapsedNanos, double distanceSquared,
                                  int seconds, int range) {
        return switch (mode) {
            case NEVER -> true;
            case TIME -> looking || Math.max(0, elapsedNanos) < seconds * 1_000_000_000L;
            case RANGE -> Double.isFinite(distanceSquared) && distanceSquared <= (double) range * range;
            case LOOKING -> looking;
        };
    }
}