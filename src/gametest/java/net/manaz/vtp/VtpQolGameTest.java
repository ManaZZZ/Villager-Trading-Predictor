package net.manaz.vtp;

import net.manaz.vtp.compat.ClientCompat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.manaz.vtp.gui.*;
import net.manaz.vtp.prediction.*;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.*;
import net.manaz.vtp.trade.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.resources.*;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.trading.*;

import java.util.*;
import static net.manaz.vtp.VtpUiGameTest.*;

public final class VtpQolGameTest implements FabricClientGameTest {
    private static final long SEED = 1234;
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static EditBox input(Screen screen, String name) {
        return screen.children().stream().filter(w -> w instanceof EditBox).map(w -> (EditBox) w)
                .filter(w -> w.getMessage().getString().equals(name)).findFirst().orElseThrow();
    }
    private static Button button(Screen screen, String label) {
        return screen.children().stream().filter(w -> w instanceof Button).map(w -> (Button) w)
                .filter(w -> w.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static void tab(Screen screen, String label) {
        if (button(screen, label).active) click(screen, label);
    }
    private static void choose(Minecraft mc, String enchantment) {
        var price = input(ClientCompat.screen(mc), "Optional base emerald price (1–64)");
        var picker = ClientCompat.screen(mc).children().stream().filter(w -> w instanceof Button).map(w -> (Button) w)
                .filter(w -> w.getX() == price.getX() && w.getY() == price.getY() - 48).findFirst().orElseThrow();
        click(ClientCompat.screen(mc), picker.getMessage().getString());
        input(ClientCompat.screen(mc), "Search enchantments").setValue(enchantment);
        var list = ClientCompat.screen(mc).children().stream().filter(w -> w instanceof VtpList).map(w -> (VtpList) w).findFirst().orElseThrow();
        var names = TradeDataLoader.getTradeableEnchantments().stream().map(e -> e.value().description().getString())
                .sorted().filter(name -> name.toLowerCase(Locale.ROOT).contains(enchantment.toLowerCase(Locale.ROOT))).toList();
        int index = java.util.stream.IntStream.range(0, names.size()).filter(i -> names.get(i).equalsIgnoreCase(enchantment))
                .findFirst().orElseThrow();
        check(list.size() == names.size(), "Picker search results differ for " + enchantment);
        list.offset(index);
        ClientCompat.screen(mc).mouseClicked(new MouseButtonEvent(list.getX() + 12, list.getY() + 10 + 20 * (index - list.offset()),
                new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
    }

    static void verifyRemote(Minecraft mc) throws ReflectiveOperationException {
        var originalMenu = mc.player.containerMenu;
        var dataField = ClientCommonPacketListenerImpl.class.getDeclaredField("serverData");
        dataField.setAccessible(true);
        var oldServerData = dataField.get(mc.getConnection());
        try {
            dataField.set(mc.getConnection(), new ServerData("QoL regression", "qol-test.invalid:25565", ServerData.Type.OTHER));
            SeedProvider.setSeedVisible(false);
            ClientCompat.setScreen(mc, new VtpSettingsScreen());
            tab(ClientCompat.screen(mc), "Settings");
            EditBox seed = input(ClientCompat.screen(mc), "World seed; required on multiplayer");
            check(seed.getValue().equals(String.valueOf(SEED)), "Seed was not restored into its field");
            seed.setValue("-9223372036854775808");
            check(SeedProvider.getSeed().orElseThrow() == Long.MIN_VALUE, "Pasted seed was not applied immediately");
            check(ServerSeedStore.getSeedForServer("qol-test.invalid:25565").orElseThrow() == Long.MIN_VALUE, "GUI seed did not persist per server");
            long epoch = VtpPersistence.epoch();
            seed.setValue("-9223372036854775808");
            check(VtpPersistence.epoch() == epoch, "Unchanged seed reset calibration");
            seed.setValue("invalid");
            check(SeedProvider.getSeed().orElseThrow() == Long.MIN_VALUE && VtpPersistence.epoch() == epoch, "Invalid input changed the active seed");
            seed.setValue(String.valueOf(SEED));
            var format = EditBox.class.getDeclaredMethod("applyFormat", String.class, int.class);
            format.setAccessible(true);
            var formatted = (FormattedCharSequence) format.invoke(seed, seed.getValue(), 0);
            StringBuilder rendered = new StringBuilder();
            formatted.accept((index, style, codepoint) -> { rendered.appendCodePoint(codepoint); return true; });
            check(rendered.toString().equals("****"), "Hidden seed still renders its digits");
            var narration = VtpSeedBox.class.getDeclaredMethod("createNarrationMessage");
            narration.setAccessible(true);
            check(!((Component) narration.invoke(seed)).getString().contains("1234"), "Narration leaked the hidden seed");
            click(ClientCompat.screen(mc), "Show");
            check(SeedProvider.isSeedVisible(), "Show seed did not reveal the field");
            ClientCompat.setScreen(mc, null);
            ClientCompat.setScreen(mc, new VtpSettingsScreen());
            tab(ClientCompat.screen(mc), "Settings");
            check(input(ClientCompat.screen(mc), "World seed; required on multiplayer").getValue().equals("1234"), "Closing settings lost the applied seed");
            click(ClientCompat.screen(mc), "Hide");
            VtpPersistence.flush();
            SeedProvider.setSeedVisible(true);
            VtpPersistence.loadGlobalSettings();
            check(!SeedProvider.isSeedVisible(), "Hidden seed preference did not survive reload");

            var access = mc.level.registryAccess();
            var profession = access.lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.LIBRARIAN);
            var data = new VillagerData(access.lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(VillagerType.PLAINS), profession, 1);
            UUID uuid = UUID.randomUUID();
            VillagerTracker.updateAndSelect(uuid, data);
            ClientCompat.setScreen(mc, new VtpSettingsScreen());
            tab(ClientCompat.screen(mc), "Calibrate");
            choose(mc, "efficiency");
            click(ClientCompat.screen(mc), "Level: " + net.manaz.vtp.render.VillagerHudRenderer.fmtLvl(1));
            click(ClientCompat.screen(mc), "Level: " + net.manaz.vtp.render.VillagerHudRenderer.fmtLvl(2));
            input(ClientCompat.screen(mc), "Optional base emerald price (1–64)").setValue("17");
            ClientCompat.setScreen(mc, null); // Leave to inspect the actual villager, then create a fresh settings screen.
            ClientCompat.setScreen(mc, new VtpSettingsScreen());
            check(button(ClientCompat.screen(mc), "Efficiency") != null, "Draft lost its enchantment");
            check(button(ClientCompat.screen(mc), "Level: " + net.manaz.vtp.render.VillagerHudRenderer.fmtLvl(3)) != null, "Draft lost its level");
            check(input(ClientCompat.screen(mc), "Optional base emerald price (1–64)").getValue().equals("17"), "Draft lost its price");
            ClientCompat.setScreen(mc, null);
            VillagerTracker.updateAndSelect(UUID.randomUUID(), data);
            ClientCompat.setScreen(mc, new VtpSettingsScreen());
            check(button(ClientCompat.screen(mc), "Choose enchantment") != null, "Another villager inherited the draft");
            ClientCompat.setScreen(mc, null);

            VillagerTracker.clear(); PredictionEngine.clearAllOffsets(); CalibrationListStore.clear();
            VillagerTracker.updateAndSelect(uuid, data);
            var menu = new MerchantMenu(77, mc.player.getInventory());
            mc.player.containerMenu = menu;
            menu.setMerchantLevel(1);
            var rounds = PredictionEngine.getUpcomingRounds(SEED, profession, 1, 0, 100);
            int start = 0;
            while (rounds.get(start).stream().anyMatch(o -> o.enchantment().isPresent())) start++;
            boolean discountedBook = false;
            for (int i = 0; i < 5; i++) {
                var expected = TradeSimulator.fingerprint(rounds.get(start + i));
                var offers = offers(mc, expected);
                for (var offer : offers) if (!ObservedOffer.from(offer).books().isEmpty()) {
                    offer.setSpecialPriceDiff(-3); discountedBook = true;
                    check(offer.getCostA().getCount() != offer.getBaseCostA().getCount(), "Discount fixture has no discount");
                }
                VillagerTracker.update(uuid, data.withProfession(access.lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.NONE)));
                VillagerTracker.beginMerchantInteraction(uuid, data);
                menu.setOffers(offers);
                var packet = new ClientboundMerchantOffersPacket(menu.containerId, offers, 1, 0, true, true);
                VillagerTracker.onMerchantOffers(packet);
                ClientCompat.setScreen(mc, new MerchantScreen(menu, mc.player.getInventory(), Component.literal("Librarian")));
                click(ClientCompat.screen(mc), "Add observation");
                check(CalibrationListStore.size() == i + 1, "Merchant button did not append exactly one round");
                check(OfferMatching.same(expected, CalibrationListStore.getAll().getLast().offers()), "Capture used discounted prices or changed offers");
                long revision = VillagerTracker.observationRevision(uuid);
                CalibrationService.capture(menu);
                check(CalibrationListStore.size() == i + 1, "Double-click recorded the same round twice");
                VillagerTracker.beginMerchantInteraction(uuid, data);
                VillagerTracker.onMerchantOffers(packet);
                check(VillagerTracker.observationRevision(uuid) == revision, "Reopening unchanged offers created another round");
                check(!CalibrationService.captureProblem(menu).isEmpty(), "Reopening allowed duplicate capture");
                if (i == 0) check(!CalibrationListStore.feedback().equals("Sequence calibrated."), "Ambiguous no-book round was auto-confirmed");
            }
            check(discountedBook, "Sequence fixture did not include a discounted book");
            check(CalibrationListStore.feedback().equals("Sequence calibrated."), "Capture never signalled successful calibration");
            check(button(ClientCompat.screen(mc), "Calibrated") != null, "Merchant screen did not show calibrated feedback");
            check(VillagerTracker.getNextRoundForLevel(profession, 1) == start + 5, "Automatic calibration chose the wrong next round");

            CalibrationListStore.clear();
            ClientCompat.setScreen(mc, new VtpSettingsScreen());
            tab(ClientCompat.screen(mc), "Calibrate");
            for (int i = 0; i < 5; i++) {
                var book = rounds.get(start + i).stream().flatMap(o -> o.enchantment().stream()).findFirst();
                if (book.isEmpty()) click(ClientCompat.screen(mc), "Add no book");
                else {
                    var value = book.get();
                    choose(mc, value.enchantment().value().description().getString());
                    String target = "Level: " + net.manaz.vtp.render.VillagerHudRenderer.fmtLvl(value.level());
                    for (int step = 0; step < 5; step++) {
                        var levelButton = ClientCompat.screen(mc).children().stream().filter(w -> w instanceof Button).map(w -> (Button) w)
                                .filter(w -> w.getMessage().getString().startsWith("Level: ")).findFirst().orElseThrow();
                        if (levelButton.getMessage().getString().equals(target)) break;
                        click(ClientCompat.screen(mc), levelButton.getMessage().getString());
                    }
                    input(ClientCompat.screen(mc), "Optional base emerald price (1–64)").setValue(String.valueOf(value.emeraldPrice()));
                    click(ClientCompat.screen(mc), "Add observation");
                }
                check(CalibrationListStore.size() == i + 1, "Manual GUI did not append the observation");
            }
            check(CalibrationListStore.feedback().equals("Sequence calibrated."), "Manual GUI did not automatically check calibration");

            CalibrationListStore.clear();
            UUID mendingVillager = UUID.randomUUID();
            VillagerTracker.clearMerchantInteraction();
            VillagerTracker.beginMerchantInteraction(mendingVillager, data);
            var mending = new ObservedOffer("minecraft:enchanted_book", 1, "minecraft:emerald", 12,
                    "minecraft:book", 1, List.of(new ObservedOffer.Book("minecraft:mending", 1)));
            var paper = new ObservedOffer("minecraft:emerald", 1, "minecraft:paper", 24, "minecraft:air", 0, List.of());
            var mendingOffers = offers(mc, List.of(mending, paper));
            mendingOffers.getFirst().setSpecialPriceDiff(-5);
            menu.setOffers(mendingOffers);
            VillagerTracker.onMerchantOffers(new ClientboundMerchantOffersPacket(menu.containerId, mendingOffers, 1, 0, true, true));
            ClientCompat.setScreen(mc, new MerchantScreen(menu, mc.player.getInventory(), Component.literal("Librarian")));
            click(ClientCompat.screen(mc), "Add observation");
            var recorded = CalibrationListStore.getAll().getFirst();
            check(recorded.enchantment().identifier().getPath().equals("mending") && recorded.enchantmentLevel() == 1
                    && recorded.price() == 12, "Mending I for 12 emeralds was not captured correctly");

            // A tier with several books is one round; earlier unlocked books must stay out of that round.
            var additional = List.of(new ObservedOffer("minecraft:enchanted_book", 1, "minecraft:emerald", 17,
                            "minecraft:book", 1, List.of(new ObservedOffer.Book("minecraft:efficiency", 3))),
                    new ObservedOffer("minecraft:enchanted_book", 1, "minecraft:emerald", 15,
                            "minecraft:book", 1, List.of(new ObservedOffer.Book("minecraft:unbreaking", 3))));
            List<ObservedOffer> allOffers = new ArrayList<>(List.of(mending, paper));
            allOffers.addAll(additional);
            var leveledOffers = offers(mc, allOffers);
            VillagerTracker.beginMerchantInteraction(mendingVillager, data.withLevel(2));
            menu.setMerchantLevel(2); menu.setOffers(leveledOffers);
            VillagerTracker.onMerchantOffers(new ClientboundMerchantOffersPacket(menu.containerId, leveledOffers, 2, 10, true, true));
            ClientCompat.setScreen(mc, new MerchantScreen(menu, mc.player.getInventory(), Component.literal("Librarian")));
            click(ClientCompat.screen(mc), "Add observation");
            check(CalibrationListStore.size() == 1 && CalibrationListStore.toSpecs().size() == 1
                    && OfferMatching.same(additional, CalibrationListStore.getAll().getFirst().offers()),
                    "Capture mixed tiers or split multiple books into separate rounds");
            check(!CalibrationListStore.feedback().equals("Sequence calibrated."), "Unsupported multi-book fixture produced a false calibration");
            VillagerTradingPredictor.LOGGER.info("[VTP QOL TEST] PASS: instant persistent seed, privacy/narration, retained draft, "
                    + "captured base prices, no-book and multi-book rounds, duplicate prevention and automatic calibration from both GUIs.");
        } finally {
            ClientCompat.setScreen(mc, null);
            mc.player.containerMenu = originalMenu;
            dataField.set(mc.getConnection(), oldServerData);
            CalibrationDraftStore.clear(); CalibrationListStore.clear();
        }
    }

    private static MerchantOffers offers(Minecraft mc, List<ObservedOffer> observations) {
        var offers = new MerchantOffers();
        for (var observation : observations) {
            var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(observation.result())), observation.count());
            var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (var book : observation.books()) enchantments.set(mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(book.key()))), book.level());
            if (!observation.books().isEmpty()) stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
            offers.add(new MerchantOffer(new ItemCost(BuiltInRegistries.ITEM.getValue(Identifier.parse(observation.costA())), observation.basePrice()),
                    observation.countB() == 0 ? Optional.empty() : Optional.of(new ItemCost(
                            BuiltInRegistries.ITEM.getValue(Identifier.parse(observation.costB())), observation.countB())), stack, 12, 1, 0.2f));
        }
        return offers;
    }

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(mc -> { SeedProvider.setSeedVisible(false); SeedProvider.setManualSeed(SEED); });
            for (int scale : new int[]{1, 2, 3}) {
                context.runOnClient(mc -> {
                    mc.getWindow().setWindowed(scale == 3 ? 960 : 1280, 720);
                    mc.options.guiScale().set(scale); mc.resizeGui();
                    ClientCompat.setScreen(mc, new VtpSettingsScreen() { @Override protected boolean singleplayer() { return false; } });
                    tab(ClientCompat.screen(mc), "Settings");
                    checkLayout(ClientCompat.screen(mc), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                });
                context.waitTicks(3);
                context.takeScreenshot("qol-hidden-seed-scale-" + scale);
                context.runOnClient(mc -> {
                    var menu = new MerchantMenu(78, mc.player.getInventory());
                    menu.setMerchantLevel(1);
                    menu.setOffers(offers(mc, List.of(new ObservedOffer("minecraft:enchanted_book", 1, "minecraft:emerald", 12,
                            "minecraft:book", 1, List.of(new ObservedOffer.Book("minecraft:mending", 1))))));
                    ClientCompat.setScreen(mc, new MerchantScreen(menu, mc.player.getInventory(), Component.literal("Librarian")) {
                        @Override protected void init() {
                            super.init();
                            new MerchantCalibrationControls(menu, leftPos + (imageWidth - 132) / 2,
                                    Math.min(topPos + imageHeight + 4, height - 34), width, widget -> addRenderableWidget(widget));
                        }
                    });
                    checkLayout(ClientCompat.screen(mc), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                });
                context.waitTicks(3);
                context.takeScreenshot("qol-merchant-scale-" + scale);
            }
            context.setScreen(() -> null);
            context.runOnClient(mc -> SeedProvider.clearManualSeed());
        }
    }
}
