package net.manaz.vtp.autoreroll;

import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.prediction.PredictionResult;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.TradeDataLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Drives the Auto-Reroll feature: breaks the workstation block the player is
 * looking at with a designated tool, switches to the workstation item slot,
 * places it back, and repeats until the desired number of rerolls is reached.
 */
public class AutoRerollManager {
    public static final int SLOT_OFFHAND = -1;

    private static final int MAX_REACH_SQ  = 20; // ~4.47 blocks, under server reach of 4.5
    private static final int PHASE_TIMEOUT = 80; // ticks before giving up on a phase
    private static final int BREAK_TIMEOUT = 400; // 20s — workstations can be slow without an optimal tool

    // ── Persistent config ────────────────────────────────────────────────────
    private static boolean useTargetCount  = true;
    private static int manualCount         = 16;
    private static int safetyRerolls       = 0;
    private static int toolSlot            = 0;  // 0-8
    private static int workstationSlot     = 1;  // 0-8 or SLOT_OFFHAND
    private static int cycleDelayTicks     = 20; // ticks to wait after each reroll cycle

    // ── Runtime state ────────────────────────────────────────────────────────
    private enum Phase { IDLE, SWITCH_TO_TOOL, BREAK, SWITCH_TO_WS, PLACE, WAIT_PLACE, WAIT_CYCLE }
    private static Phase    phase        = Phase.IDLE;
    private static BlockPos targetPos    = null;
    private static Direction face        = null;
    private static int plannedRerolls    = 0;
    private static int completedRerolls  = 0;
    private static int phaseTicks        = 0;
    private static int previousSlot      = -1;
    private static boolean destroyStarted = false;
    private static String lastStatus     = "Idle";

    // ── Config getters/setters ───────────────────────────────────────────────
    public static boolean isUseTargetCount()       { return useTargetCount; }
    public static void    setUseTargetCount(boolean v) { useTargetCount = v; }
    public static int     getManualCount()         { return manualCount; }
    public static void    setManualCount(int v)    { manualCount = Math.max(0, v); }
    public static int     getSafetyRerolls()       { return safetyRerolls; }
    public static void    setSafetyRerolls(int v)  { safetyRerolls = Math.max(0, v); }
    public static int     getToolSlot()            { return toolSlot; }
    public static void    setToolSlot(int v)       { toolSlot = clampHotbar(v); }
    public static int     getWorkstationSlot()     { return workstationSlot; }
    public static void    setWorkstationSlot(int v) {
        workstationSlot = v == SLOT_OFFHAND ? SLOT_OFFHAND : clampHotbar(v);
    }
    public static int     getCycleDelayTicks()     { return cycleDelayTicks; }
    public static void    setCycleDelayTicks(int v){ cycleDelayTicks = Math.max(0, v); }

    public static boolean isRunning()          { return phase != Phase.IDLE; }
    public static int     getPlannedRerolls()  { return plannedRerolls; }
    public static int     getCompletedRerolls(){ return completedRerolls; }
    public static String  getStatus()          { return lastStatus; }

    private static int clampHotbar(int v) { return Math.max(0, Math.min(8, v)); }

    // ── Entry points ─────────────────────────────────────────────────────────

    public static void toggle() {
        if (isRunning()) stop("Auto-reroll stopped.");
        else start();
    }

