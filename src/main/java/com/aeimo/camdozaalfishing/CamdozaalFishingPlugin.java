package com.aeimo.camdozaalfishing;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.*;
import java.util.function.Function;
import javax.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(name = "Camdozaal Fishing Helper", description = "Visual indicators and alerts to simplify Camdozaal fishing", tags = {"afk", "camdozaal", "f2p", "fishing", "prayer"}, enabledByDefault = false)
public class CamdozaalFishingPlugin extends Plugin {
    //== constants ====================================================================================================================

    private static final Set<Integer> CAMDOZAAL_REGIONS = ImmutableSet.of();

    private static final int EARLY_ALERT_THRESHOLD = 1;

    private static final int SHORELINE_X_COORD = 10_000;

    private static final int SHORELINE_Y_COORD_START = 10_000;

    private static final int SHORELINE_Y_COORD_END = 10_005;

    // [7104, 6976] (west), [7232, 6976] (east)
    private static final int PREPARATION_TABLE_OBJECT_ID = 41_545;

    private static final LocalPoint NORTH_PREPARATION_TABLE_LOCATION = new LocalPoint(6144, 6976); // south at y=6336

    // [7360, 6848], (north-west) [7488, 6464] (south-east)
    private static final int OFFERING_TABLE_OBJECT_ID = 41_546;

    private static final LocalPoint OFFERING_TABLE_LOCATION = new LocalPoint(6400, 6656);

    private static final int RAW_GUPPY = 25652;

    private static final int GUPPY = 25654;

    private static final int RAW_CAVEFISH = 25658;

    private static final int CAVEFISH = 25660;

    private static final int RAW_TETRA = 25664;

    private static final int TETRA = 25666;

    private static final int[] RAW_FISH = new int[]{RAW_GUPPY, RAW_CAVEFISH, RAW_TETRA};

    private static final int[] PREPARED_FISH = new int[]{GUPPY, CAVEFISH, TETRA};

    //== attributes ===================================================================================================================

    @Inject
    private CamdozaalFishingConfig config;

    @Inject
    private CamdozaalFishingOverlay overlay;

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    /*@Getter
    private final List<DriftNet> NETS = ImmutableList.of(
            new DriftNet(NullObjectID.NULL_31433, Varbits.NORTH_NET_STATUS, Varbits.NORTH_NET_CATCH_COUNT, ImmutableSet.of(
                    new WorldPoint(3746, 10297, 1),
                    new WorldPoint(3747, 10297, 1),
                    new WorldPoint(3748, 10297, 1),
                    new WorldPoint(3749, 10297, 1)
            )),
            new DriftNet(NullObjectID.NULL_31434, Varbits.SOUTH_NET_STATUS, Varbits.SOUTH_NET_CATCH_COUNT, ImmutableSet.of(
                    new WorldPoint(3742, 10288, 1),
                    new WorldPoint(3742, 10289, 1),
                    new WorldPoint(3742, 10290, 1),
                    new WorldPoint(3742, 10291, 1),
                    new WorldPoint(3742, 10292, 1)
            )));*/

    // General state
    private CamdozaalFishingState goalPlayerState;

    private boolean doAlertWeak;

    private boolean doAlert;

    private boolean inCamdozaal;

    // Inventory info
    private Map<Integer, PreviousAndCurrentInt> itemCountMemory = new HashMap<>();

    // World info
    private final List<NPC> shorelineFishingSpots = new ArrayList<>();

    @Getter
    private NPC southernMostFishingSpot = null;

    @Getter
    private ColorTileObject northernPreparationTable = null;

    @Getter
    private ColorTileObject offeringTable = null;

    @Getter
    private GameObject offeringTableTest = null;

    private List<GameObject> offeringTables = new ArrayList<>();

    @Getter
    private GameObject northernPreparationTableTest = null;

    private List<GameObject> preparationTables = new ArrayList<>();

    @Getter
    private List<HighlightGameObject> testHighlightObjects = new ArrayList();

    private ObjectIndicatorsUtil objectIndicatorsUtil;

    @AllArgsConstructor
    @Getter
    class HighlightGameObject {
        private final GameObject gameObject;

        private final Color color;
    }

    //== setup =======================================================================================================================

