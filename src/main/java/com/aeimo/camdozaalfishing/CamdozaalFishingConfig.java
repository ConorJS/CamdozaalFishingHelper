package com.aeimo.camdozaalfishing;

import java.awt.Color;
import net.runelite.client.config.*;

@ConfigGroup("camdozaalfishing")
public interface CamdozaalFishingConfig extends Config {
    int DEFAULT_GLOW_BREATHE_PERIOD_MS = 1_000;

    int DEFAULT_PRE_EMPTIVE_DELAY_MS = 1_200;

    int DEFAULT_MAX_GLOW_BREATHE_INTENSITY = 60;

    Color DEFAULT_GLOW_COLOR = new Color(255, 0, 0);

    Color DEFAULT_WEAK_GLOW_COLOR = new Color(255, 192, 0);

    @ConfigItem(
            name = "Glow speed (ms)",
            keyName = "glowSpeedMs",
            description = "How long between cycles of min and max brightness of the glow effect",
            position = 1)
    @Units(Units.MILLISECONDS)
    default int glowSpeedMs() {
        return DEFAULT_GLOW_BREATHE_PERIOD_MS;
    }

    @ConfigItem(
            name = "Max glow intensity",
            keyName = "maxBreatheIntensityPercent",
            description = "Max intensity of glow effect (100% is opaque)",
            position = 2)
    @Units(Units.PERCENT)
    @Range(min = 10, max = 100)
    default int maxBreatheIntensityPercent() {
        return DEFAULT_MAX_GLOW_BREATHE_INTENSITY;
    }

    @Alpha
    @ConfigItem(
            position = 3,
            keyName = "glowColor",
            name = "Glow color",
            description = "The color of the main glow effect"
    )
    default Color glowColor() {
        return DEFAULT_GLOW_COLOR;
    }

    @ConfigItem(
            keyName = "usePreEmptiveAlerts",
            name = "Whether to use pre-emptive alerts",
            description = "If enabled, will give a weaker alert right before a task is complete",
            position = 4
    )
    default boolean usePreEmptiveAlerts() {
        return false;
    }

    @ConfigItem(
            name = "Pre-emptive alert advance warning (ms)",
            keyName = "preEmptiveDelayMs",
            description = "How long before a task is done to start the pre-emptive alert (ms)",
            position = 5)
    @Units(Units.MILLISECONDS)
    default int preEmptiveDelayMs() {
        return DEFAULT_PRE_EMPTIVE_DELAY_MS;
    }

    @Alpha
    @ConfigItem(
            position = 6,
            keyName = "weakGlowColor",
            name = "Weak glow color",
            description = "The color of the glow effect used in pre-emptive alerts"
    )
    default Color weakGlowColor() {
        return DEFAULT_WEAK_GLOW_COLOR;
    }
}
