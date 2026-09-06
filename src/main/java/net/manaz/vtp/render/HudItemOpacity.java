package net.manaz.vtp.render;

/** Opacity belongs to an individual GUI draw, never to the shared item model or atlas. */
public interface HudItemOpacity {
    float vtp$getOpacity();
    void vtp$setOpacity(float opacity);
}