    @Provides
    CamdozaalFishingConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(CamdozaalFishingConfig.class);
    }

    @Override
    protected void startUp() {
        objectIndicatorsUtil = new ObjectIndicatorsUtil(client);
        objectIndicatorsUtil.startUp();

        overlayManager.add(overlay);

        updateCountsOfItems();
        updatePlayerLocation();
        //updateEnvironment();
        updateInCamdozaal();
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
    }

    public List<ColorTileObject> getObjects() {
        return objectIndicatorsUtil.getObjects();
    }

    //== methods =====================================================================================================================

    public int getGlowBreathePeriod() {
        return config.glowSpeedMs();
    }

    public int getMaxBreatheIntensityPercent() {
        return config.maxBreatheIntensityPercent();
    }

    public Color getGlowColor() {
        return config.glowColor();
    }

    private PreviousAndCurrent<LocalPoint> playerLocationMemory;

    //== subscriptions ===============================================================================================================

    /*@Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        GameState gameState = gameStateChanged.getGameState();
        if (gameState == GameState.CONNECTION_LOST || gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING) {
            // TODO some logic to clear UI
        }
    }*/

    // TODO when config added
    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        updateCountsOfItems();
        updatePlayerLocation();
        updateInCamdozaal();

        establishState();
        establishAlerts();
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        final NPC npc = event.getNpc();

        if (FishingSpot.findSpot(npc.getId()) == null) {
            return;
        }

        shorelineFishingSpots.add(npc);
        southernMostFishingSpot = findClosestGameObject(shorelineFishingSpots, NPC::getLocalLocation);

        System.out.println("New fishing spot: " + npc.getLocalLocation());
        System.out.println("Closest fishing spot: " + southernMostFishingSpot.getLocalLocation());

        /*if (loc.getX() == SHORELINE_X_COORD && (loc.getY() >= SHORELINE_Y_COORD_START && loc.getY() <= SHORELINE_Y_COORD_END)) {
            log.info("Fishing spot added: {} at {}", npc, npc.getLocalLocation());
            shorelineFishingSpots.add(npc);
        }*/
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        final NPC npc = npcDespawned.getNpc();

        //log.info("Fishing spot removed: {} at {}", npc, npc.getLocalLocation());
        shorelineFishingSpots.remove(npc);
        if (southernMostFishingSpot == npc) {
            southernMostFishingSpot = null;
        }

        System.out.println("Removed fishing spot: " + npc.getLocalLocation());
        if (southernMostFishingSpot != null) {
            System.out.println("Closest fishing spot: " + southernMostFishingSpot.getLocalLocation());
        } else {
            System.out.println("Closest fishing spot: NULL");
        }
    }

    /*@Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        if (!inCamdozaal) {
            return;
        }
        addGameObject(event.getGameObject());
    }*/

    /*@Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        if (!inCamdozaal) {
            return;
        }

        removeGameObject(event.getGameObject());
    }*/

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

        // Verify that all regions exist in MOTHERLODE_MAP_REGIONS
        for (int region : currentMapRegions) {
            if (!CAMDOZAAL_REGIONS.contains(region)) {
                return false;
            }
        }

        return true;
    }

    //== subscriptions (object indicator integration) ===============================================================================

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event)
    {
        objectIndicatorsUtil.onWallObjectSpawned(event);
    }

    @Subscribe
    public void onWallObjectDespawned(WallObjectDespawned event)
    {
        objectIndicatorsUtil.onWallObjectDespawned(event);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        objectIndicatorsUtil.onGameObjectSpawned(event);
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
    {
        objectIndicatorsUtil.onDecorativeObjectSpawned(event);
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        objectIndicatorsUtil.onGameObjectDespawned(event);
    }

    @Subscribe
    public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
    {
        objectIndicatorsUtil.onDecorativeObjectDespawned(event);
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event)
    {
        objectIndicatorsUtil.onGroundObjectSpawned(event);
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned event)
    {
        objectIndicatorsUtil.onGroundObjectDespawned(event);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        objectIndicatorsUtil.onGameStateChanged(gameStateChanged);
    }

    //== core ========================================================================================================================

    private void updateCountsOfItems() {
        updateCountOfItem(RAW_GUPPY);
        updateCountOfItem(RAW_CAVEFISH);
        updateCountOfItem(RAW_TETRA);
        updateCountOfItem(GUPPY);
        updateCountOfItem(CAVEFISH);
        updateCountOfItem(TETRA);
    }

    private void updatePlayerLocation() {
        LocalPoint playerLocation = client.getLocalDestinationLocation();
        if (playerLocationMemory == null) {
            playerLocationMemory = new PreviousAndCurrent<>(playerLocation);
        } else {
            playerLocationMemory.newData(playerLocation);
        }
    }

    private void updateEnvironment() {
        southernMostFishingSpot = shorelineFishingSpots.stream()
                .max(Comparator.comparing(a -> a.getLocalLocation().getY()))
                .orElse(null);
    }

    private void updateInCamdozaal() {
        inCamdozaal = checkInCamdozaal();
    }

    private void establishState() {
        // If multiple items changed in a tick, don't attempt to cater to this (only lag should be able to cause this).
        if (itemCountMemory.values().stream().filter(PreviousAndCurrent::changed).count() > 1) {
            return;
        }

        // When the inventory fills up with raw fish (we can assume while fishing), switch to fish preparation.
        if (emptyInventorySlots() == 0 && anyItemsIncreased(RAW_FISH)) {
            goalPlayerState = CamdozaalFishingState.PREPARE;
        }

        if (getItemsCount(RAW_FISH) == 0) {
            if (getItemsCount(PREPARED_FISH) > 0) {
                // If we have at least one prepared fish, and no raw fish, we can assume the player should be making offerings (they may already be).
                goalPlayerState = CamdozaalFishingState.OFFER;
            } else {
                // If the player has no fish of any type, they should be fishing.
                goalPlayerState = CamdozaalFishingState.FISH;
            }
        }
    }

    // TODO
    private void establishAlerts() {
        // A strong alert should be issued whenever the player is:
        // 1) Standing still
        // 2) Not fishing, preparing or offering
        // 2.1) For preparing or offering, this is covered by just checking if items have changed in the past 4 ticks
        // 2.2) For fishing, this can be known by checking if the inventory is full, or... TODO check RL fishing plugin

        if (getItemsCount(RAW_FISH) == EARLY_ALERT_THRESHOLD && anyItemsDecreased(RAW_FISH)) {
            doAlertWeak = true;
        }
    }

    //== helpers =====================================================================================================================

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
        return Arrays.stream(itemIds)
                .mapToObj(itemCountMemory::get)
                .mapToInt(PreviousAndCurrentInt::current)
                .sum();
    }

    private int getItemCount(int itemId) {
        return itemCountMemory.get(itemId).current;
    }

    private static int secondsToTicksRoundNearest(int ticks) {
        return (int) Math.round(ticks / 0.6);
    }

    private void addGameObject(GameObject gameObject) {
        if (PREPARATION_TABLE_OBJECT_ID == gameObject.getId()) {
            preparationTables.add(gameObject);
            northernPreparationTableTest = findClosestGameObject(preparationTables, GameObject::getLocalLocation);
            System.out.printf("Prep table: ID=%s Loc=%s%n", gameObject.getId(), gameObject.getLocalLocation());
        }

        if (OFFERING_TABLE_OBJECT_ID == gameObject.getId()) {
            offeringTables.add(gameObject);
            offeringTableTest = findClosestGameObject(offeringTables, GameObject::getLocalLocation);
            System.out.printf("Offer table: ID=%s Loc=%s%n", gameObject.getId(), gameObject.getLocalLocation());
        }
    }

    private void removeGameObject(GameObject gameObject) {
        if (PREPARATION_TABLE_OBJECT_ID == gameObject.getId()) {
            preparationTables.remove(gameObject);
            northernPreparationTableTest = findClosestGameObject(preparationTables, GameObject::getLocalLocation);
            System.out.printf("Prep table removed: ID=%s Loc=%s%n", gameObject.getId(), gameObject.getLocalLocation());
        }

        if (OFFERING_TABLE_OBJECT_ID == gameObject.getId()) {
            offeringTables.remove(gameObject);
            offeringTableTest = findClosestGameObject(offeringTables, GameObject::getLocalLocation);
            System.out.printf("Offer table removed: ID=%s Loc=%s%n", gameObject.getId(), gameObject.getLocalLocation());
        }
    }

    private <T> T findClosestGameObject(List<T> gameObjects, Function<T, LocalPoint> locationHandler) {
        LocalPoint playerLoc = client.getLocalPlayer().getLocalLocation();
        return gameObjects.stream()
                .min(Comparator.comparingInt(o -> locationHandler.apply(o).distanceTo(playerLoc)))
                .orElse(null);
    }

    //== types =======================================================================================================================

    enum CamdozaalFishingState {
        FISH, PREPARE, OFFER
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
            return current > previous;
        }

        boolean decreased() {
            return previous > current;
        }
    }
}
