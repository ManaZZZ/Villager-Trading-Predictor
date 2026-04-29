package net.manaz.vtp.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import java.util.Arrays;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.autoreroll.AutoRerollManager;
import net.manaz.vtp.gui.VtpSettingsScreen;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.seed.ServerSeedStore;
import net.manaz.vtp.trade.TradeDataLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.enchantment.Enchantment;

public class VtpCommand {

    /** Single-word enchantment suggestions (used by /vtp target). */
    private static final SuggestionProvider<FabricClientCommandSource> ENCHANTMENT_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        for (Holder<Enchantment> holder : TradeDataLoader.getTradeableEnchantments()) {
            holder.unwrapKey().ifPresent(key -> {
                String name = key.identifier().getPath();
                if (name.startsWith(remaining) || remaining.isEmpty()) {
                    builder.suggest(name);
                }
            });
        }
        return builder.buildFuture();
    };

    /**
     * Multi-enchantment suggestions for the greedy calibrate argument.
     * Accepts ';' (preferred) or ',' as the separator between specs so chat
     * autocomplete can complete each enchantment cleanly without swallowing
     * a following comma.
     */
    private static final SuggestionProvider<FabricClientCommandSource> MULTI_ENCHANT_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemaining();
        int lastSep = Math.max(remaining.lastIndexOf(';'), remaining.lastIndexOf(','));
        String currentPart = (lastSep >= 0 ? remaining.substring(lastSep + 1) : remaining).stripLeading();
        String currentLower = currentPart.toLowerCase();
        String keepPrefix = lastSep >= 0 ? remaining.substring(0, lastSep + 1) + " " : "";

        for (Holder<Enchantment> holder : TradeDataLoader.getTradeableEnchantments()) {
            holder.unwrapKey().ifPresent(key -> {
                String name = key.identifier().getPath();
                if (name.startsWith(currentLower) || currentLower.isEmpty()) {
                    builder.suggest(keepPrefix + name);
                }
            });
        }
        return builder.buildFuture();
    };

    /** Suggests 1-based indices for /vtp target remove, with enchantment name/level as tooltip text. */
    private static final SuggestionProvider<FabricClientCommandSource> TARGET_INDEX_SUGGESTIONS = (ctx, builder) -> {
        java.util.List<EnchantmentTarget> tgts = VillagerHudRenderer.getTargets();
        for (int i = 0; i < tgts.size(); i++) {
            builder.suggest(i + 1);
        }
        return builder.buildFuture();
    };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("vtp")
                    // /vtp  (no args) → open settings GUI
                    .executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        mc.execute(() -> mc.setScreen(new VtpSettingsScreen()));
                        return 1;
                    })
                    // /vtp seed [value]
                    .then(ClientCommands.literal("seed")
                            .then(ClientCommands.argument("value", LongArgumentType.longArg())
                                    .executes(ctx -> {
                                        long seed = LongArgumentType.getLong(ctx, "value");
                                        SeedProvider.setManualSeed(seed);
                                        VtpPersistence.load(seed);
                                        // Persist seed for this server so it auto-restores on reconnect
                                        var serverData = Minecraft.getInstance().getCurrentServer();
                                        if (serverData != null) {
                                            ServerSeedStore.saveSeedForServer(serverData.ip, seed);
                                        }
                                        ctx.getSource().sendFeedback(
                                                Component.literal("[VTP] Seed set to: " + seed));
                                        return 1;
                                    })
                            )
                            .executes(ctx -> {
                                var seedOpt = SeedProvider.getSeed();
                                if (seedOpt.isPresent()) {
                                    ctx.getSource().sendFeedback(
                                            Component.literal("[VTP] Current seed: " + seedOpt.getAsLong()));
                                } else {
                                    ctx.getSource().sendFeedback(
                                            Component.literal("[VTP] No seed available. Use /vtp seed <value> to set one."));
                                }
                                return 1;
                            })
                    )
                    // /vtp target [<enchantment> [level [maxprice]]] | clear
                    .then(ClientCommands.literal("target")
                            .then(ClientCommands.literal("clear")
                                    .executes(ctx -> {
                                        VillagerHudRenderer.clearTargets();
                                        VtpPersistence.saveCurrentWorldState();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("[VTP] All targets cleared."));
                                        return 1;
                                    })
                            )
                            .then(ClientCommands.literal("remove")
                                    .then(ClientCommands.argument("index", IntegerArgumentType.integer(1))
                                            .suggests(TARGET_INDEX_SUGGESTIONS)
                                            .executes(ctx -> {
                                                int idx = IntegerArgumentType.getInteger(ctx, "index");
                                                java.util.List<EnchantmentTarget> tgts = VillagerHudRenderer.getTargets();
                                                if (idx < 1 || idx > tgts.size()) {
                                                    ctx.getSource().sendFeedback(Component.literal(
                                                            "[VTP] No target at index " + idx
                                                            + " (active: " + tgts.size() + ")."));
                                                    return 0;
                                                }
                                                EnchantmentTarget removed = tgts.get(idx - 1);
                                                VillagerHudRenderer.removeTarget(idx - 1);
                                                VtpPersistence.saveCurrentWorldState();
                                                ctx.getSource().sendFeedback(Component.literal(
                                                        "[VTP] Removed target: "
                                                        + removed.enchantmentKey().identifier().getPath()
                                                        + " " + removed.minLevel()));
                                                return 1;
                                            })
                                    )
                            )
                            .then(ClientCommands.argument("enchantment", StringArgumentType.word())
                                    .suggests(ENCHANTMENT_SUGGESTIONS)
                                    .then(ClientCommands.argument("level", IntegerArgumentType.integer(1, 10))
                                            .then(ClientCommands.argument("maxprice", IntegerArgumentType.integer(1))
                                                    .executes(ctx -> {
                                                        String enchName = StringArgumentType.getString(ctx, "enchantment");
                                                        int level = IntegerArgumentType.getInteger(ctx, "level");
                                                        int maxPrice = IntegerArgumentType.getInteger(ctx, "maxprice");
                                                        return addTarget(ctx.getSource(), enchName, level, maxPrice);
                                                    })
                                            )
                                            .executes(ctx -> {
                                                String enchName = StringArgumentType.getString(ctx, "enchantment");
                                                int level = IntegerArgumentType.getInteger(ctx, "level");
                                                return addTarget(ctx.getSource(), enchName, level, -1);
                                            })
                                    )
                                    .executes(ctx -> {
                                        String enchName = StringArgumentType.getString(ctx, "enchantment");
                                        return addTarget(ctx.getSource(), enchName, 1, -1);
                                    })
                            )
                            .executes(ctx -> {
                                java.util.List<EnchantmentTarget> tgts = VillagerHudRenderer.getTargets();
                                if (tgts.isEmpty()) {
                                    ctx.getSource().sendFeedback(
                                            Component.literal("[VTP] No targets set. Use /vtp target <enchantment> [level]"));
                                } else {
                                    StringBuilder sb = new StringBuilder("[VTP] Active targets:");
                                    for (int i = 0; i < tgts.size(); i++) {
                                        EnchantmentTarget t = tgts.get(i);
                                        sb.append("\n  ").append(i + 1).append(". ")
                                          .append(t.enchantmentKey().identifier().getPath())
                                          .append(' ').append(t.minLevel());
                                        if (t.maxPrice() >= 0) sb.append(" (max ").append(t.maxPrice()).append(" em)");
                                    }
                                    ctx.getSource().sendFeedback(Component.literal(sb.toString()));
                                }
                                return 1;
                            })
                    )
                    // /vtp toggle
                    .then(ClientCommands.literal("toggle")
                            .executes(ctx -> {
                                boolean newState = !VillagerHudRenderer.isEnabled();
                                VillagerHudRenderer.setEnabled(newState);
                                VtpPersistence.saveGlobalSettings();
                                ctx.getSource().sendFeedback(
                                        Component.literal("[VTP] Overlay " + (newState ? "enabled" : "disabled")));
                                return 1;
                            })
                    )
                    // /vtp maxrerolls <count>
                    .then(ClientCommands.literal("maxrerolls")
                            .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 10000))
                                    .executes(ctx -> {
                                        int count = IntegerArgumentType.getInteger(ctx, "count");
                                        PredictionEngine.setMaxRerolls(count);
                                        VtpPersistence.saveGlobalSettings();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("[VTP] Max rerolls set to: " + count));
                                        return 1;
                                    })
                            )
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(
                                        Component.literal("[VTP] Max rerolls: " + PredictionEngine.getMaxRerolls()));
                                return 1;
                            })
                    )
                    // /vtp configure <villagerlevel> <enchantment> <enchantlevel> <price>
                    .then(ClientCommands.literal("configure")
                            // /vtp configure clear [villagerlevel]
                            .then(ClientCommands.literal("clear")
                                    .then(ClientCommands.argument("villagerlevel", IntegerArgumentType.integer(1, 5))
                                            .executes(ctx -> {
                                                int lvl = IntegerArgumentType.getInteger(ctx, "villagerlevel");
                                                return configureClear(ctx.getSource(), lvl);
                                            })
                                    )
                                    .executes(ctx -> configureClear(ctx.getSource(), -1))
                            )
                            // /vtp configure <villagerlevel> clear | <enchantment> <enchantlevel> <price>
                            .then(ClientCommands.argument("villagerlevel", IntegerArgumentType.integer(1, 5))
                                    .then(ClientCommands.literal("clear")
                                            .executes(ctx -> {
                                                int lvl = IntegerArgumentType.getInteger(ctx, "villagerlevel");
                                                return configureClear(ctx.getSource(), lvl);
                                            })
                                    )
                                    .then(ClientCommands.argument("enchantment", StringArgumentType.word())
                                            .suggests(ENCHANTMENT_SUGGESTIONS)
                                            .then(ClientCommands.argument("enchantlevel", IntegerArgumentType.integer(1, 10))
                                                    .then(ClientCommands.argument("price", IntegerArgumentType.integer(1, 64))
                                                            .executes(ctx -> {
                                                                int vLvl    = IntegerArgumentType.getInteger(ctx, "villagerlevel");
                                                                String ench = StringArgumentType.getString(ctx, "enchantment");
                                                                int eLvl    = IntegerArgumentType.getInteger(ctx, "enchantlevel");
                                                                int price   = IntegerArgumentType.getInteger(ctx, "price");
                                                                return configureLevel(ctx.getSource(), vLvl, ench, eLvl, price);
                                                            })
                                                    )
                                            )
                                    )
                            )
                            // /vtp configure  →  show current saved offers for looked-at villager
                            .executes(ctx -> {
                                Entity crosshair = Minecraft.getInstance().crosshairPickEntity;
                                if (!(crosshair instanceof Villager villager)) {
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "[VTP] Look at a villager first."));
                                    return 0;
                                }
                                java.util.UUID uuid = villager.getUUID();
                                StringBuilder sb = new StringBuilder("[VTP] Saved offers for this villager:");
                                for (int lvl = 1; lvl <= 5; lvl++) {
                                    java.util.List<VillagerTracker.OfferData> saved =
                                            VillagerTracker.getSavedLevelOffers(uuid, lvl);
                                    sb.append("\n  Lv.").append(lvl).append(": ");
                                    if (saved.isEmpty()) {
                                        sb.append("(not recorded)");
                                    } else {
                                        for (VillagerTracker.OfferData od : saved) {
                                            String name = od.enchantmentKey()
                                                    .substring(od.enchantmentKey().lastIndexOf(':') + 1)
                                                    .replace("_", " ");
                                            sb.append(name).append(' ').append(od.enchantmentLevel())
                                              .append("  (").append(od.additionalCost()).append(" em)");
                                        }
                                    }
                                }
                                ctx.getSource().sendFeedback(Component.literal(sb.toString()));
                                return 1;
                            })
                    )
                    // /vtp calibrate fire_aspect 2; knockback 2; mending   (';' or ',' both accepted)
                    // /vtp calibrate confirm <round>
                    // /vtp calibrate reset
                    .then(ClientCommands.literal("calibrate")
                            .then(ClientCommands.literal("reset")
                                    .executes(ctx -> {
                                        PredictionEngine.clearAllOffsets();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("[VTP] Calibration reset. All offsets = 0 (fresh world assumed)."));
                                        return 1;
                                    })
                            )
                            .then(ClientCommands.literal("confirm")
                                    .then(ClientCommands.argument("round", IntegerArgumentType.integer(0))
                                            .executes(ctx -> {
                                                int round = IntegerArgumentType.getInteger(ctx, "round");
                                                return confirmCalibrate(ctx.getSource(), round);
                                            })
                                    )
                            )
                            // Greedy string: "fire_aspect 2; knockback 2; mending"  (',' also accepted)
                            .then(ClientCommands.argument("enchantments", StringArgumentType.greedyString())
                                    .suggests(MULTI_ENCHANT_SUGGESTIONS)
                                    .executes(ctx -> {
                                        String spec = StringArgumentType.getString(ctx, "enchantments");
                                        return runCalibrate(ctx.getSource(), spec);
                                    })
                            )
                            .executes(ctx -> {
                                var offsets = PredictionEngine.getAllOffsets();
                                String msg = offsets.isEmpty()
                                        ? "[VTP] No calibrations set. Usage: /vtp calibrate <ench> [lvl]; <ench> [lvl]; ... — look at your villager first."
                                        : "[VTP] Calibrated offsets: " + offsets.size() + " trade set(s). Use 'reset' to clear all.";
                                ctx.getSource().sendFeedback(Component.literal(msg));
                                return 1;
                            })
                    )
                    // /vtp reroll  |  /vtp reroll start | stop
                    // AutoRerollManager.stop(String) prints its own "[VTP] <msg>" chat line,
                    // so stop paths avoid an extra sendFeedback to prevent duplicates.
                    .then(ClientCommands.literal("reroll")
                            .then(ClientCommands.literal("start")
                                    .executes(ctx -> {
                                        if (AutoRerollManager.isRunning()) {
                                            ctx.getSource().sendFeedback(Component.literal("[VTP] Auto-reroll already running."));
                                            return 0;
                                        }
                                        AutoRerollManager.toggle();
                                        ctx.getSource().sendFeedback(Component.literal("[VTP] Auto-reroll started."));
                                        return 1;
                                    })
                            )
                            .then(ClientCommands.literal("stop")
                                    .executes(ctx -> {
                                        if (!AutoRerollManager.isRunning()) {
                                            ctx.getSource().sendFeedback(Component.literal("[VTP] Auto-reroll is not running."));
                                            return 0;
                                        }
                                        AutoRerollManager.stop("Auto-reroll stopped.");
                                        return 1;
                                    })
                            )
                            .executes(ctx -> {
                                if (AutoRerollManager.isRunning()) {
                                    AutoRerollManager.stop("Auto-reroll stopped.");
                                } else {
                                    AutoRerollManager.toggle();
                                    ctx.getSource().sendFeedback(Component.literal("[VTP] Auto-reroll started."));
                                }
                                return 1;
                            })
                    )
                    // /vtp help
                    .then(ClientCommands.literal("help")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Component.literal(
                                        "[VTP] Commands:"
                                        + "\n  /vtp                                  Open settings GUI"
                                        + "\n  /vtp help                             Show this help"
                                        + "\n  /vtp status                           Show current state"
                                        + "\n  /vtp seed [value]                     Get or set world seed"
                                        + "\n  /vtp toggle                           Toggle HUD overlay"
                                        + "\n  /vtp maxrerolls <count>               Set max search limit"
                                        + "\n  /vtp target [<ench> [lvl] [price]]    List or add targets"
                                        + "\n  /vtp target remove <index>            Remove target by index"
                                        + "\n  /vtp target clear                     Clear all targets"
                                        + "\n  /vtp configure [<lvl> <ench> <l> <p>] View or set saved offers"
                                        + "\n  /vtp configure [<lvl>] clear          Clear saved offers"
                                        + "\n  /vtp calibrate <ench> [lvl]; ...      Auto-find RNG offset"
                                        + "\n  /vtp calibrate confirm <round>        Apply specific match"
                                        + "\n  /vtp calibrate reset                  Clear all offsets"
                                        + "\n  /vtp reroll [start|stop]              Toggle auto-reroll"));
                                return 1;
                            })
                    )
                    // /vtp status
                    .then(ClientCommands.literal("status")
                            .executes(ctx -> {
                                var seedOpt = SeedProvider.getSeed();
                                java.util.List<EnchantmentTarget> tgts = VillagerHudRenderer.getTargets();
                                var offsets = PredictionEngine.getAllOffsets();

                                StringBuilder sb = new StringBuilder("[VTP] Status:");
                                sb.append("\n  Seed: ").append(seedOpt.isPresent() ? seedOpt.getAsLong() : "(none)");
                                sb.append("\n  Overlay: ").append(VillagerHudRenderer.isEnabled() ? "on" : "off");
                                sb.append("\n  Auto-reroll: ").append(AutoRerollManager.isRunning() ? "running" : "idle");
                                if (AutoRerollManager.isRunning()) {
                                    sb.append(" (").append(AutoRerollManager.getCompletedRerolls())
                                      .append('/').append(AutoRerollManager.getPlannedRerolls()).append(')');
                                }
                                sb.append("\n  Max rerolls: ").append(PredictionEngine.getMaxRerolls());
                                sb.append("\n  Calibrated trade sets: ").append(offsets.size());
                                if (tgts.isEmpty()) {
                                    sb.append("\n  Targets: (none)");
                                } else {
                                    sb.append("\n  Targets:");
                                    for (int i = 0; i < tgts.size(); i++) {
                                        EnchantmentTarget t = tgts.get(i);
                                        sb.append("\n    ").append(i + 1).append(". ")
                                          .append(t.enchantmentKey().identifier().getPath())
                                          .append(' ').append(t.minLevel());
                                        if (t.maxPrice() >= 0) sb.append(" (max ").append(t.maxPrice()).append(" em)");
                                    }
                                }
                                ctx.getSource().sendFeedback(Component.literal(sb.toString()));
                                return 1;
                            })
                    )
            );
        });
    }

    /**
     * Parses a comma-separated list of "enchantment [level]" specs into an ordered
     * sequence. Each spec represents one consecutive reroll round.
     * Examples: "fire_aspect 2, knockback 2, mending" or "mending 1"
     * Spaces within enchantment names are converted to underscores.
     * Level is optional (defaults to 1).
     */
    private static java.util.List<PredictionEngine.CalibrationSpec> parseEnchantSpecs(String input) {
        java.util.List<PredictionEngine.CalibrationSpec> result = new java.util.ArrayList<>();
        // Accept ';' (preferred, autocomplete-friendly) or ',' as separators.
        for (String part : input.split("[;,]")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            String[] tokens = part.split("\\s+");
            int level = 1;
            int nameEnd = tokens.length;
            if (tokens.length > 1) {
                try {
                    level = Integer.parseInt(tokens[tokens.length - 1]);
                    nameEnd = tokens.length - 1;
                } catch (NumberFormatException ignored) {}
            }
            // Join name tokens with underscore (handles "fire aspect 2" → "fire_aspect")
            String name = String.join("_", Arrays.copyOf(tokens, nameEnd)).toLowerCase();
            Identifier enchId = name.contains(":")
                    ? Identifier.parse(name)
                    : Identifier.fromNamespaceAndPath("minecraft", name);
            result.add(new PredictionEngine.CalibrationSpec(
                    ResourceKey.create(Registries.ENCHANTMENT, enchId), level));
        }
        return result;
    }

    private static int runCalibrate(FabricClientCommandSource source, String spec) {
        Entity crosshair = Minecraft.getInstance().crosshairPickEntity;
        if (!(crosshair instanceof Villager villager)) {
            source.sendFeedback(Component.literal("[VTP] Look at a villager first, then run /vtp calibrate."));
            return 0;
        }
        var seedOpt = SeedProvider.getSeed();
        if (seedOpt.isEmpty()) {
            source.sendFeedback(Component.literal("[VTP] No seed set. Use /vtp seed <value> first."));
            return 0;
        }

        java.util.List<PredictionEngine.CalibrationSpec> specs = parseEnchantSpecs(spec);
        if (specs.isEmpty()) {
            source.sendFeedback(Component.literal("[VTP] No valid enchantments parsed. Example: fire_aspect 2; knockback 2; mending"));
            return 0;
        }

        VillagerData data = villager.getVillagerData();
        java.util.List<Integer> matches = PredictionEngine.findCalibrationSequence(
                seedOpt.getAsLong(), data.profession(), data.level(), specs);

        if (matches.isEmpty()) {
            String hint = specs.size() == 1
                    ? "[VTP] No round found with that enchantment within "
                    : "[VTP] No matching sequence of " + specs.size() + " consecutive rerolls found within ";
            source.sendFeedback(Component.literal(
                    hint + PredictionEngine.getMaxRerolls()
                    + " rounds. Check your seed, order, and enchantment names."));
            return 0;
        }

        if (matches.size() == 1) {
            applyAndConfirm(source, villager, data, matches.get(0));
            return 1;
        }

        StringBuilder sb = new StringBuilder("[VTP] Current round matches at: ");
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(matches.get(i));
        }
        sb.append(". Add more rerolls to the sequence or use /vtp calibrate confirm <round>.");
        source.sendFeedback(Component.literal(sb.toString()));
        return 1;
    }

    private static int confirmCalibrate(FabricClientCommandSource source, int round) {
        Entity crosshair = Minecraft.getInstance().crosshairPickEntity;
        if (!(crosshair instanceof Villager villager)) {
            source.sendFeedback(Component.literal("[VTP] Look at the same villager, then confirm."));
            return 0;
        }
        applyAndConfirm(source, villager, villager.getVillagerData(), round);
        return 1;
    }

    private static void applyAndConfirm(FabricClientCommandSource source,
                                         Villager villager, VillagerData data, int round) {
        PredictionEngine.applyCalibration(data.profession(), data.level(), round);
        VillagerTracker.resetAfterCalibrate(villager.getUUID(), data, round);
        source.sendFeedback(Component.literal(
                "[VTP] Calibrated! Offset = " + round + " for "
                + data.profession().unwrapKey()
                        .map(k -> k.identifier().getPath()).orElse("?")
                + " Lv." + data.level() + "."));
    }

    private static int configureLevel(FabricClientCommandSource source,
                                       int villagerLevel, String enchName,
                                       int enchLevel, int price) {
        Entity crosshair = Minecraft.getInstance().crosshairPickEntity;
        if (!(crosshair instanceof Villager villager)) {
            source.sendFeedback(Component.literal("[VTP] Look at a villager first."));
            return 0;
        }
        Identifier id = enchName.contains(":")
                ? Identifier.parse(enchName)
                : Identifier.fromNamespaceAndPath("minecraft", enchName);
        String enchKey = id.toString();

        VillagerTracker.OfferData offer = new VillagerTracker.OfferData(enchKey, enchLevel, price);
        boolean ok = VillagerTracker.setLevelOffer(villager.getUUID(), villagerLevel, offer);
        if (!ok) {
            source.sendFeedback(Component.literal(
                    "[VTP] Villager not tracked yet — look at it for a moment first."));
            return 0;
        }
        var seedOpt = SeedProvider.getSeed();
        if (seedOpt.isPresent()) {
            VtpPersistence.save(seedOpt.getAsLong());
        }
        source.sendFeedback(Component.literal(
                "[VTP] Lv." + villagerLevel + " set to: "
                + enchName.replace("_", " ") + " " + enchLevel
                + "  (" + price + " em)"));
        return 1;
    }

    private static int configureClear(FabricClientCommandSource source, int villagerLevel) {
        Entity crosshair = Minecraft.getInstance().crosshairPickEntity;
        if (!(crosshair instanceof Villager villager)) {
            source.sendFeedback(Component.literal("[VTP] Look at a villager first."));
            return 0;
        }
        boolean ok = VillagerTracker.clearLevelOffer(villager.getUUID(), villagerLevel);
        if (!ok) {
            source.sendFeedback(Component.literal(
                    "[VTP] Villager not tracked yet — look at it for a moment first."));
            return 0;
        }
        var seedOpt = SeedProvider.getSeed();
        if (seedOpt.isPresent()) {
            VtpPersistence.save(seedOpt.getAsLong());
        }
        String levelStr = villagerLevel == -1 ? "all levels" : "Lv." + villagerLevel;
        source.sendFeedback(Component.literal(
                "[VTP] Cleared saved offers for " + levelStr + ". Simulation will be used."));
        return 1;
    }

    private static int addTarget(FabricClientCommandSource source, String enchName, int level, int maxPrice) {
        Identifier id = enchName.contains(":")
                ? Identifier.parse(enchName)
                : Identifier.fromNamespaceAndPath("minecraft", enchName);

        ResourceKey<Enchantment> enchKey = ResourceKey.create(Registries.ENCHANTMENT, id);
        EnchantmentTarget target = new EnchantmentTarget(enchKey, level, maxPrice);
        VillagerHudRenderer.addTarget(target);
        VtpPersistence.saveCurrentWorldState();

        String msg = "[VTP] Target added: " + enchName.replace("_", " ") + " " + level;
        if (maxPrice >= 0) msg += " (max " + maxPrice + " emeralds)";
        source.sendFeedback(Component.literal(msg));
        return 1;
    }
}
