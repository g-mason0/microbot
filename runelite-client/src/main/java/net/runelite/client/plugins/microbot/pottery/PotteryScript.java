package net.runelite.client.plugins.microbot.pottery;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.pottery.enums.HumidifyItems;
import net.runelite.client.plugins.microbot.pottery.enums.PotteryItems;
import net.runelite.client.plugins.microbot.pottery.enums.State;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

public class PotteryScript extends Script {
    public static double version = 1.0;

    boolean init = true;
    public State state;
    public boolean isCraftingSoftClay = false;

    public boolean run(PotteryConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (init) {
                    getPotteryState(config);
                    Rs2Camera.setAngle(0);
                }

                if (!config.location().hasRequirements()) {
                    Microbot.showMessage("You do not meet the requirements for this location");
                    shutdown();
                    return;
                }

                switch (state) {
                    case HUMIDIFY:
                        boolean hasClayInInventory = Rs2Inventory.hasItemAmount("clay", 14, false, true);
                        boolean hasHumidifyItemInInventory = Rs2Inventory.hasItemAmount(config.humidifyItem().getFilledItemName(), 14);

                        if (hasClayInInventory && hasHumidifyItemInInventory) {
                            Rs2Inventory.combine("clay", config.humidifyItem().getFilledItemName());
                            sleep(Random.random(600, 800));
                            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                            sleepUntil(() -> !Rs2Inventory.contains("clay") && !Rs2Player.isAnimating(), 16000);
                            return;
                        }

                        state = State.BANK;
                        break;
                    case SPINNING:
                        if (!config.potteryItem().hasRequirements()) {
                            Microbot.showMessage("You do not meet the requirements for this item");
                        }

                        int softClayCount = Rs2Inventory.count("soft clay");
                        boolean hasSoftClay = Rs2Inventory.contains("soft clay");

                        Rs2Walker.walkTo(config.location().getWheelWorldPoint(), 4);
                        if (!isNearPotteryWheel(config, 4)) {
                            return;
                        }

                        Rs2Inventory.useItemOnObject(ItemID.SOFT_CLAY, config.location().getWheelObjectID());
                        Rs2Widget.sleepUntilHasWidget("how many do you wish to make?");

                        Rs2Widget.clickWidget(config.potteryItem().getUnfiredWheelWidgetID());

                        sleepUntil(() -> !hasSoftClay && !Rs2Player.isInteracting(), (2500 * softClayCount));
                        state = State.COOKING;
                        break;
                    case COOKING:
                        if (!config.potteryItem().hasRequirements()) {
                            Microbot.showMessage("You do not meet the requirements for this item");
                        }

                        int unfiredPotteryCount = Rs2Inventory.count(config.potteryItem().getUnfiredItemName());
                        boolean hasUnfiredPotteryItem = Rs2Inventory.contains(config.potteryItem().getUnfiredItemName());
                        Rs2Walker.walkMiniMap(config.location().getOvenWorldPoint(), 2);
                        if (!isNearPotteryOven(config, 4)) {
                            return;
                        }

                        Rs2Inventory.useItemOnObject(config.potteryItem().getUnfiredItemID(), config.location().getOvenObjectID());
                        Rs2Widget.sleepUntilHasWidget("What would you like to fire in the oven?");

                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        sleepUntil(() -> !hasUnfiredPotteryItem && !Rs2Player.isInteracting(), (5000 * unfiredPotteryCount));
                        state = State.BANK;
                        break;
                    case REFILLING:
                        if(config.humidifyItem().equals(HumidifyItems.BUCKET)){
                            Rs2Walker.walkTo(config.location().getWellWaterPoint(), 4);
                            if (!isNearWellWaterPoint(config, 4)) {
                                return;
                            }
                        } else {
                            Rs2Walker.walkTo(config.location().getWaterPoint(), 4);
                            if (!isNearWaterPoint(config, 4)) {
                                return;
                            }
                        }

                        sleep(Random.random(1200, 1800));

                        Rs2Inventory.useItemOnObject(config.humidifyItem().getItemID(), config.location().getWaterPointObjectID());
                        sleepUntil(() -> !Rs2Inventory.contains(config.humidifyItem().getItemName()) && !Rs2Player.isInteracting(), 28000);
                        state = State.BANK;
                        break;
                    case BANK:
                        boolean isBankOpen = Rs2Bank.walkToBankAndUseBank();
                        if (!isBankOpen || !Rs2Bank.isOpen()) {
                            return;
                        }

                        Rs2Bank.depositAll();

                        boolean hasRequiredHumidifyItemInBank = Rs2Bank.hasBankItem(config.humidifyItem().getFilledItemName(), 14);
                        boolean hasEmptyHumidifyItemInBank = Rs2Bank.hasBankItem(config.humidifyItem().getItemName(), true);
                        boolean hasClayItemInBank = Rs2Bank.hasBankItem("clay", 14, true);
                        boolean hasSoftItemClayInBank = Rs2Bank.hasBankItem("soft clay", true);

                        if(!hasClayItemInBank && !hasSoftItemClayInBank){
                            Rs2Bank.closeBank();
                            Microbot.showMessage("No clay and soft clay found in bank");
                            shutdown();
                            break;
                        }

                        if(hasEmptyHumidifyItemInBank && config.forceRefill()){
                            Rs2Bank.withdrawAll(config.humidifyItem().getItemName(), true);
                            Rs2Bank.closeBank();
                            state = State.REFILLING;
                            break;
                        }

                        if (hasClayItemInBank) {
                            if(!hasRequiredHumidifyItemInBank){
                                if (hasEmptyHumidifyItemInBank) {
                                    Rs2Bank.withdrawAll(config.humidifyItem().getItemName(), true);
                                    Rs2Bank.closeBank();
                                    state = State.REFILLING;
                                } else {
                                    Rs2Bank.closeBank();
                                    Microbot.showMessage("Not enough " + config.humidifyItem().getItemName() + " found in bank");
                                    shutdown();
                                }
                                break;
                            }

                            Rs2Bank.withdrawX(config.humidifyItem().getFilledItemName(), 14);
                            Rs2Bank.withdrawX("clay", 14);
                            Rs2Bank.closeBank();
                            sleep(Random.random(600, 800));
                            state = State.HUMIDIFY;
                            break;
                        }

                        if (config.potteryItem().equals(PotteryItems.CUP)) {
                            Rs2Bank.withdrawX("soft clay", 7);
                        } else {
                            Rs2Bank.withdrawAll("soft clay");
                        }
                        Rs2Bank.closeBank();
                        sleep(Random.random(600, 800));
                        state = State.SPINNING;
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private void getPotteryState(PotteryConfig config) {
        boolean hasClayItem = Rs2Inventory.hasItem("clay", true);
        boolean hasSoftClayItem = Rs2Inventory.hasItem("soft clay");

        if (hasEmptyHumidifyItem(config) && (getEmptyHumidifyCount(config) + getHumidifyCount(config) + Rs2Inventory.getEmptySlots() == 28)) {
            init = false;
            state = State.REFILLING;
            return;
        }

        if (Rs2Bank.isNearBank(8)) {
            if (hasClayItem && hasHumidifyItem(config)) {
                init = false;
                state = State.HUMIDIFY;
                return;
            }
            init = false;
            state = State.BANK;
            return;
        }

        if (isNearWaterPoint(config, 8) || isNearWellWaterPoint(config, 8)) {
            if (!hasEmptyHumidifyItem(config)) {
                init = false;
                state = State.BANK;
                return;
            }
            init = false;
            state = State.REFILLING;
            return;
        }

        if (isNearPotteryWheel(config, 8)) {
            if (hasSoftClayItem) {
                init = false;
                state = State.SPINNING;
                return;
            }

            if (hasUnfiredPotteryItem(config)) {
                init = false;
                state = State.COOKING;
                return;
            }

            init = false;
            state = State.BANK;
            return;
        }

        if (isNearPotteryOven(config, 8)) {
            if (!hasUnfiredPotteryItem(config)) {
                init = false;
                state = State.BANK;
                return;
            }

            init = false;
            state = State.COOKING;
            return;
        }

        init = false;
        state = State.BANK;
    }

    private boolean isNearWaterPoint(PotteryConfig config, int distance) {
        return Rs2Player.getWorldLocation().distanceTo(config.location().getWaterPoint()) <= distance;
    }

    private boolean isNearWellWaterPoint(PotteryConfig config, int distance) {
        return Rs2Player.getWorldLocation().distanceTo(config.location().getWellWaterPoint()) <= distance;
    }

    private boolean isNearPotteryWheel(PotteryConfig config, int distance) {
        return Rs2Player.getWorldLocation().distanceTo(config.location().getWheelWorldPoint()) <= distance;
    }

    private boolean isNearPotteryOven(PotteryConfig config, int distance) {
        return Rs2Player.getWorldLocation().distanceTo(config.location().getOvenWorldPoint()) <= distance;
    }

    private boolean hasEmptyHumidifyItem(PotteryConfig config) {
        return Rs2Inventory.hasItem(config.humidifyItem().getItemID());
    }

    private boolean hasHumidifyItem(PotteryConfig config) {
        return Rs2Inventory.hasItem(config.humidifyItem().getFilledItemID());
    }

    private boolean hasUnfiredPotteryItem(PotteryConfig config) {
        return Rs2Inventory.hasItem(config.potteryItem().getUnfiredItemID());
    }

    private int getEmptyHumidifyCount(PotteryConfig config){
        return Rs2Inventory.count(config.humidifyItem().getItemID());
    }

    private int getHumidifyCount(PotteryConfig config){
        return Rs2Inventory.count(config.humidifyItem().getFilledItemID());
    }
}
