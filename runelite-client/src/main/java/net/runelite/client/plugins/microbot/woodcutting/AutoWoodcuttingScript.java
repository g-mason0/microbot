package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.api.GameObject;
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
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingWalkBack;

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
                            if (config.hasAxeInventory()) {
                                Rs2Inventory.dropAll(x -> x.slot > 0);
                            } else {
                                Rs2Inventory.dropAll();
                            }
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
                            if (config.hasAxeInventory()) {

                            } else {

                            }
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
        }, 0, 500, TimeUnit.MILLISECONDS);
        return true;
    }

    private void findOpenTile() {

    }

    private void burnLog() {

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