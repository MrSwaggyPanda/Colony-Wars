package com.ithinkrok.mccw.event;

import com.ithinkrok.mccw.data.User;
import org.bukkit.inventory.PlayerInventory;

/**
 * Created by paul on 13/11/15.
 * <p>
 * An event for when a player gets an upgrade
 */
public class UserUpgradeEvent extends UserEvent {

    private String upgradeName;
    private int upgradeLevel;

    public UserUpgradeEvent(User user, String upgradeName, int upgradeLevel) {
        super(user);
        this.upgradeName = upgradeName;
        this.upgradeLevel = upgradeLevel;
    }

    public String getUpgradeName() {
        return upgradeName;
    }

    public int getUpgradeLevel() {
        return upgradeLevel;
    }


}
