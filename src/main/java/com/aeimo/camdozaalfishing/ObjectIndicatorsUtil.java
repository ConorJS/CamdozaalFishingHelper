package com.aeimo.camdozaalfishing;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.awt.Color;
import java.util.*;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
public class ObjectIndicatorsUtil
{
    private static final String CONFIG_GROUP = "objectindicatorstest";
    private static final String MARK = "Mark object";
    private static final String UNMARK = "Unmark object";

    @Getter(AccessLevel.PACKAGE)
    private final List<ColorTileObject> objects = new ArrayList<>();
    private final Map<Integer, Set<ObjectPoint>> points = new HashMap<>();

    private Client client;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private Gson gson;

    protected ObjectIndicatorsUtil (Client client) {
        this.client = client;
    }

    protected void shutDown()
    {
        points.clear();
        objects.clear();
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event)
    {
        checkObjectPoints(event.getWallObject());
    }

    @Subscribe
    public void onWallObjectDespawned(WallObjectDespawned event)
    {
        objects.removeIf(o -> o.getTileObject() == event.getWallObject());
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        checkObjectPoints(event.getGameObject());
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
    {
        checkObjectPoints(event.getDecorativeObject());
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        objects.removeIf(o -> o.getTileObject() == event.getGameObject());
    }

    @Subscribe
    public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
    {
        objects.removeIf(o -> o.getTileObject() == event.getDecorativeObject());
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event)
    {
        checkObjectPoints(event.getGroundObject());
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned event)
    {
        objects.removeIf(o -> o.getTileObject() == event.getGroundObject());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        GameState gameState = gameStateChanged.getGameState();
        if (gameState == GameState.LOADING)
        {
            // Reload points with new map regions

            points.clear();
            for (int regionId : client.getMapRegions())
            {
                // load points for region
                final Set<ObjectPoint> regionPoints = createPoints(regionId);
                points.put(regionId, regionPoints);
            }
        }

        if (gameStateChanged.getGameState() != GameState.LOGGED_IN && gameStateChanged.getGameState() != GameState.CONNECTION_LOST)
        {
            objects.clear();
        }
    }

    private void checkObjectPoints(TileObject object)
    {
        if (object.getPlane() < 0)
        {
            // object is under a bridge, which can't be marked anyway
            return;
        }

        final WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, object.getLocalLocation(), object.getPlane());
        final Set<ObjectPoint> objectPoints = points.get(worldPoint.getRegionID());

        if (objectPoints == null)
        {
            return;
        }

        ObjectComposition objectComposition = client.getObjectDefinition(object.getId());
        if (objectComposition.getImpostorIds() == null)
        {
            // Multiloc names are instead checked in the overlay
            String name = objectComposition.getName();
            if (Strings.isNullOrEmpty(name) || name.equals("null"))
            {
                // was marked, but name has changed
                return;
            }
        }

        for (ObjectPoint objectPoint : objectPoints)
        {
            if (worldPoint.getRegionX() == objectPoint.getRegionX()
                    && worldPoint.getRegionY() == objectPoint.getRegionY()
                    && worldPoint.getPlane() == objectPoint.getZ()
                    && objectPoint.getId() == object.getId())
            {
                objects.add(new ColorTileObject(object,
                        objectComposition,
                        objectPoint.getName(),
                        objectPoint.getColor()));
                break;
            }
        }
    }

    private Set<ObjectPoint> createPoints(final int regionId) {
        Set<ObjectPoint> points = new HashSet<>();
        points.add(new ObjectPoint(41545, "Preparation Table", 11610, 56, 14, 0, Color.YELLOW));
        points.add(new ObjectPoint(41546, "Altar", 11610, 58, 12, 0, Color.YELLOW));
        return points;
    }
}
