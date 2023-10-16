package com.aeimo.camdozaalfishing;

import com.google.common.base.Strings;
import java.awt.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.ColorUtil;

public class CamdozaalFishingOverlay extends Overlay {
    private static final int MAX_BRIGHTNESS_ALPHA_LEVEL = 255;

    @Inject
    private CamdozaalFishingPlugin plugin;

    @Inject
    private Client client;

    private boolean isRenderingAlertAnimation = false;

    @Inject
    private CamdozaalFishingOverlay(Client client, CamdozaalFishingPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    private static final Integer BORDER_WIDTH = 8;

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!this.plugin.isWithinCamdozaal()) {
            return null;
        }

        Stroke stroke = new BasicStroke((float) BORDER_WIDTH);
        if (plugin.isHighlightAltar()) {
            renderColorTileObject(graphics, plugin.getAltar(), stroke);
        }
        if (plugin.isHighlightPreparationTable()) {
            renderColorTileObject(graphics, plugin.getPreparationTable(), stroke);
        }

        CamdozaalFishingPlugin.TrackedNPC closestFishingSpot = plugin.getClosestFishingSpot();
        plugin.getFishingSpots()
                .forEach(spot -> {
                    Color closestHighlightColor = plugin.isHighlightFishingSpot() ? Color.YELLOW : Color.BLUE;
                    highlightFishingSpot(graphics, spot, spot == closestFishingSpot ? closestHighlightColor : Color.BLACK);
                });

        boolean fullAlert = plugin.isDoAlertFull();
        if (fullAlert || plugin.isDoAlertWeak()) {
            Color glowColor = fullAlert ? plugin.getGlowColor() : plugin.getWeakGlowColor();
            graphics.setColor(new Color(
                    glowColor.getRed(),
                    glowColor.getGreen(),
                    glowColor.getBlue(),
                    getBreathingAlpha(plugin.getGlowBreathePeriod(), fullAlert ? 1.0f : 0.5f))
            );
            graphics.fill(getGameWindowRectangle());
        } else {
            isRenderingAlertAnimation = false;
        }

        return null;
    }

    private void highlightFishingSpot(Graphics2D graphics, CamdozaalFishingPlugin.TrackedNPC trackedFishingSpot, Color highlightColor) {
        NPC spotNPC = trackedFishingSpot.getNpc();
        Polygon poly = spotNPC.getCanvasTilePoly();
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, highlightColor);
        }
    }

    private void renderColorTileObject(Graphics2D graphics, ColorTileObject colorTileObject, Stroke stroke) {
        TileObject object = colorTileObject.getTileObject();
        Color color = colorTileObject.getColor();

        if (object.getPlane() != client.getPlane()) {
            return;
        }

        ObjectComposition composition = colorTileObject.getComposition();
        if (composition.getImpostorIds() != null) {
            composition = composition.getImpostor();
            // Only mark the object if the name still matches
            if (composition == null
                    || Strings.isNullOrEmpty(composition.getName())
                    || "null".equals(composition.getName())
                    || !composition.getName().equals(colorTileObject.getName())) {
                return;
            }
        }

        Shape clickBox = object.getClickbox();
        if (clickBox != null) {
            Color clickBoxColor = ColorUtil.colorWithAlpha(color, color.getAlpha() / 12);
            OverlayUtil.renderPolygon(graphics, clickBox, color, clickBoxColor, stroke);
        }
    }

    private Rectangle getGameWindowRectangle() {
        Dimension clientCanvasSize = client.getCanvas().getSize();
        Point clientCanvasLocation = client.getCanvas().getLocation();
        // Need to adjust rectangle position slightly to cover whole game window perfectly (x: -5, y: -20)
        Point adjustedLocation = new Point(clientCanvasLocation.x - 5, clientCanvasLocation.y - 20);

        return new Rectangle(adjustedLocation, clientCanvasSize);
    }

    private int getBreathingAlpha(int breathePeriodMillis, float intensityModifier) {
        double currentMillisOffset = System.currentTimeMillis() % breathePeriodMillis;
        double fractionCycleComplete = currentMillisOffset / breathePeriodMillis;

        int maxIntensityPc = (int) ((float) plugin.getMaxBreatheIntensityPercent() * intensityModifier);
        double fractionAlpha = Math.sin(fractionCycleComplete * 2 * Math.PI);
        double fractionAlphaPositive = (fractionAlpha + 1) / 2;

        // This check forces the animation to start near the dimmest point of the wave (gives a fade-in effect)
        if (isRenderingAlertAnimation || fractionAlphaPositive < 0.025) {
            isRenderingAlertAnimation = true;
            return ((int) (fractionAlphaPositive * MAX_BRIGHTNESS_ALPHA_LEVEL * (maxIntensityPc / 100.0)));
        }
        return 0;
    }
}
