package net.manaz.vtp.mixin;

import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.prediction.PredictionResult;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.TradeDataLoader;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

@Mixin(MerchantScreen.class)
public class MerchantScreenMixin {

    @Inject(method = "extractContents", at = @At("RETURN"))
    private void vtp$renderPrediction(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        List<EnchantmentTarget> targets = VillagerHudRenderer.getTargets();
        if (targets.isEmpty()) return;

        Optional<VillagerData> villagerDataOpt = VillagerTracker.getLastVillagerData();
        if (villagerDataOpt.isEmpty()) return;

        VillagerData villagerData = villagerDataOpt.get();
        Optional<ResourceKey<VillagerProfession>> profKeyOpt = villagerData.profession().unwrapKey();
        if (profKeyOpt.isEmpty()) return;
        if (profKeyOpt.get().equals(VillagerProfession.NONE)
                || profKeyOpt.get().equals(VillagerProfession.NITWIT)) return;

        OptionalLong seedOpt = SeedProvider.getSeed();
        if (seedOpt.isEmpty()) return;

        ResourceKey<TradeSet> tsKey = TradeDataLoader.getTradeSetKey(
                villagerData.profession(), villagerData.level());
        int typeOffset = tsKey != null ? PredictionEngine.getOffset(tsKey) : 0;
        int tradeSetTotal = VillagerTracker.getTradeSetTotal(villagerData);

        UUID lastUuid = VillagerTracker.getLastVillagerUuid();
        int nowRound = lastUuid != null
                ? VillagerTracker.getCurrentRound(lastUuid).orElse(typeOffset + tradeSetTotal)
                : typeOffset + tradeSetTotal;

        PredictionResult best = null;
        EnchantmentTarget bestTarget = null;
        long worldSeed = seedOpt.getAsLong();
        for (EnchantmentTarget tgt : targets) {
            PredictionResult r = PredictionEngine.predictFrom(
                    worldSeed, villagerData.profession(), villagerData.level(), tgt, nowRound);
            if (best == null
                    || (r.found() && (!best.found()
                    || r.rerollsNeeded().get() < best.rerollsNeeded().get()))) {
                best = r;
                bestTarget = tgt;
            }
        }
        if (best == null || bestTarget == null) return;
        PredictionResult result = best;
        EnchantmentTarget target = bestTarget;

        Font font = Minecraft.getInstance().font;
        int x = 4;
        int y = 4;
        int lineHeight = 11;

        String targetName = target.enchantmentKey().identifier().getPath().replace("_", " ");
        String targetLabel = "Target: " + capitalize(targetName) + " " + target.minLevel();
        if (target.maxPrice() >= 0) {
            targetLabel += " (\u2264" + target.maxPrice() + "em)";
        }
        Component targetText = Component.literal(targetLabel);
        graphics.text(font, targetText, x, y, 0xFF55FF55);
        y += lineHeight;

        if (result.found()) {
            int absoluteReroll = result.rerollsNeeded().orElse(0);
            int remaining = absoluteReroll - nowRound;

            Component resultText;
            if (remaining <= 0) {
                resultText = Component.literal(">>> This roll has the target! <<<");
                graphics.text(font, resultText, x, y, 0xFFFFFF00);
            } else {
                resultText = Component.literal("Rerolls remaining: " + remaining);
                graphics.text(font, resultText, x, y, 0xFFFFFF55);
            }
            y += lineHeight;

            for (TradeSimulator.SimulatedOffer offer : result.targetRollOffers()) {
                if (offer.enchantment().isPresent()) {
                    TradeSimulator.EnchantmentResult ench = offer.enchantment().get();
                    String enchName = ench.enchantment().unwrapKey()
                            .map(k -> k.identifier().getPath())
                            .orElse("?");
                    Component offerText = Component.literal("  " + capitalize(enchName.replace("_", " "))
                            + " " + ench.level());
                    graphics.text(font, offerText, x, y, 0xFFFFFFFF);
                    y += lineHeight;
                }
            }
        } else {
            Component notFound = Component.literal("Not found within " + result.maxRerollsSearched() + " rerolls");
            graphics.text(font, notFound, x, y, 0xFFFF5555);
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
