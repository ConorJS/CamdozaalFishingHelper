package com.aeimo.camdozaalfishing;

import com.google.common.base.Strings;
import java.awt.*;
import java.awt.Point;
import java.util.Arrays;
import java.util.Objects;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.ColorUtil;

public class CamdozaalFishingOverlay extends Overlay {
    private static final int MAX_BRIGHTNESS_ALPHA_LEVEL = 255;

    @Inject
    private CamdozaalFishingPlugin plugin;

    @Inject
    private Client client;

    private CamdozaalFishingConfig config;

    private boolean isRenderingAlertAnimation = false;

    @Inject
    private CamdozaalFishingOverlay(Client client, CamdozaalFishingConfig config, CamdozaalFishingPlugin plugin,
            ModelOutlineRenderer modelOutlineRenderer)
    {
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    //== overlay ====================================================================================================================================

    private static final Integer INVENTORY_SIZE = 28;

    private static final Integer BORDER_WIDTH = 8;

    // TODO(conor) - These could be configured in the plugin itself
    private static final Color OVERRIDE_FULL_INV_OBJECT_COLOR = new Color(0xff00ff00);
    private static final Color OVERRIDE_NON_FULL_INV_OBJECT_COLOR = new Color(0xffff0000);

    private final ModelOutlineRenderer modelOutlineRenderer;

    @Override
    public Dimension render(Graphics2D graphics)
    {
        //Stroke stroke = new BasicStroke((float) config.borderWidth());
        Stroke stroke = new BasicStroke((float) BORDER_WIDTH);

        if (plugin.isHighlightAltar()) {
            renderColorTileObject(graphics, plugin.getAltar(), stroke);
        }
        if (plugin.isHighlightPreparationTable()) {
            renderColorTileObject(graphics, plugin.getPreparationTable(), stroke);
        }
        if (plugin.isHighlightFishingSpot() && plugin.getSouthernMostFishingSpot() != null) {
            Polygon poly = plugin.getSouthernMostFishingSpot().getCanvasTilePoly();
            if (poly != null)
            {
                OverlayUtil.renderPolygon(graphics, poly, Color.YELLOW);
            }
        }

        return null;
    }

    private void renderColorTileObject(Graphics2D graphics, ColorTileObject colorTileObject, Stroke stroke) {
        TileObject object = colorTileObject.getTileObject();
        Color color = colorTileObject.getColor();

        if (object.getPlane() != client.getPlane())
        {
            return;
        }

        ObjectComposition composition = colorTileObject.getComposition();
        if (composition.getImpostorIds() != null)
        {
            // This is a multiloc
            composition = composition.getImpostor();
            // Only mark the object if the name still matches
            if (composition == null
                    || Strings.isNullOrEmpty(composition.getName())
                    || "null".equals(composition.getName())
                    || !composition.getName().equals(colorTileObject.getName()))
            {
                return;
            }
        }

        Shape clickBox = object.getClickbox();
        if (clickBox != null)
        {
            Color clickBoxColor = ColorUtil.colorWithAlpha(color, color.getAlpha() / 12);
            OverlayUtil.renderPolygon(graphics, clickBox, color, clickBoxColor, stroke);
        }

        /*if (color == null || !config.rememberObjectColors())
        {
            // Fallback to the current config if the object is marked before the addition of multiple colors
            color = config.markerColor();
        }*/

        // !! Overrides all previous assignments
        //color = isInventoryFull() ? OVERRIDE_FULL_INV_OBJECT_COLOR : OVERRIDE_NON_FULL_INV_OBJECT_COLOR;

        /*if (config.highlightHull())
        {
            renderConvexHull(graphics, object, color, stroke);
        }

        if (config.highlightOutline())
        {
            modelOutlineRenderer.drawOutline(object, (int)config.borderWidth(), color, config.outlineFeather());
        }

        if (config.highlightClickbox())
        {
            Shape clickbox = object.getClickbox();
            if (clickbox != null)
            {
                Color clickBoxColor = ColorUtil.colorWithAlpha(color, color.getAlpha() / 12);
                OverlayUtil.renderPolygon(graphics, clickbox, color, clickBoxColor, stroke);
            }
        }

        if (config.highlightTile())
        {
            Polygon tilePoly = object.getCanvasTilePoly();
            if (tilePoly != null)
            {
                Color tileColor = ColorUtil.colorWithAlpha(color, color.getAlpha() / 12);
                OverlayUtil.renderPolygon(graphics, tilePoly, color, tileColor, stroke);
            }
        }*/
    }

    private void renderConvexHull(Graphics2D graphics, TileObject object, Color color, Stroke stroke)
    {
        final Shape polygon;
        Shape polygon2 = null;

        if (object instanceof GameObject)
        {
            polygon = ((GameObject) object).getConvexHull();
        }
        else if (object instanceof WallObject)
        {
            polygon = ((WallObject) object).getConvexHull();
            polygon2 = ((WallObject) object).getConvexHull2();
        }
        else if (object instanceof DecorativeObject)
        {
            polygon = ((DecorativeObject) object).getConvexHull();
            polygon2 = ((DecorativeObject) object).getConvexHull2();
        }
        else if (object instanceof GroundObject)
        {
            polygon = ((GroundObject) object).getConvexHull();
        }
        else
        {
            polygon = object.getCanvasTilePoly();
        }

        if (polygon != null)
        {
            OverlayUtil.renderPolygon(graphics, polygon, color, stroke);
        }

        if (polygon2 != null)
        {
            OverlayUtil.renderPolygon(graphics, polygon2, color, stroke);
        }
    }

    // Duplicates zMenuEntryPlugin
    private boolean isInventoryFull() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return false;
        }

        return (Arrays.stream(inventory.getItems())
                .filter(Objects::nonNull)
                // Empty inventory slot
                .filter(i -> i.getId() != -1)
                .count() >= INVENTORY_SIZE);
    }

    //== old ========================================================================================================================================

    public Dimension renderOld(Graphics2D graphics) {
        /*if (plugin.playerIsAfk()) {
            Color glowColor = plugin.getGlowColor();
            graphics.setColor(new Color(
                    glowColor.getRed(),
                    glowColor.getGreen(),
                    glowColor.getBlue(),
                    getBreathingAlpha(plugin.getGlowBreathePeriod()))
            );

            graphics.fill(getGameWindowRectangle());
        } else {
            isRenderingAlertAnimation = false;
        }*/
        return null;
    }

    private Rectangle getGameWindowRectangle() {
        Dimension clientCanvasSize = client.getCanvas().getSize();
        Point clientCanvasLocation = client.getCanvas().getLocation();
        // Need to adjust rectangle position slightly to cover whole game window perfectly (x: -5, y: -20)
        Point adjustedLocation = new Point(clientCanvasLocation.x - 5, clientCanvasLocation.y - 20);

        return new Rectangle(adjustedLocation, clientCanvasSize);
    }

    private int getBreathingAlpha(int breathePeriodMillis) {
        double currentMillisOffset = System.currentTimeMillis() % breathePeriodMillis;
        double fractionCycleComplete = currentMillisOffset / breathePeriodMillis;

        int maxIntensityPc = plugin.getMaxBreatheIntensityPercent();
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
