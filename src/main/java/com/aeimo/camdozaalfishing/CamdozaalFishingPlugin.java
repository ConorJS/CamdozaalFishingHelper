package com.aeimo.camdozaalfishing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.*;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
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

    private static final int EARLY_ALERT_THRESHOLD = 1;

    private static final int SHORELINE_X_COORD = 10_000;
    private static final int SHORELINE_Y_COORD_START = 10_000;
    private static final int SHORELINE_Y_COORD_END = 10_005;

    // [7104, 6976] (west), [7232, 6976] (east)
    private static final int NORTHERN_PREPARATION_TABLE_OBJECT_ID = 10_000;
    // [7360, 6848], (north-west) [7488, 6464] (south-east)
    private static final int OFFERING_TABLE_OBJECT_ID = 10_001;

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

    //== setup =======================================================================================================================

    @Provides
    CamdozaalFishingConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(CamdozaalFishingConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);

        updateCountsOfItems();
        updatePlayerLocation();
        updateEnvironment();
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
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

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        GameState gameState = gameStateChanged.getGameState();
        if (gameState == GameState.CONNECTION_LOST || gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING)
        {
            // TODO some logic to clear UI
        }
    }

    // TODO when config added
    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        updateCountsOfItems();
        updatePlayerLocation();
        updateEnvironment();

        establishState();
        establishAlerts();
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        final NPC npc = event.getNpc();

        if (FishingSpot.findSpot(npc.getId()) == null)
        {
            return;
        }

        LocalPoint loc = npc.getLocalLocation();
        if (loc.getX() == SHORELINE_X_COORD && (loc.getY() >= SHORELINE_Y_COORD_START && loc.getY() <= SHORELINE_Y_COORD_END)) {
            log.info("Fishing spot added: {} at {}", npc, npc.getLocalLocation());
            shorelineFishingSpots.add(npc);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned)
    {
        final NPC npc = npcDespawned.getNpc();

        log.info("Fishing spot removed: {} at {}", npc, npc.getLocalLocation());
        shorelineFishingSpots.remove(npc);
        if (southernMostFishingSpot == npc) {
            southernMostFishingSpot = null;
        }
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
        // TODO- tiles?
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();

        /*log.info("tile 1D length = {}", tiles.length);
        log.info("tile 2D[0] length = {}", tiles[0].length);
        log.info("tile 2D[1] length = {}", tiles[1].length);
        log.info("tile 3D[0][0] length = {}", tiles[0][0].length);
        log.info("tile 3D[0][1] length = {}", tiles[0][1].length);*/

        int gameObjectCount = 0;
        int wallObjectCount = 0;
        int groundObjectCount = 0;
        for (int i = 0; i < tiles.length; i++) {
            Tile[][] tilesX = tiles[i];
            for (int j = 0; j < tilesX.length; j++) {
                Tile[] tileY = tiles[i][j];
                for (int k = 0; k < tilesX.length; k++) {
                    Tile tileZ = tiles[i][j][k];

                    final Tile tile = tileZ;
                    if (tile != null) {
                        final GameObject[] tileGameObjects = tile.getGameObjects();
                        final DecorativeObject tileDecorativeObject = tile.getDecorativeObject();
                        final WallObject tileWallObject = tile.getWallObject();
                        final GroundObject groundObject = tile.getGroundObject();

                        //log.info("tile = {}", tile);
                        //log.info("tileGameObjects = {}", tileGameObjects);
                        //log.info("tileWallObject = {}", tileWallObject);
                        //log.info("groundObject = {}", groundObject);
                        if (tileGameObjects != null) {
                            gameObjectCount += tileGameObjects.length;
                        }
                        if (tileWallObject != null) {
                            gameObjectCount++;
                        }
                        if (groundObject != null) {
                            groundObjectCount++;
                        }
                    } else {
                        //log.info("Null tile: [{i}, {j}, {k}]");
                    }
                }
            }
        }

        log.info("game objects: {}, wall objects: {}, ground object: {}",
                gameObjectCount, wallObjectCount, groundObjectCount);

        if (true) {
            return;
        }

        final Tile tile = tiles[0][0][0];
        final GameObject[] tileGameObjects = tile.getGameObjects();
        final DecorativeObject tileDecorativeObject = tile.getDecorativeObject();
        final WallObject tileWallObject = tile.getWallObject();
        final GroundObject groundObject = tile.getGroundObject();

        log.info("tile = {}", tile);
        log.info("tileGameObjects = {}", tileGameObjects);
        log.info("tileWallObject = {}", tileWallObject);
        log.info("groundObject = {}", groundObject);

        TileObject tileObject = tileGameObjects.length == 0 ? null : tileGameObjects[0];
        northernPreparationTable = new ColorTileObject(tileObject,
                client.getObjectDefinition(NORTHERN_PREPARATION_TABLE_OBJECT_ID),
                "Northern preparation table",
                new Color(255, 255, 0));

        // TODO- same for offering table

        southernMostFishingSpot = shorelineFishingSpots.stream()
                .max(Comparator.comparing(a -> a.getLocalLocation().getY()))
                .orElse(null);
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