    public static void stop(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (phase == Phase.BREAK && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
        if (mc.player != null && previousSlot >= 0 && previousSlot <= 8
                && mc.player.getInventory().getSelectedSlot() != previousSlot) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(previousSlot));
        }
        phase        = Phase.IDLE;
        targetPos    = null;
        face         = null;
        previousSlot = -1;
        destroyStarted = false;
        if (message != null) {
            lastStatus = message;
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[VTP] " + message));
        } else {
            lastStatus = "Idle";
        }
    }

    private static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) {
            abort(mc, "Look at a workstation first.");
            return;
        }

        BlockState state = mc.level.getBlockState(bh.getBlockPos());
        if (state.isAir()) {
            abort(mc, "No block at crosshair.");
            return;
        }

        Inventory inv = mc.player.getInventory();
        ItemStack toolStack = inv.getItem(toolSlot);
        if (toolStack.isEmpty()) {
            abort(mc, "Tool slot " + (toolSlot + 1) + " is empty.");
            return;
        }
        ItemStack wsStack = workstationSlot == SLOT_OFFHAND
                ? mc.player.getOffhandItem() : inv.getItem(workstationSlot);
        if (wsStack.isEmpty()) {
            abort(mc, "Workstation slot is empty.");
            return;
        }

        int rerollsTilTarget;
        if (useTargetCount) {
            OptionalInt opt = computeRerollsUntilTarget();
            if (opt.isEmpty()) {
                abort(mc, "Cannot compute rerolls — need seed, target, and a tracked villager.");
                return;
            }
            rerollsTilTarget = opt.getAsInt();
        } else {
            rerollsTilTarget = manualCount;
        }

        int planned = Math.max(0, rerollsTilTarget - safetyRerolls);
        if (planned <= 0) {
            abort(mc, "Nothing to do: " + rerollsTilTarget + " - " + safetyRerolls + " safety = " + planned + ".");
            return;
        }

        targetPos        = bh.getBlockPos();
        face             = bh.getDirection();
        plannedRerolls   = planned;
        completedRerolls = 0;
        previousSlot     = inv.getSelectedSlot();
        phase            = Phase.SWITCH_TO_TOOL;
        phaseTicks       = 0;
        lastStatus       = "Running";
        say(mc, "Auto-reroll started: " + planned + " rerolls (" + rerollsTilTarget
                + " to target, " + safetyRerolls + " safety).");
    }

    // ── Per-tick state machine ───────────────────────────────────────────────

    public static void tick() {
        if (phase == Phase.IDLE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null || targetPos == null) {
            stop(null);
            return;
        }

        if (mc.player.distanceToSqr(Vec3.atCenterOf(targetPos)) > MAX_REACH_SQ) {
            stop("Stopped: out of reach.");
            return;
        }

        phaseTicks++;
        int timeout = switch (phase) {
            case WAIT_CYCLE -> Integer.MAX_VALUE;
            case BREAK     -> BREAK_TIMEOUT;
            default        -> PHASE_TIMEOUT;
        };
        if (phaseTicks > timeout) {
            stop("Timed out in phase " + phase + ".");
            return;
        }

        switch (phase) {
            case SWITCH_TO_TOOL -> {
                selectSlot(mc, toolSlot);
                phase = Phase.BREAK;
                phaseTicks = 0;
                destroyStarted = false;
            }
            case BREAK -> {
                BlockState here = mc.level.getBlockState(targetPos);
                if (here.isAir()) {
                    if (destroyStarted) mc.gameMode.stopDestroyBlock();
                    destroyStarted = false;
                    phase = Phase.SWITCH_TO_WS;
                    phaseTicks = 0;
                    return;
                }
                if (!destroyStarted) {
                    mc.gameMode.startDestroyBlock(targetPos, face);
                    destroyStarted = true;
                } else {
                    mc.gameMode.continueDestroyBlock(targetPos, face);
                }
            }
            case SWITCH_TO_WS -> {
                if (workstationSlot != SLOT_OFFHAND) selectSlot(mc, workstationSlot);
                phase = Phase.PLACE;
                phaseTicks = 0;
            }
            case PLACE -> {
                BlockPos placeAgainst = targetPos.relative(face.getOpposite());
                Direction placeDir    = face;
                if (mc.level.getBlockState(placeAgainst).isAir()) {
                    placeAgainst = targetPos.below();
                    placeDir     = Direction.UP;
                    if (mc.level.getBlockState(placeAgainst).isAir()) {
                        stop("Stopped: no solid block to place against.");
                        return;
                    }
                }
                InteractionHand hand = workstationSlot == SLOT_OFFHAND
                        ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                Vec3 hitVec = new Vec3(
                        placeAgainst.getX() + 0.5 + placeDir.getStepX() * 0.5,
                        placeAgainst.getY() + 0.5 + placeDir.getStepY() * 0.5,
                        placeAgainst.getZ() + 0.5 + placeDir.getStepZ() * 0.5);
                BlockHitResult placeHit = new BlockHitResult(hitVec, placeDir, placeAgainst, false);
                mc.gameMode.useItemOn(mc.player, hand, placeHit);
                mc.player.swing(hand);
                phase = Phase.WAIT_PLACE;
                phaseTicks = 0;
            }
            case WAIT_PLACE -> {
                if (!mc.level.getBlockState(targetPos).isAir()) {
                    completedRerolls++;
                    if (completedRerolls >= plannedRerolls) {
                        stop("Done: " + completedRerolls + "/" + plannedRerolls + " rerolls.");
                    } else {
                        phase = cycleDelayTicks > 0 ? Phase.WAIT_CYCLE : Phase.SWITCH_TO_TOOL;
                        phaseTicks = 0;
                    }
                }
            }
            case WAIT_CYCLE -> {
                if (phaseTicks >= cycleDelayTicks) {
                    phase = Phase.SWITCH_TO_TOOL;
                    phaseTicks = 0;
                }
            }
        }
    }

    private static void selectSlot(Minecraft mc, int slot) {
        Inventory inv = mc.player.getInventory();
        if (inv.getSelectedSlot() == slot) return;
        inv.setSelectedSlot(slot);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static OptionalInt computeRerollsUntilTarget() {
        OptionalLong seedOpt = SeedProvider.getSeed();
        Optional<VillagerData> dataOpt = VillagerTracker.getLastVillagerData();
        if (seedOpt.isEmpty() || dataOpt.isEmpty()) return OptionalInt.empty();

        List<EnchantmentTarget> tgts = VillagerHudRenderer.getTargets();
        if (tgts.isEmpty()) return OptionalInt.empty();

        VillagerData data = dataOpt.get();
        Holder<VillagerProfession> profession = data.profession();
        Optional<ResourceKey<VillagerProfession>> profKey = profession.unwrapKey();
        if (profKey.isEmpty()
                || profKey.get().equals(VillagerProfession.NONE)
                || profKey.get().equals(VillagerProfession.NITWIT)) return OptionalInt.empty();

        long worldSeed = seedOpt.getAsLong();
        ResourceKey<TradeSet> tsKey = TradeDataLoader.getTradeSetKey(profession, data.level());
        int typeOffset    = tsKey != null ? PredictionEngine.getOffset(tsKey) : 0;
        int tradeSetTotal = VillagerTracker.getTradeSetTotal(data);
        UUID uuid = VillagerTracker.getLastVillagerUuid();
        int nowRound = uuid != null
                ? VillagerTracker.getCurrentRound(uuid).orElse(typeOffset + tradeSetTotal)
                : typeOffset + tradeSetTotal;

        PredictionResult best = null;
        for (EnchantmentTarget t : tgts) {
            PredictionResult r = PredictionEngine.predictFrom(
                    worldSeed, profession, data.level(), t, nowRound);
            if (r.found() && (best == null
                    || r.rerollsNeeded().get() < best.rerollsNeeded().get())) {
                best = r;
            }
        }
        if (best == null) return OptionalInt.empty();
        return OptionalInt.of(Math.max(0, best.rerollsNeeded().get() - nowRound));
    }

    private static void abort(Minecraft mc, String msg) {
        lastStatus = msg;
        say(mc, msg);
    }

    private static void say(Minecraft mc, String msg) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[VTP] " + msg));
    }
}
