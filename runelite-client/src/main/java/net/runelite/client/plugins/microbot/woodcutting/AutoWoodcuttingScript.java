package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.api.AnimationID;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingWalkBack;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AutoWoodcuttingScript extends Script {

    public static double version = 1.5;

    private WorldPoint returnPoint;

    public boolean run(AutoWoodcuttingConfig config) {
        if (config.hopWhenPlayerDetected()) {
            Microbot.showMessage("Make sure autologin plugin is enabled and randomWorld checkbox is checked!");
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (config.hopWhenPlayerDetected()) {
                    Rs2Player.logoutIfPlayerDetected(1, 10);
                    return;
                }


                if (Rs2Equipment.isWearing("Dragon axe"))
                    Rs2Combat.setSpecState(true, 1000);
                if (Microbot.isMoving() || Microbot.isAnimating() || Microbot.pauseAllScripts) return;
                if (Rs2Inventory.isFull()) {
                    switch(config.resetOptions()){
                        case DROP:
                            Rs2Inventory.dropAll(config.TREE().getLog());
                            break;
                        case BANK:
                            boolean reachedDestination = Rs2Bank.walkToBank();
                            if (!reachedDestination) {
                                sleepUntil(() -> Rs2Walker.isNear());
                            }

                            if (!Rs2Bank.isOpen()) {
                                Rs2Bank.openBank();
                                Rs2Bank.depositAll(config.TREE().getLog());
                            }

                            if (Rs2Bank.isOpen()) {
                                Rs2Bank.closeBank();
                                sleepUntil(() -> !Rs2Bank.isOpen());
                            }

                            walkBack(config);
                            break;
                        case FIREMAKE:
                            do {
                               sleepUntil(() -> burnLog(config));
                            }
                            while (Rs2Inventory.contains(config.TREE().getLog()));

                            walkBack(config);
                            break;
                    }

                }
                GameObject tree = Rs2GameObject.findObject(config.TREE().getName(), true, config.distanceToStray(), getInitialPlayerLocation());

                if (tree != null){
                    if(Rs2GameObject.interact(tree, config.TREE().getAction())){
                        if(config.walkBack().equals(WoodcuttingWalkBack.LAST_LOCATION)){
                            returnPoint = Microbot.getClient().getLocalPlayer().getWorldLocation();
                        }
                    }
                }else {
                    System.out.println("No trees in zone");
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    /*
        TODO: fix PoseAnimation Check to ensure next firemake starts when the first one finishes or spams fires
     */
    private boolean burnLog(AutoWoodcuttingConfig config) {
        if (Rs2Player.isStandingOnObject()){
            WorldPoint fireSpot = fireSpot(3);
            Rs2Walker.walkTo(fireSpot);
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(fireSpot));
        }
        Rs2Inventory.use("tinderbox");
        sleep(Random.random(300,600));
        Rs2Inventory.use(config.TREE().getLog());
        // Rs2Player.waitForAnimation(12000);
        sleepUntil(() -> Microbot.getClient().getLocalPlayer().getPoseAnimation() != AnimationID.FIREMAKING, 12000);
        return true;
    }

    private WorldPoint fireSpot(int distance) {
        List<WorldPoint> worldPoints = Rs2Tile.getWalkableTilesAroundPlayer(distance);

        for(WorldPoint walkablePoint : worldPoints) {
            if (doesFireExists(walkablePoint) || (Rs2GameObject.getGameObject(walkablePoint) != null)) { continue; }
            return walkablePoint;
        }

        fireSpot(distance+1);

        return null;
    }

    private boolean doesFireExists(WorldPoint worldPoint) {
        List<GameObject> gameObjectsWithinDistance = Rs2GameObject.getGameObjectsWithinDistance(1, worldPoint);
        if(gameObjectsWithinDistance.isEmpty()) { return false; }

        for(GameObject gameObject : gameObjectsWithinDistance) {
            if ((gameObject.getId() == ObjectID.FIRE_26185) && gameObject.getWorldLocation() == worldPoint){
                return true;
            }
        }
        return false;
    }

    private void walkBack(AutoWoodcuttingConfig config) {
        if(config.walkBack().equals(WoodcuttingWalkBack.INITIAL_LOCATION)){
            if(config.randomReturnTile()){
                Rs2Walker.walkTo(new WorldPoint(initialPlayerLocation.getX() - Random.random(-1, 1), initialPlayerLocation.getY() - Random.random(-1, 1), initialPlayerLocation.getPlane()));
            } else {
                Rs2Walker.walkTo(initialPlayerLocation);
            }
            sleepUntil(() -> Rs2Walker.isNear(initialPlayerLocation));
        }
        if(config.walkBack().equals(WoodcuttingWalkBack.LAST_LOCATION)){
            if(config.randomReturnTile()){
                Rs2Walker.walkTo(new WorldPoint(returnPoint.getX() - Random.random(-1, 1), returnPoint.getY() - Random.random(-1, 1), returnPoint.getPlane()));
            } else {
                Rs2Walker.walkTo(returnPoint);
            }
            sleepUntil(() -> Rs2Walker.isNear(returnPoint));
        }
    }
}