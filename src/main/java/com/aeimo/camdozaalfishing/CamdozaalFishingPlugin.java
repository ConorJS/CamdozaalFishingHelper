package com.aeimo.camdozaalfishing;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

// TODO(conor) - Notify when need to run Barronite handles (+config for handle count)
// TODO(conor) - When fish spots die, alert is slow
// TODO(conor) - Needs magic numbers moved to constants
@Slf4j
@PluginDescriptor(name = "Camdozaal Fishing Helper", description = "Visual indicators and alerts to simplify Camdozaal fishing", tags = {"afk", "camdozaal", "f2p", "fishing", "prayer"}, enabledByDefault = false)
public class CamdozaalFishingPlugin extends Plugin {
    //<editor-fold desc=constants>
    // @formatter:off
    //== constants ====================================================================================================================

    private static final int PREPARATION_TABLE_OBJECT_ID = 41_545;
    private static final int OFFERING_TABLE_OBJECT_ID = 41_546;
    static final ObjectPoint PREPARATION_TABLE = new ObjectPoint(PREPARATION_TABLE_OBJECT_ID, "Preparation Table", 11610, 56, 14, 0, Color.YELLOW);
    static final ObjectPoint ALTAR = new ObjectPoint(OFFERING_TABLE_OBJECT_ID, "Altar", 11610, 58, 12, 0, Color.YELLOW);

    // TODO(conor) - Implement, use in isCamdozaal
    private static final Set<Integer> CAMDOZAAL_REGIONS = ImmutableSet.of();

    private static final int TICKS_PER_FISH_ATTEMPT = 6;
    private static final int TICKS_PER_PREPARE = 4;
    private static final int TICKS_PER_OFFER = 3;

    private static final int RAW_GUPPY = 25_652;
    private static final int GUPPY = 25_654;
    private static final int RAW_CAVEFISH = 25_658;
    private static final int CAVEFISH = 25_660;
    private static final int RAW_TETRA = 25_664;
    private static final int TETRA = 25_666;
    private static final Integer[] RAW_FISH = new Integer[]{RAW_GUPPY, RAW_CAVEFISH, RAW_TETRA};
    private static final Integer[] PREPARED_FISH = new Integer[]{GUPPY, CAVEFISH, TETRA};
    //</editor-fold>

    //<editor-fold desc=attributes>
    //== attributes ===================================================================================================================

    @Inject
    private CamdozaalFishingConfig config;
    @Inject
    private CamdozaalFishingOverlay overlay;
    @Inject
    private Client client;
    @Inject
    private OverlayManager overlayManager;

