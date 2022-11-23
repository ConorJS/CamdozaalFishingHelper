package com.aeimo.camdozaalfishing;

import java.awt.Color;
import net.runelite.client.config.*;

@ConfigGroup("camdozaalfishing")
public interface CamdozaalFishingConfig extends Config {
    int DEFAULT_AFK_DURATION_S = 5;
    int DEFAULT_GLOW_BREATHE_PERIOD_MS = 1_000;
    int DEFAULT_MAX_GLOW_BREATHE_INTENSITY = 100;
    Color DEFAULT_GLOW_COLOR = new Color(255, 0, 0);

    @ConfigItem(name = "AFK threshold (Bank)", keyName = "afkDurationThresholdBank", description = "How long is the player idle with the bank open before an AFK alert fires", position = 1)
    @Units(Units.SECONDS)
    default int afkDurationThresholdBank() {
        return DEFAULT_AFK_DURATION_S;
    }

    @ConfigItem(name = "Glow speed (ms)", keyName = "glowSpeedMs", description = "How long between cycles of min and max brightness of the glow effect", position = 2)
    @Units(Units.MILLISECONDS)
    default int glowSpeedMs() {
        return DEFAULT_GLOW_BREATHE_PERIOD_MS;
    }

    @ConfigItem(name = "Max glow intensity", keyName = "maxBreatheIntensityPercent", description = "Max intensity of glow effect (100% is opaque)", position = 3)
    @Units(Units.PERCENT)
    @Range(min = 10, max = 100)
    default int maxBreatheIntensityPercent() {
        return DEFAULT_MAX_GLOW_BREATHE_INTENSITY;
    }

    @Alpha
    @ConfigItem(
        position = 4,
        keyName = "glowColor",
        name = "Glow color",
        description = "The color of the glow effect"
    )
    default Color glowColor() {
        return DEFAULT_GLOW_COLOR;
    }
}
