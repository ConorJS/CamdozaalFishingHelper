package com.aeimo.camdozaalfishing;

import com.google.common.base.Strings;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
        // TODO(conor) Make this configurable, or remove this fragment
        //Stroke stroke = new BasicStroke((float) config.borderWidth());
        Stroke stroke = new BasicStroke((float) BORDER_WIDTH);

        if (plugin.isHighlightAltar()) {
            renderColorTileObject(graphics, plugin.getAltar(), stroke);
        }
        if (plugin.isHighlightPreparationTable()) {
            renderColorTileObject(graphics, plugin.getPreparationTable(), stroke);
        }
        CamdozaalFishingPlugin.TrackedNPC closestFishingSpot = plugin.getClosestFishingSpot();
        NPC fishingSpot = closestFishingSpot.getNpc();
        if (plugin.isHighlightFishingSpot() && fishingSpot != null) {
            Polygon poly = fishingSpot.getCanvasTilePoly();
            // At the end of a fishing spot's lifespan, is enters a state (TODO what?)...
            boolean oddState = false;
            if (fishingSpot.isDead()) {
                oddState = true;
                String textOverlay = "Dead fishing spot";
                net.runelite.api.Point textLoc = fishingSpot.getCanvasTextLocation(graphics, textOverlay, 36);
                OverlayUtil.renderTextLocation(graphics, textLoc, textOverlay, Color.RED);
            } else if (fishingSpot.getComposition() != null && fishingSpot.getComposition().isInteractible()) {
                oddState = true;
                String textOverlay = "Not clickable";
                net.runelite.api.Point textLoc = fishingSpot.getCanvasTextLocation(graphics, textOverlay, 36);
                OverlayUtil.renderTextLocation(graphics, textLoc, textOverlay, Color.RED);
            }
            if (poly != null) {
                OverlayUtil.renderPolygon(graphics, poly, oddState ? Color.RED : Color.YELLOW);
            }
        }

        // TODO(conor) - Remove later, diagnosing weird fish spot issue
        plugin.getFishingSpots().stream()
                //.filter(spot -> spot != fishingSpot)
                .forEach(spot -> {
                    Color closestHighlightColor = plugin.isHighlightFishingSpot() ? Color.YELLOW : Color.BLUE;
                    highlightFishingSpotWithDate(graphics, spot, spot == closestFishingSpot ? closestHighlightColor : Color.BLACK);
                });

        if (plugin.isHighlightFishingSpot() && fishingSpot != null) {
            Polygon poly = fishingSpot.getCanvasTilePoly();
            // At the end of a fishing spot's lifespan, is enters a state (TODO what?)...
            // TODO(conor) - Remove this; debugging only
            boolean oddState = false;
            if (fishingSpot.isDead()) {
                oddState = true;
                String textOverlay = "Dead fishing spot";
                net.runelite.api.Point textLoc = fishingSpot.getCanvasTextLocation(graphics, textOverlay, 0);
                OverlayUtil.renderTextLocation(graphics, textLoc, textOverlay, Color.RED);
            } else if (fishingSpot.getComposition() != null && !fishingSpot.getComposition().isInteractible()) {
                oddState = true;
                String textOverlay = "Not clickable";
                net.runelite.api.Point textLoc = fishingSpot.getCanvasTextLocation(graphics, textOverlay, 0);
                OverlayUtil.renderTextLocation(graphics, textLoc, textOverlay, Color.RED);
            }
            // TODO(conor) - Temporarily replaced by debug highlighting above
            /*if (poly != null) {
                OverlayUtil.renderPolygon(graphics, poly, oddState ? Color.RED : Color.YELLOW);
            }*/
        }

        boolean fullAlert = plugin.isDoAlertFull(false);
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

    private void highlightFishingSpotWithDate(Graphics2D graphics, CamdozaalFishingPlugin.TrackedNPC trackedFishingSpot, Color highlightColor) {
        NPC spotNPC = trackedFishingSpot.getNpc();
        Polygon poly = spotNPC.getCanvasTilePoly();
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, highlightColor);

            long instantiationDiff = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - trackedFishingSpot.getInstantiationDate().toEpochSecond(ZoneOffset.UTC);
            Color textColor1 = new Color(0, asLogScaledFractionOf250(instantiationDiff), 0);

            String textOverlay1 = String.format("%d seconds old", instantiationDiff);
            net.runelite.api.Point textLoc1 = spotNPC.getCanvasTextLocation(graphics, textOverlay1, 0);
            OverlayUtil.renderTextLocation(graphics, textLoc1, textOverlay1, textColor1);

            long moveDiff = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - trackedFishingSpot.getLastMoveDate().toEpochSecond(ZoneOffset.UTC);
            Color textColor2 = new Color(0, asLogScaledFractionOf250(moveDiff), 0);

            String textOverlay2 = String.format("%d seconds here [%d,%d]", moveDiff, spotNPC.getWorldLocation().getX(), spotNPC.getWorldLocation().getY());
            net.runelite.api.Point textLoc2 = new net.runelite.api.Point(textLoc1.getX(), textLoc1.getY() + 18);
            OverlayUtil.renderTextLocation(graphics, textLoc2, textOverlay2, textColor2);
        }
    }

    private int asLogScaledFractionOf250(double value) {
        // Green(g=255) at 0s,
        // Black(g=0) at 100,000s (~1.2day)
        // log10(100,000) = 5, so this won't get closer to black than this.
        return Math.min(255, (int) (255.0 - (Math.min(5.0, Math.log10(value)) * 51.0)));
    }

    private void renderColorTileObject(Graphics2D graphics, ColorTileObject colorTileObject, Stroke stroke) {
        TileObject object = colorTileObject.getTileObject();
        Color color = colorTileObject.getColor();

        if (object.getPlane() != client.getPlane()) {
            return;
        }

        ObjectComposition composition = colorTileObject.getComposition();
        if (composition.getImpostorIds() != null) {
            // This is a multiloc
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
