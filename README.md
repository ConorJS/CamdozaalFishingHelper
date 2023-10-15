# Camdozaal Fishing Helper
Provides a simple overlay and useful alerts for Camdozaal fishing/prayer, to make this method a little friendlier.

**Attention**: This plugin should not be enabled outside Camdozaal, as it will constantly alert the player. In the future, this may be solved with region checking.

The plugin highlights the correct bench to use based on which fish you have and what state they are in (processed or otherwise), alerts the player by adding a visual glow effect to the window when a resource is **nearly** depleted (advance warning is given; the delay for this is configurable). When the player has finished offering fish, the nearby fishing spot is highlighted instead.

The game window will also glow a color (configurable) when an action is complete. You can also configure the speed of
the glow effect, and how long your character needs to stand still for the
alert to trigger. This is deliberately separate from RuneLite's built-in alerts, as RuneLite's more generic idle notifier occasionally gets stuck when the window is being frequently focused and unfocused, and this more targeted approach does not. Also, the breathing effect on the alert offers an (arguably) less harsh visual alert, while still be noticeable.