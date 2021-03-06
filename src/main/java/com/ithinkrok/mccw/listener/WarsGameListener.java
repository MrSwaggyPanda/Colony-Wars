package com.ithinkrok.mccw.listener;

import com.ithinkrok.mccw.WarsPlugin;
import com.ithinkrok.mccw.data.Building;
import com.ithinkrok.mccw.data.Schematic;
import com.ithinkrok.mccw.data.Team;
import com.ithinkrok.mccw.data.User;
import com.ithinkrok.mccw.enumeration.GameState;
import com.ithinkrok.mccw.event.UserAttackEvent;
import com.ithinkrok.mccw.event.UserInteractEvent;
import com.ithinkrok.mccw.inventory.InventoryHandler;
import com.ithinkrok.mccw.playerclass.PlayerClassHandler;
import com.ithinkrok.mccw.strings.Buildings;
import com.ithinkrok.mccw.util.Facing;
import com.ithinkrok.mccw.util.SchematicBuilder;
import com.ithinkrok.mccw.util.TreeFeller;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Created by paul on 01/11/15.
 * <p>
 * Listens for Bukkit events while the game is in progress
 */
public class WarsGameListener implements Listener {

    private static HashMap<PotionEffectType, Boolean> GOOD_POTIONS = new HashMap<>();

    static {
        GOOD_POTIONS.put(PotionEffectType.HEAL, true);
        GOOD_POTIONS.put(PotionEffectType.HARM, false);

        //Add more potions if required
    }

    private WarsPlugin plugin;

    public WarsGameListener(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        User user = plugin.getUser(event.getPlayer());
        if (user == null) {
            event.setCancelled(true);
            return;
        }

        if (user.isInGame() && user.getTeamColor() != null) {
            String playerClass = user.getPlayerClass().toString();
            String teamColor = user.getTeamColor().chatColor.toString();
            event.setFormat(teamColor + "<" + ChatColor.DARK_GRAY + "[" + ChatColor.GRAY + playerClass +
                    ChatColor.DARK_GRAY + "] %s" + teamColor + "> " + ChatColor.WHITE + "%s");
        } else {
            event.setFormat(ChatColor.LIGHT_PURPLE + "<" + ChatColor.GRAY + "%s" + ChatColor.LIGHT_PURPLE + "> " +
                    ChatColor.WHITE + "%s");
        }

        //        plugin.getUsers().stream()
        //                .filter(other -> other.getTeamColor() != user.getTeamColor() && !other.getPlayer().isOp())
        //                .forEach(other -> event.getRecipients().remove(other.getPlayer()));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        User user = plugin.getUser(event.getPlayer());

        user.setSpectator();
        user.getPlayer().teleport(plugin.getMapSpawn(null));

        user.message(plugin.getLocale("game-in-progress"));
        user.message(plugin.getLocale("game-wait-next"));
        user.message(plugin.getLocale("spectate-heads"));
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        switch (event.getEntity().getItemStack().getType()) {
            case GOLD_INGOT:
            case DIAMOND:
                return;
            default:
                event.setCancelled(true);
        }
    }


    @EventHandler
    public void onPickupItem(PlayerPickupItemEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) {
            event.setCancelled(true);
            return;
        }

        User user = plugin.getUser(event.getPlayer());

        if (!user.isInGame()) {
            event.setCancelled(true);
            return;
        }

        switch (event.getItem().getItemStack().getType()) {
            case GOLD_INGOT:
                giveCashPerItem(event, 120, 80);
                break;
            case DIAMOND:
                giveCashPerItem(event, 1200, 800);
                break;
        }

