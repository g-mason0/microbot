package net.runelite.client.plugins.microbot.pottery;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.pottery.enums.HumidifyAction;
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
    public State state;

    public boolean run(PotteryConfig config) {
        Microbot.enableAutoRunOn = false;
        Rs2Camera.setAngle(0);
        getPotteryState(config);

        if (!config.location().hasRequirements()) {
            Microbot.showMessage("You do not meet the requirements for this location");
            shutdown();
            return false;
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Microbot.pauseAllScripts) return;

                long startTime = System.currentTimeMillis();

                switch (state) {
                    case HUMIDIFY:
                        if (getClayItemCount() >= 1 && getHumidifyItemCount(config) >= getClayItemCount()) {
                            Rs2Inventory.combine("clay", config.humidifyItem().getFilledItemName());
                            sleep(Random.random(600, 800));
                            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                            Rs2Inventory.waitForInventoryChanges();
                            return;
                        }

                        state = State.BANK;
                        break;
                    case SPINNING:
                        if (!config.potteryItem().hasRequirements()) {
                            Microbot.showMessage("You do not meet the requirements for this item");
                        }

                        if (!isNearPotteryWheel(config, 4)) {
                            Rs2Walker.walkTo(config.location().getWheelWorldPoint(), 4);
                            sleepUntil(() -> isNearPotteryWheel(config, 4));
                        }

                        Rs2Walker.walkFastCanvas(config.location().getWheelWorldPoint());
                        sleepUntil(() -> isNearPotteryWheel(config, 1));

                        Rs2Inventory.useItemOnObject(ItemID.SOFT_CLAY, config.location().getWheelObjectID());
                        Rs2Widget.sleepUntilHasWidget("how many do you wish to make?");

                        Rs2Widget.clickWidget(config.potteryItem().getUnfiredWheelWidgetID());

                        sleepUntil(() -> getSoftClayItemCount() == 0 && PotteryPlugin.hasPlayerStoppedAnimating());
                        state = State.COOKING;
                        break;
                    case COOKING:
                        if (!config.potteryItem().hasRequirements()) {
                            Microbot.showMessage("You do not meet the requirements for this item");
                            shutdown();
                            return;
                        }

                        if (!isNearPotteryOven(config, 4)) {
                            Rs2Walker.walkTo(config.location().getOvenWorldPoint(), 4);
                            sleepUntil(() -> isNearPotteryOven(config, 4));
                        }

                        Rs2Walker.walkFastCanvas(config.location().getOvenWorldPoint());
                        sleepUntil(() -> isNearPotteryOven(config, 1));

                        Rs2Inventory.useItemOnObject(config.potteryItem().getUnfiredItemID(), config.location().getOvenObjectID());
                        Rs2Widget.sleepUntilHasWidget("What would you like to fire in the oven?");

                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        sleepUntil(() -> getUnfiredPotteryItemCount(config) == 0 && PotteryPlugin.hasPlayerStoppedAnimating());
                        state = State.BANK;
                        break;
                    case REFILLING:
                        if (config.humidifyItem().equals(HumidifyItems.BUCKET)) {
                            Rs2Walker.walkTo(config.location().getWellWaterPoint(), 4);
                            if (!isNearWellWaterPoint(config, 4)) return;

                            Rs2Inventory.useItemOnObject(config.humidifyItem().getItemID(), config.location().getWellWaterPointObjectID());
                        } else {
                            Rs2Walker.walkTo(config.location().getWaterPoint(), 4);
                            if (!isNearWaterPoint(config, 4)) return;

                            Rs2Inventory.useItemOnObject(config.humidifyItem().getItemID(), config.location().getWaterPointObjectID());
                        }

                        sleepUntil(() -> getEmptyHumidifyItemCount(config) == 0 & PotteryPlugin.hasPlayerStoppedAnimating());
                        state = State.BANK;
                        break;
                    case BANK:
                        boolean isBankOpen = Rs2Bank.walkToBankAndUseBank();
                        if (!isBankOpen || !Rs2Bank.isOpen()) return;

                        Rs2Bank.depositAll();

                        if (getClayItemCount() == 0 && getSoftClayItemCount() == 0) {
                            Rs2Bank.closeBank();
                            Microbot.showMessage("No clay and soft clay found in bank");
                            shutdown();
                            break;
                        }

                        if (getHumidifyAction(config).equals(HumidifyAction.ITEM)) {
                            if (getEmptyHumidifyItemCount(config) > 0 && config.forceRefill()) {
                                Rs2Bank.withdrawAll(config.humidifyItem().getItemName(), true);
                                Rs2Bank.closeBank();
                                state = State.REFILLING;
                                break;
                            }

                            if (getClayItemCount() >= 1) {
                                if (getHumidifyItemCount(config) < getClayItemCount()) {
                                    if (getEmptyHumidifyItemCount(config) == 0) {
                                        Rs2Bank.closeBank();
                                        Microbot.showMessage("Not enough " + config.humidifyItem().getItemName() + " found in bank");
                                        shutdown();
                                        return;
                                    }
                                    Rs2Bank.withdrawAll(config.humidifyItem().getItemName(), true);
                                    Rs2Bank.closeBank();
                                    state = State.REFILLING;
                                    break;
                                }

                                Rs2Bank.withdrawX(config.humidifyItem().getFilledItemName(), 14);
                                Rs2Bank.withdrawX("clay", 14);
                                Rs2Bank.closeBank();
                                sleep(Random.random(600, 800));
                                state = State.HUMIDIFY;
                                break;
                            }
                        } else {
                            /*
                             TODO: implement logic for using Humidify Lunar Spell
                            */
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
        if (getEmptyHumidifyItemCount(config) > 0 && (getEmptyHumidifyItemCount(config) + getHumidifyItemCount(config) + Rs2Inventory.getEmptySlots() == 28)) {
            state = State.REFILLING;
            return;
        }

        if (getSoftClayItemCount() > 0 && getUnfiredPotteryItemCount(config) > 0) {
            state = State.SPINNING;
            return;
        }

        if (getUnfiredPotteryItemCount(config) > 0 && getUnfiredPotteryItemCount(config) > 0) {
            state = State.COOKING;
            return;
        }

        if (Rs2Bank.isNearBank(8)) {
            if (getClayItemCount() > 0 && getHumidifyItemCount(config) > 0) {
                state = State.HUMIDIFY;
                return;
            }
            state = State.BANK;
            return;
        }

        if (isNearWaterPoint(config, 8) || isNearWellWaterPoint(config, 8)) {
            if (getEmptyHumidifyItemCount(config) == 0) {
                state = State.REFILLING;
                return;
            }
            state = State.BANK;
            return;
        }

        if (isNearPotteryWheel(config, 8)) {
            if (getSoftClayItemCount() > 0) {
                state = State.SPINNING;
                return;
            }

            if (getUnfiredPotteryItemCount(config) > 0 && getSoftClayItemCount() < 0) {
                state = State.COOKING;
                return;
            }

            state = State.BANK;
            return;
        }

        if (isNearPotteryOven(config, 8)) {
            if (getUnfiredPotteryItemCount(config) == 0) {
                state = State.COOKING;
                return;
            }
            state = State.BANK;
            return;
        }

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

    private int getEmptyHumidifyItemCount(PotteryConfig config) {
        if (Rs2Bank.isOpen()) {
            return Rs2Bank.count(config.humidifyItem().getItemName());
        }
        return Rs2Inventory.count(config.humidifyItem().getItemID());
    }

    private int getHumidifyItemCount(PotteryConfig config) {
        if (Rs2Bank.isOpen()) {
            return Rs2Bank.count(config.humidifyItem().getFilledItemName());
        }
        return Rs2Inventory.count(config.humidifyItem().getFilledItemID());
    }

    private int getUnfiredPotteryItemCount(PotteryConfig config) {
        if (Rs2Bank.isOpen()) {
            return Rs2Bank.count(config.potteryItem().getUnfiredItemName());
        }
        return Rs2Inventory.count(config.potteryItem().getUnfiredItemID());
    }

    private int getPotteryItemCount(PotteryConfig config) {
        if (Rs2Bank.isOpen()) {
            return Rs2Bank.count(config.potteryItem().getFiredItemName());
        }
        return Rs2Inventory.count(config.potteryItem().getFiredItemID());
    }

    private int getClayItemCount() {
        if (Rs2Bank.isOpen()) {
            return Rs2Bank.count("clay", true);
        }
        return Rs2Inventory.count(ItemID.CLAY);
    }

    private int getSoftClayItemCount() {
        if (Rs2Bank.isOpen()) {
            return Rs2Bank.count("soft clay", true);
        }
        return Rs2Inventory.count(ItemID.SOFT_CLAY);
    }

    private HumidifyAction getHumidifyAction(PotteryConfig config) {
        return config.humidifyAction();
    }
}
