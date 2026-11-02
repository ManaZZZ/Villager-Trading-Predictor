package net.manaz.vtp.compat;

import net.fabricmc.loader.api.FabricLoader;

public final class BuildInfo {
    private BuildInfo() {}
    public static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
    }
    public static String minecraftFamily() {
        String[] parts = minecraftVersion().split("[.]");
        return parts[0] + "." + parts[1];
    }
}
