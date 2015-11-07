package com.ithinkrok.mccw.inventory;

import com.ithinkrok.mccw.data.BuildingInfo;
import com.ithinkrok.mccw.data.PlayerInfo;
import com.ithinkrok.mccw.data.TeamInfo;
import com.ithinkrok.mccw.util.InventoryUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Created by paul on 06/11/15.
 * <p>
 * A handler for shops that use the buyable system
 */
public class BuyableInventory implements InventoryHandler {

    private List<Buyable> items = new ArrayList<>();
    private HashMap<ItemStack, Buyable> stackToBuyable = new HashMap<>();

    public BuyableInventory(Buyable... items) {
        this(Arrays.asList(items));
    }

    public BuyableInventory(List<Buyable> items) {
        this.items = items;

        for (Buyable item : items) {
            stackToBuyable.put(item.getDisplayItemStack(), item);
        }
    }

    @Override
    public boolean onInventoryClick(ItemStack item, BuildingInfo buildingInfo, PlayerInfo playerInfo,
                                    TeamInfo teamInfo) {

        Buyable i = stackToBuyable.get(item);
        if (i != null) {
            if (!i.getBuildingName().equals(buildingInfo.getBuildingName())) return false;
            tryBuyItem(i, buildingInfo, playerInfo, teamInfo);

            return true;
        }

        return false;
    }

    private void tryBuyItem(Buyable item, BuildingInfo buildingInfo, PlayerInfo playerInfo, TeamInfo teamInfo) {
        if (item.buyWithTeamMoney()) {
            if (!InventoryUtils.hasTeamCash(item.getCost(), teamInfo, playerInfo)) {
                playerInfo.message(ChatColor.RED + "Your Team and you do not have enough money to purchase this item!");
                return;
            }
        } else {
            if (!playerInfo.hasPlayerCash(item.getCost())) {
                playerInfo.message(ChatColor.RED + "You do not have enough money to purchase this item!");
                return;
            }
        }

        int requiredSlots = item.getMinFreeSlots();

        if (requiredSlots > 0) {
            PlayerInventory inventory = playerInfo.getPlayer().getInventory();
            int freeSlots = 0;

            for (int i = 0; i < inventory.getSize(); ++i) {
                ItemStack slot = inventory.getItem(i);

                if (slot != null && slot.getType() != Material.AIR) continue;
                ++freeSlots;
            }

            if (freeSlots < requiredSlots) {
                playerInfo.message(ChatColor.RED + "You do not have enough free slots in your inventory!");
                return;
            }
        }

        ItemPurchaseEvent event = new ItemPurchaseEvent(buildingInfo, playerInfo, teamInfo);

        if (!item.canBuy(event)) {
            playerInfo.message(ChatColor.RED + "You cannot buy this item!");
            playerInfo.recalculateInventory();
            return;
        }

        item.prePurchase(event);

        if (item.buyWithTeamMoney()) {
            InventoryUtils.payWithTeamCash(item.getCost(), teamInfo, playerInfo);
        } else playerInfo.subtractPlayerCash(item.getCost());

        InventoryUtils.playBuySound(playerInfo.getPlayer());
        item.onPurchase(event);
    }

    @Override
    public void addInventoryItems(List<ItemStack> items, BuildingInfo buildingInfo, PlayerInfo playerInfo,
                                  TeamInfo teamInfo) {
        for (Buyable buyable : this.items) {
            if (!buyable.getBuildingName().equals(buildingInfo.getBuildingName())) continue;
            if (!buyable.canBuy(new ItemPurchaseEvent(buildingInfo, playerInfo, teamInfo))) continue;

            items.add(buyable.getDisplayItemStack());
        }
    }
}
