package com.aeimo.camdozaalfishing;

import java.awt.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ColorUtil;

public class CamdozaalFishingOverlay extends Overlay {
    private static final int MAX_BRIGHTNESS_ALPHA_LEVEL = 255;

    @Inject
    private CamdozaalFishingPlugin plugin;

    @Inject
    private Client client;

    private boolean isRenderingAlertAnimation = false;

    @Override
    public Dimension render(Graphics2D graphics) {
        /*Stroke stroke = new BasicStroke(4.0f);
        Color yellow = new Color(255, 255, 0);
        TileObject object = null;
        Shape clickbox = object.getClickbox();
        if (clickbox != null)
        {
            Color clickBoxColor = ColorUtil.colorWithAlpha(yellow, yellow.getAlpha() / 12);
            OverlayUtil.renderPolygon(graphics, clickbox, yellow, clickBoxColor, stroke);
        }*/



        //OverlayUtil.rend

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
        }

        return null;*/
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