        event.setCancelled(true);
        event.getItem().remove();
    }

    private void giveCashPerItem(PlayerPickupItemEvent event, int playerCash, int teamCash) {
        User user = plugin.getUser(event.getPlayer());

        user.addPlayerCash(playerCash * event.getItem().getItemStack().getAmount());
        event.getPlayer().playSound(event.getItem().getLocation(), Sound.ORB_PICKUP, 1.0f,
                0.8f + (plugin.getRandom().nextFloat()) * 0.4f);

        Team team = user.getTeam();
        team.addTeamCash(teamCash * event.getItem().getItemStack().getAmount());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        User user = plugin.getUser(event.getPlayer());

        if (!user.isInGame()) {
            event.setCancelled(true);
            return;
        }

        if (event.getBlock().getType() != Material.LAPIS_ORE) return;

        ItemMeta meta = event.getItemInHand().getItemMeta();
        if (!meta.hasDisplayName()) return;

        Schematic schematic = plugin.getSchematicData(meta.getDisplayName());
        if (schematic == null) {
            user.message(ChatColor.RED + "Unknown building!");
            event.setCancelled(true);
            return;
        }

        int rotation = Facing.getFacing(event.getPlayer().getLocation().getYaw());

        if (!SchematicBuilder
                .buildSchematic(plugin, schematic, event.getBlock().getLocation(), rotation, user.getTeamColor())) {
            event.setCancelled(true);
            user.message(ChatColor.RED + "You cannot build that here!");
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        User user = plugin.getUser(event.getPlayer());

        if (!user.isInGame()) {
            event.setCancelled(true);
            return;
        }

        resetDurability(event.getPlayer());

        if (event.getBlock().getType() != Material.OBSIDIAN) return;

        Building building = plugin.getBuildingInfo(event.getBlock().getLocation());
        if (building == null) {
            plugin.getLogger().warning("The player destroyed an obsidian block, but it wasn't a building. Odd");
            plugin.getLogger().warning("Obsidian location: " + event.getBlock().getLocation());
            user.message(ChatColor.RED + "That obsidian block does not appear to be part of a building");
            return;
        }

        if (user.getTeamColor() == building.getTeamColor()) {
            user.message(ChatColor.RED + "You cannot destroy your own team's buildings!");
            event.setCancelled(true);
            return;
        }

        if (Buildings.BASE.equals(building.getBuildingName())) {
            user.message(ChatColor.RED + "You cannot destroy other team's bases!");
            return;
        }

        building.explode();
    }

    private void resetDurability(Player player) {
        ItemStack item = player.getItemInHand();

        if (item == null) return;
        if (item.getDurability() != 0 && item.getType().getMaxDurability() != 0) {
            item.setDurability((short) 0);
            player.setItemInHand(item);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        event.setDroppedExp(0);
        event.getDrops().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExp(BlockExpEvent event) {
        switch (event.getBlock().getType()) {
            case GOLD_ORE:
                event.getBlock().getWorld()
                        .dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.GOLD_INGOT, 3));
                break;
            case LOG:
            case LOG_2:
                if (plugin.isInBuilding(event.getBlock().getLocation())) break;
                event.getBlock().getWorld()
                        .dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.GOLD_INGOT, 1));
                TreeFeller.fellTree(plugin.getGameInstance(), event.getBlock().getLocation());
                break;
            case DIAMOND_ORE:
                event.getBlock().getWorld()
                        .dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.DIAMOND, 1));
        }

        event.getBlock().setType(Material.AIR);
    }

    @EventHandler
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
        event.getBrokenItem().setDurability((short) 0);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        User user = plugin.getUser(event.getPlayer());

        if (!user.isInGame()) {
            event.setCancelled(true);
            return;
        }

        resetDurability(event.getPlayer());

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock().getType() != Material.OBSIDIAN) {
            PlayerClassHandler classHandler = user.getPlayerClassHandler();

            if (classHandler.onInteractWorld(new UserInteractEvent(user, event))) event.setCancelled(true);
            return;
        }

        Building building = plugin.getBuildingInfo(event.getClickedBlock().getLocation());

        event.setCancelled(true);

        user.openShopInventory(building.getCenterBlock());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (plugin.getGameState() != GameState.SHOWDOWN) return;
        if (event.getPlayer().getAllowFlight()) return;

        if (!plugin.getShowdownArena().isInBounds(event.getTo())) {
            if (!plugin.getShowdownArena().isInBounds(event.getFrom())) {
                event.getPlayer().teleport(plugin.getMapSpawn(null));
                plugin.messageAll(event.getPlayer().getDisplayName() + ChatColor.GOLD + " was teleported back to the " +
                        "center!");
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        resetDurability((Player) event.getEntity());
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player damager = getPlayerFromEntity(event.getDamager());
        if (damager == null) {
            Entity ent = event.getDamager();
            if (!ent.hasMetadata("team")) return;

            User hurt = plugin.getUser(getPlayerFromEntity(event.getEntity()));
            if (hurt == null) return;

            if (ent.getMetadata("team").get(0).value() == hurt.getTeamColor()) {
                event.setCancelled(true);
            }
            return;
        }

        User damagerInfo = plugin.getUser(damager);

        if (!damagerInfo.isInGame()) {
            event.setCancelled(true);
            return;
        }

        resetDurability(damager);

        if (!(event.getEntity() instanceof Player)) {
            if (event.getEntity() instanceof Tameable) {
                Tameable tameable = (Tameable) event.getEntity();
                if (onPlayerAttackTameable(event, damagerInfo, tameable)) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK)
                userAttackNonUser(new UserAttackEvent(damagerInfo, null, event));
            return;
        }

        Player target = (Player) event.getEntity();

        if (damagerInfo.getTeamColor() == plugin.getUser(target).getTeamColor()) {
            event.setCancelled(true);
            return;
        }

        User targetInfo = plugin.getUser(target);

        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            userAttackUser(new UserAttackEvent(damagerInfo, targetInfo, event));
        }


        if (target.getHealth() - event.getFinalDamage() < 1) {
            if (event.getDamager() instanceof Tameable) {
                Creature creature = (Creature) event.getDamager();
                creature.setTarget(null);
            }

            event.setCancelled(true);
            playerDeath(target, damager, true);
        }
    }

    public Player getPlayerFromEntity(Entity entity) {

        if (!(entity instanceof Player)) {
            if (entity instanceof Projectile) {
                Projectile arrow = (Projectile) entity;

                if (!(arrow.getShooter() instanceof Player)) return null;
                return (Player) arrow.getShooter();
            } else if (entity instanceof Tameable) {
                Tameable tameable = (Tameable) entity;
                if (tameable.getOwner() == null || !(tameable.getOwner() instanceof Player)) return null;
                return (Player) tameable.getOwner();
            } else {
                List<MetadataValue> values = entity.getMetadata("striker");
                if (values == null || values.isEmpty()) return null;

                User user = plugin.getUser((UUID) values.get(0).value());
                if (user == null) return null;
                return user.getPlayer();
            }
        } else {
            return (Player) entity;
        }
    }

    public boolean onPlayerAttackTameable(EntityDamageByEntityEvent event, User player, Tameable tameable) {
        if (tameable.getOwner() != null && tameable.getOwner() instanceof Player) {
            User owner = plugin.getUser((Player) tameable.getOwner());

            if (!owner.isInGame()) {
                event.getEntity().remove();
                return true;
            }

            if (owner.getTeamColor() == player.getTeamColor()) return true;
        }

        return false;
    }

    private void userAttackNonUser(UserAttackEvent event) {
        if (event.getWeapon() == null) return;

        PlayerClassHandler classHandler = event.getUser().getPlayerClassHandler();
        classHandler.onUserAttack(event);
    }

    private void userAttackUser(UserAttackEvent event) {
        ItemStack weapon = event.getWeapon();

        if (weapon == null) return;

        if (weapon.containsEnchantment(Enchantment.FIRE_ASPECT)) {
            event.getTargetUser().setFireAttacker(event.getUser());
        }

        PlayerClassHandler classHandler = event.getUser().getPlayerClassHandler();
        classHandler.onUserAttack(event);
    }

    public void playerDeath(Player died, Player killer, boolean intentionally) {
        if (plugin.getGameState() == GameState.AFTERMATH) return;
        User diedInfo = plugin.getUser(died);

        if (!diedInfo.isInGame()) {
            diedInfo.resetPlayerStats(true);

            died.teleport(plugin.getMapSpawn(null));
            return;
        }

        removeEntityTargets(died);

        User killerInfo = killer == null ? null : plugin.getUser(killer);
        if (killerInfo != null) {
            if (intentionally) plugin.messageAll(plugin.getLocale("player-killed-player", diedInfo.getFormattedName(),
                    killerInfo.getFormattedName()));
            else plugin.messageAll(plugin.getLocale("player-died-last-attacker", diedInfo.getFormattedName(),
                    killerInfo.getFormattedName()));
        } else {
            plugin.messageAll(plugin.getLocale("player-died", diedInfo.getFormattedName()));
        }

        Team diedTeam = diedInfo.getTeam();
        boolean respawn = plugin.getGameState() != GameState.SHOWDOWN &&
                plugin.getRandom().nextFloat() < (diedTeam.getRespawnChance() / 100f);

        if (respawn) {
            plugin.messageAll(diedInfo.getFormattedName() + ChatColor.GOLD + " has respawned!");

            diedTeam.setRespawnChance(diedTeam.getRespawnChance() - 15);
            diedTeam.respawnPlayer(died);

            diedInfo.resetPlayerStats(false);

            diedTeam.message(
                    ChatColor.GOLD + "Your revival chance is now " + ChatColor.DARK_AQUA + diedTeam.getRespawnChance());
        } else {
            plugin.messageAll(diedInfo.getFormattedName() + ChatColor.GOLD + " did not respawn!");
            diedInfo.removeFromGame();
            diedInfo.setSpectator();
        }
    }

    private void removeEntityTargets(Player player) {
        for (Entity e : player.getWorld().getEntities()) {
            if (!(e instanceof Creature)) continue;

            Creature creature = (Creature) e;
            if (creature.getTarget() == null || creature.getTarget() != player) continue;
            creature.setTarget(null);
        }
    }


    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        ProjectileSource projectileSource = event.getEntity().getShooter();

        if (!(projectileSource instanceof Player)) return;
        Player shooter = (Player) projectileSource;
        User shooterInfo = plugin.getUser(shooter);

        if (!shooterInfo.isInGame() || shooterInfo.getTeamColor() == null) {
            event.setCancelled(true);
            return;
        }

        //If NPE then add potion to GOOD_POTIONS
        boolean good = GOOD_POTIONS.get(event.getPotion().getEffects().iterator().next().getType());

        for (LivingEntity ent : event.getAffectedEntities()) {
            if (!(ent instanceof Player)) continue;

            Player player = (Player) ent;
            User user = plugin.getUser(player);

            if (!user.isInGame() || user.getTeamColor() == null ||
                    (user.getTeamColor() == shooterInfo.getTeamColor()) != good) {
                event.setIntensity(ent, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        User target = plugin.getUser((Player) event.getEntity());

        if (target.getPlayer().getHealth() - event.getFinalDamage() < 1) {
            event.setCancelled(true);

            User killer = null;

            switch (event.getCause()) {
                case FIRE_TICK:
                    killer = target.getFireAttacker();
                    break;
                case WITHER:
                    killer = target.getWitherAttacker();
                    break;
            }

            if (killer == null) {
                if (target.getLastAttacker() != null)
                    playerDeath(target.getPlayer(), target.getLastAttacker().getPlayer(), false);
                else playerDeath(target.getPlayer(), null, false);
            } else playerDeath(target.getPlayer(), killer.getPlayer(), true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        User diedInfo = plugin.getUser(event.getPlayer());
        Team diedTeam = diedInfo.getTeam();
        if (diedTeam == null) return;

        diedInfo.removeFromGame();
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.getBlock().setType(Material.AIR);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getSlotType().equals(InventoryType.SlotType.ARMOR)) event.setCancelled(true);

        User user = plugin.getUser((Player) event.getWhoClicked());
        if (!user.isInGame() || user.getTeamColor() == null) {
            plugin.handleSpectatorInventory(event);
            return;
        }

        if (event.getInventory().getType() != InventoryType.PLAYER &&
                event.getInventory().getType() != InventoryType.CRAFTING) {

            event.setCancelled(true);

            if (event.getCurrentItem() == null) return;

            Team team = user.getTeam();

            PlayerClassHandler classHandler = user.getPlayerClassHandler();

            Building building = plugin.getBuildingInfo(user.getShopBlock());
            if (building == null) return;

            boolean done = classHandler.onInventoryClick(event.getCurrentItem(), building, user, team);

            if (done) return;

            InventoryHandler handler = plugin.getBuildingInventoryHandler();
            if (handler == null) return;

            handler.onInventoryClick(event.getCurrentItem(), building, user, team);
        }
    }


    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();

        User user = plugin.getUser(player);

        if (user.getShopInventory() == null || user.getShopInventory().getTitle() == null) return;

        if (user.getShopInventory().getTitle().equals(event.getInventory().getTitle())) {
            user.shopInventoryClosed();
        }
    }


}
