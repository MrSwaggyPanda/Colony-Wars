package com.ithinkrok.mccw.inventory;

import com.ithinkrok.mccw.event.ItemPurchaseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Created by paul on 06/11/15.
 *
 * A buyable item
 */
public class ItemBuyable extends Buyable {

    protected ItemStack purchase;
    protected boolean allowMultiple;

    protected String upgradeToken;

    public ItemBuyable(ItemStack display, String buildingName, int cost, boolean allowMultiple) {
        this(display, display.clone(), buildingName, cost, false, allowMultiple);
    }

    public ItemBuyable(ItemStack display, ItemStack purchase, String buildingName, int cost, boolean team,
                       boolean allowMultiple) {
        super(display, buildingName, cost, team, 1);

        this.purchase = purchase;
        this.allowMultiple = allowMultiple;

        this.upgradeToken = UUID.randomUUID().toString();
    }

    @Override
    public void onPurchase(ItemPurchaseEvent event) {
        event.getUserInventory().addItem(purchase.clone());

        int newLevel = event.getUser().getUpgradeLevel(upgradeToken) + 1;
        event.getUser().setUpgradeLevel(upgradeToken, newLevel);
    }

    @Override
    public boolean canBuy(ItemPurchaseEvent event) {
        return allowMultiple || event.getUser().getUpgradeLevel(upgradeToken) == 0;
    }
}
