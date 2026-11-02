package net.manaz.vtp.gui;

import net.manaz.vtp.compat.ClientCompat;

import net.manaz.vtp.prediction.CalibrationService;
import net.manaz.vtp.render.VillagerTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MerchantMenu;

import java.util.function.Consumer;

/** One click records one received trade round, including its undiscounted prices. */
public final class MerchantCalibrationControls {
    private final MerchantMenu menu;
    private final VtpButton button;
    private final int screenWidth;
    private String status = "";
    private long statusUntil, capturedRevision = -1;
    private boolean calibrated;

    public MerchantCalibrationControls(MerchantMenu menu, int x, int y, int screenWidth, Consumer<VtpButton> add) {
        this.menu = menu; this.screenWidth = screenWidth;
        button = new VtpButton("Add observation", x, y, 132, this::capture);
        add.accept(button);
        tick();
    }

    private void capture() {
        var result = CalibrationService.capture(menu);
        calibrated = result.calibrated(); status = result.message();
        capturedRevision = VillagerTracker.observationRevision(VillagerTracker.getMenuVillagerUuid());
        statusUntil = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        ClientCompat.overlayMessage(Minecraft.getInstance(), Component.literal("[VTP] " + status)
                .withStyle(calibrated ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        tick();
    }

    public void tick() {
        String problem = CalibrationService.captureProblem(menu);
        button.active = problem.isEmpty();
        boolean sameRound = capturedRevision >= 0
                && capturedRevision == VillagerTracker.observationRevision(VillagerTracker.getMenuVillagerUuid());
        button.setMessage(Component.literal(sameRound && calibrated ? "Calibrated" : "Add observation"));
        button.selected(sameRound && calibrated);
        button.setTooltip(Tooltip.create(Component.literal(problem.isEmpty()
                ? "Record this round's enchantments and base prices, then check calibration." : problem)));
    }

    public void extract(GuiGraphicsExtractor g, int mx, int my) {
        if (status.isEmpty() || System.nanoTime() >= statusUntil) return;
        int x = 6, width = screenWidth - 12;
        VtpUi.text(g, Minecraft.getInstance().font, status, x, button.getY() + 23, width,
                calibrated ? VtpUi.accentText() : VtpUi.TEXT, mx, my);
    }
}