    @Provides
    CamdozaalFishingConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(CamdozaalFishingConfig.class);
    }

    private ObjectIndicatorsUtil objectIndicatorsUtil;

    // General state
    private CamdozaalFishingState currentPlayerState;
    private CamdozaalFishingState goalPlayerState;

    private boolean inCamdozaal;

    private PreviousAndCurrent<LocalPoint> playerLocationMemory;

    // Inventory info
    private final Map<Integer, PreviousAndCurrentInt> itemCountMemory = new HashMap<>();
    private Integer lastItemIncrease;
    private Integer lastItemDecrease;
    private int ticksSinceLastItemChange = 0;

    // World info
    @Getter
    private final List<NPC> fishingSpots = new ArrayList<>();
    @Getter
    private NPC southernMostFishingSpot;

    //</editor-fold>
    // @formatter:on

    //<editor-fold desc=subscriptions>
    //== subscriptions ===============================================================================================================

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        updateCountsOfItems();
        updatePlayerLocation();
        updateInCamdozaal();

        currentPlayerState = establishCurrentState();
        goalPlayerState = establishGoalState();

        pruneDeadFishingSpots();
        recalculateClosestFishingSpot();
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        final NPC npc = event.getNpc();
        if (npc.getName() != null && !npc.getName().contains("Fishing spot")) {
            return;
        }

        fishingSpots.add(npc);
        recalculateClosestFishingSpot();
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        final NPC npc = npcDespawned.getNpc();
        if (npc.getName() != null && !npc.getName().contains("Fishing spot")) {
            return;
        }

        fishingSpots.remove(npc);
        recalculateClosestFishingSpot();
    }
    //</editor-fold>

    //<editor-fold desc=object indicator bridging>
    //== object indicator bridging ==================================================================================================================

    public ColorTileObject getPreparationTable() {
        return objectIndicatorsUtil.getObjects().stream()
                .filter(o -> o.getName().equals(PREPARATION_TABLE.getName()))
                .findFirst()
                .orElse(null);
    }

    public ColorTileObject getAltar() {
        return objectIndicatorsUtil.getObjects().stream()
                .filter(o -> o.getName().equals(ALTAR.getName()))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event) {
        objectIndicatorsUtil.onWallObjectSpawned(event);
    }

    @Subscribe
    public void onWallObjectDespawned(WallObjectDespawned event) {
        objectIndicatorsUtil.onWallObjectDespawned(event);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        objectIndicatorsUtil.onGameObjectSpawned(event);
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event) {
        objectIndicatorsUtil.onDecorativeObjectSpawned(event);
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        objectIndicatorsUtil.onGameObjectDespawned(event);
    }

    @Subscribe
    public void onDecorativeObjectDespawned(DecorativeObjectDespawned event) {
        objectIndicatorsUtil.onDecorativeObjectDespawned(event);
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event) {
        objectIndicatorsUtil.onGroundObjectSpawned(event);
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned event) {
        objectIndicatorsUtil.onGroundObjectDespawned(event);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        objectIndicatorsUtil.onGameStateChanged(gameStateChanged);
    }
    //</editor-fold>

    //<editor-fold desc=core>
    //== core ========================================================================================================================

    @Override
    protected void startUp() {
        objectIndicatorsUtil = new ObjectIndicatorsUtil(client);

        overlayManager.add(overlay);

        updatePlayerLocation();
        updateInCamdozaal();
    }

    protected boolean isDoAlertWeak() {
        if (!config.usePreEmptiveAlerts()) {
            return false;
        }

        if (userInteractingWithClient()) {
            return false;
        }

        int thresholdTicks = secondsToTicksRoundNearest(config.preEmptiveDelayMs() / 1000f);

        if (Arrays.asList(RAW_FISH).contains(lastItemIncrease)
                && goalPlayerState == CamdozaalFishingState.FISH
                && meetsThresholdWithRemainderDelayOrExceeds(emptyInventorySlots(), thresholdTicks, TICKS_PER_FISH_ATTEMPT)) {
            return true;
        }

        if (Arrays.asList(RAW_FISH).contains(lastItemDecrease)
                && (goalPlayerState == CamdozaalFishingState.PREPARE || goalPlayerState == CamdozaalFishingState.OFFER_PREEMPT)
                && meetsThresholdWithRemainderDelayOrExceeds(getItemCount(lastItemDecrease), thresholdTicks, TICKS_PER_PREPARE)) {
            return true;
        }

        return (Arrays.asList(PREPARED_FISH).contains(lastItemDecrease)
                && (goalPlayerState == CamdozaalFishingState.OFFER || goalPlayerState == CamdozaalFishingState.FISH_PREEMPT)
                && meetsThresholdWithRemainderDelayOrExceeds(getItemCount(lastItemDecrease), thresholdTicks, TICKS_PER_OFFER));
    }

    private boolean meetsThresholdWithRemainderDelayOrExceeds(int subject, int thresholdTicks, int actionTicks) {
        // Unsuccessful actions pad ticksSinceLastItemChange, get around this by figuring out when the last action (successful
        // or otherwise) must have occurred.
        int ticksSinceLastAction = ticksSinceLastItemChange % actionTicks;
        int estimatedTicksLeft = ((actionTicks * subject) - ticksSinceLastAction);
        return thresholdTicks >= estimatedTicksLeft;
    }

    protected boolean isDoAlertFull() {
        if (userInteractingWithClient()) {
            return false;
        }

        if (currentPlayerState != goalPlayerState && currentPlayerState != CamdozaalFishingState.MOVING) {
            return true;
        }

        return currentPlayerState == CamdozaalFishingState.INACTIVE;
    }

    protected boolean isHighlightPreparationTable() {
        return goalPlayerState == CamdozaalFishingState.PREPARE;
    }

    protected boolean isHighlightAltar() {
        return goalPlayerState == CamdozaalFishingState.OFFER || goalPlayerState == CamdozaalFishingState.OFFER_PREEMPT;
    }

    protected boolean isHighlightFishingSpot() {
        return goalPlayerState == CamdozaalFishingState.FISH || goalPlayerState == CamdozaalFishingState.FISH_PREEMPT;
    }

    private void updateCountsOfItems() {
        updateCountOfItem(RAW_GUPPY);
        updateCountOfItem(RAW_CAVEFISH);
        updateCountOfItem(RAW_TETRA);
        updateCountOfItem(GUPPY);
        updateCountOfItem(CAVEFISH);
        updateCountOfItem(TETRA);

        Integer maybeItemIncreased = determineLastItemChange(this::anyItemsIncreased);
        Integer maybeItemDecreased = determineLastItemChange(this::anyItemsDecreased);
        if (maybeItemIncreased == null && maybeItemDecreased == null) {
            ticksSinceLastItemChange++;
        } else {
            ticksSinceLastItemChange = 0;
        }
        lastItemIncrease = orDefault(maybeItemIncreased, lastItemIncrease);
        lastItemDecrease = orDefault(maybeItemDecreased, lastItemDecrease);
    }

    private <T> T orDefault(T maybe, T defaultValue) {
        return maybe == null ? defaultValue : maybe;
    }

    private Integer determineLastItemChange(IntPredicate handler) {
        List<Integer> decreasedItems = Stream.concat(Arrays.stream(RAW_FISH), Arrays.stream(PREPARED_FISH))
                .filter(handler::test)
                .collect(Collectors.toList());
        if (!decreasedItems.isEmpty()) {
            if (decreasedItems.size() > 1) {
                log.error("Multiple tracked items changed in the same way: {}", decreasedItems);
            } else {
                return decreasedItems.get(0);
            }
        }
        return null;
    }

    private void updatePlayerLocation() {
        LocalPoint playerLocation = client.getLocalDestinationLocation();
        if (playerLocationMemory == null) {
            playerLocationMemory = new PreviousAndCurrent<>(playerLocation);
        } else {
            playerLocationMemory.newData(playerLocation);
        }
    }

    private boolean checkInCamdozaal() {
        // TODO(conor) - CAMDOZAAL_REGIONS isn't implemented
        if (true) {
            return true;
        }

        GameState gameState = client.getGameState();
        if (gameState != GameState.LOGGED_IN
                && gameState != GameState.LOADING) {
            return false;
        }

        int[] currentMapRegions = client.getMapRegions();

        // Verify that all regions exist in CAMDOZAAL_REGIONS
        for (int region : currentMapRegions) {
            if (!CAMDOZAAL_REGIONS.contains(region)) {
                return false;
            }
        }

        return true;
    }

    private void updateInCamdozaal() {
        inCamdozaal = checkInCamdozaal();
    }

    private CamdozaalFishingState establishCurrentState() {
        Player player = client.getLocalPlayer();
        int animationId = player.getAnimation();
        LocalPoint playerLocation = player.getLocalLocation();
        if (animationId == 896 && playerLocation.distanceTo(getPreparationTable().getTileObject().getLocalLocation()) <= 143) {
            return CamdozaalFishingState.PREPARE;
        }
        if (animationId == 3_705 && playerLocation.distanceTo(getPreparationTable().getTileObject().getLocalLocation()) <= 271) {
            return CamdozaalFishingState.OFFER;
        }
        if (animationId == 621 && playerLocation.distanceTo(getSouthernMostFishingSpot().getLocalLocation()) <= 128) {
            return CamdozaalFishingState.FISH;
        }
        // 808 pose animation seems to occur when the player isn't walking or running.
        // Initiating walking seems to change this to 819 for the first tick, then 821 for all subsequent.
        // Initiating running seems to change this to 820 for the first tick, then 824 for all subsequent.
        // Pose animation probably changes to values other than these 5, but just not normally outside of Camdozaal (unless items trigger them).
        if (playerLocationMemory.changed() || animationId != -1 || player.getPoseAnimation() != 808) {
            return CamdozaalFishingState.MOVING;
        }

        return CamdozaalFishingState.INACTIVE;
    }

    private CamdozaalFishingState establishGoalState() {
        // If more than 2 items changed in a tick, don't attempt to cater to this (only lag should be able to cause this).
        // 2 items changing = a raw item being converted to prepared.
        if (itemCountMemory.values().stream().filter(PreviousAndCurrent::changed).count() > 2) {
            return CamdozaalFishingState.UNKNOWN;
        }

        if (getItemsCount(primIntArray(RAW_FISH)) == 0) {
            if (getItemsCount(primIntArray(PREPARED_FISH)) > 0) {
                // If we have at least one prepared fish, and no raw fish, we can assume the player should be making offerings (they may already be).
                if (isDoAlertWeak() && onlyOneItemTypeRemaining(primIntArray(PREPARED_FISH)) && getItemsCount(lastItemDecrease) > 0) {
                    // Right at the end of offering, if we're doing pre-emptive alerting, also pre-emptively guide the player to fish.
                    return CamdozaalFishingState.FISH_PREEMPT;
                }
                return CamdozaalFishingState.OFFER;
            } else {
                // If the player has no fish of any type, they should be fishing.
                return CamdozaalFishingState.FISH;
            }
        } else {
            // RHS=Edge case: If the player has empty inventory slots, raw fish and prepared fish, then they've not filled up their inventory
            // before starting preparation; it's easiest to just prepare any raw fish and finish the whole inventory - so focus preparation.
            if (emptyInventorySlots() == 0 || getItemsCount(primIntArray(PREPARED_FISH)) > 0) {
                if (isDoAlertWeak() && onlyOneItemTypeRemaining(primIntArray(RAW_FISH)) && getItemsCount(lastItemDecrease) > 0) {
                    // Right at the end of preparing, if we're doing pre-emptive alerting, also pre-emptively guide the player to fish.
                    return CamdozaalFishingState.OFFER_PREEMPT;
                }
                return CamdozaalFishingState.PREPARE;
            } else {
                // Don't try and pre-emptively highlight the preparation table at the end of fishing (fishing can fail).
                return CamdozaalFishingState.FISH;
            }
        }
    }

    private boolean onlyOneItemTypeRemaining(int... itemIds) {
        return Arrays.stream(itemIds)
                .filter(i -> getItemsCount(i) > 0)
                .boxed()
                .count() <= 1;
    }

    private int[] primIntArray(Integer[] objectArray) {
        return Arrays.stream(objectArray).mapToInt(obj -> obj).toArray();
    }
    //</editor-fold>

    //<editor-fold desc=helpers (alerts)>
    //== helpers (alerts) ===========================================================================================================================

    private boolean userInteractingWithClient() {
        // `client.getKeyboardIdleTicks() < 10` used to be included here
        return client.getGameState() != GameState.LOGGED_IN
                || client.getLocalPlayer() == null
                // If user has clicked in the last second then they're not idle so don't send idle notification
                || System.currentTimeMillis() - client.getMouseLastPressedMillis() < 1000
                || client.getKeyboardIdleTicks() < 10;
    }

    private int getItemCount(int itemId) {
        return itemCountMemory.get(itemId).current;
    }

    private static int secondsToTicksRoundNearest(float seconds) {
        return (int) Math.round(seconds / 0.6);
    }

    public int getGlowBreathePeriod() {
        return config.glowSpeedMs();
    }

    public int getMaxBreatheIntensityPercent() {
        return config.maxBreatheIntensityPercent();
    }

    public Color getGlowColor() {
        return config.glowColor();
    }

    public Color getWeakGlowColor() {
        return config.weakGlowColor();
    }
    //</editor-fold>

    //<editor-fold desc=helpers (item management)>
    //== helpers (item management) ==================================================================================================================

    private int countOfItem(int itemId) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return 0;
        }

        return (int) Arrays.stream(inventory.getItems())
                .filter(Objects::nonNull)
                // Empty inventory slot
                .filter(i -> i.getId() == itemId)
                .count();
    }

    private int emptyInventorySlots() {
        return countOfItem(-1);
    }

    private void updateCountOfItem(int itemId) {
        int count = countOfItem(itemId);
        if (itemCountMemory.containsKey(itemId)) {
            itemCountMemory.get(itemId).newData(count);
        } else {
            itemCountMemory.put(itemId, new PreviousAndCurrentInt(count));
        }
    }

    private boolean anyItemsIncreased(int... itemIds) {
        return Arrays.stream(itemIds)
                .mapToObj(itemCountMemory::get)
                .anyMatch(PreviousAndCurrentInt::increased);
    }

    private boolean anyItemsDecreased(int... itemIds) {
        return Arrays.stream(itemIds)
                .mapToObj(itemCountMemory::get)
                .anyMatch(PreviousAndCurrentInt::decreased);
    }

    private int getItemsCount(int... itemIds) {
        if (itemIds == null) {
            return 0;
        }
        return Arrays.stream(itemIds)
                .mapToObj(itemCountMemory::get)
                .mapToInt(PreviousAndCurrentInt::current)
                .sum();
    }
    //</editor-fold>

    //<editor-fold desc=helpers (game objects)>
    //== helpers (game objects) =====================================================================================================================

    private void recalculateClosestFishingSpot() {
        southernMostFishingSpot = findClosestGameObject(fishingSpots, NPC::getLocalLocation);
    }

    private void pruneDeadFishingSpots() {
        fishingSpots.stream()
                .filter(spot -> {
                    // At the end of a fishing spot's lifespan, is enters a state (TODO what?)...
                    boolean oddState = false;
                    if (spot.isDead()) {
                        oddState = true;
                        System.out.println("Dead fishing spot");
                    }
                    if (!spot.getComposition().isClickable()) {
                        oddState = true;
                        System.out.println("Not clickable spot");
                    }
                    return oddState;
                })
                .forEach(fishingSpots::remove);
    }

    private <T> T findClosestGameObject(List<T> gameObjects, Function<T, LocalPoint> locationHandler) {
        LocalPoint playerLoc = client.getLocalPlayer().getLocalLocation();
        return gameObjects.stream()
                .min(Comparator.comparingInt(o -> locationHandler.apply(o).distanceTo(playerLoc)))
                .orElse(null);
    }
    //</editor-fold>

    //<editor-fold desc=types>
    //== types ======================================================================================================================================

    enum CamdozaalFishingState {
        FISH, FISH_PREEMPT, PREPARE, OFFER, OFFER_PREEMPT, MOVING, INACTIVE, UNKNOWN
    }

    static class PreviousAndCurrent<T> {
        T previous;

        T current;

        PreviousAndCurrent(T initialValue) {
            current = initialValue;
        }

        T current() {
            return current;
        }

        void newData(T data) {
            previous = current;
            current = data;
        }

        boolean changed() {
            return !Objects.equals(previous, current);
        }
    }

    static class PreviousAndCurrentInt extends PreviousAndCurrent<Integer> {
        PreviousAndCurrentInt(Integer initialValue) {
            super(initialValue);
        }

        boolean increased() {
            return current != null && previous != null && current > previous;
        }

        boolean decreased() {
            return current != null && previous != null && previous > current;
        }
    }
    //</editor-fold>
}
