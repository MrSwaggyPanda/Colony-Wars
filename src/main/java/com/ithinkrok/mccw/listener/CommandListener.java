package com.ithinkrok.mccw.listener;

import com.ithinkrok.mccw.WarsPlugin;
import com.ithinkrok.mccw.data.Team;
import com.ithinkrok.mccw.data.User;
import com.ithinkrok.mccw.enumeration.PlayerClass;
import com.ithinkrok.mccw.enumeration.TeamColor;
import com.ithinkrok.mccw.util.InventoryUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Created by paul on 13/11/15.
 *
 * Handles commands for Colony Wars
 */
public class CommandListener implements CommandExecutor {

    private WarsPlugin plugin;

    public CommandListener(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("You must be a player to execute Colony Wars commands");
            return true;
        }

        Player player = (Player) sender;

        switch (command.getName().toLowerCase()) {
            case "transfer":
                if (args.length < 1) return false;

                try {
                    int amount = Integer.parseInt(args[0]);

                    User user = plugin.getUser(player);
                    if (!user.subtractPlayerCash(amount)) {
                        user.message(ChatColor.RED + "You do not have that amount of money");
                        return true;
                    }

                    Team team = user.getTeam();
                    team.addTeamCash(amount);

                    team.message(user.getFormattedName() + ChatColor.DARK_AQUA + " transferred " +
                            ChatColor.GREEN + "$" + amount +
                            ChatColor.YELLOW + " to your team's account!");
                    team.message("Your Team's new Balance is: " + ChatColor.GREEN + "$" + team.getTeamCash() +
                            ChatColor.YELLOW + "!");

                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }

            case "test":
                return args.length >= 1 && onTestCommand(player, args);

            default:
                return false;
        }

    }

    private boolean onTestCommand(Player player, String[] args) {
        User user = plugin.getUser(player);

        switch (args[0]) {
            case "team":
                if (args.length < 2) return false;

                TeamColor teamColor = TeamColor.valueOf(args[1].toUpperCase());
                user.setTeamColor(teamColor);

                user.message("You were changed to team " + teamColor);

                break;
            case "class":
                if (args.length < 2) return false;

                PlayerClass playerClass = PlayerClass.valueOf(args[1].toUpperCase());
                user.setPlayerClass(playerClass);

                user.message("You were changed to class " + playerClass);

                break;
            case "money":

                user.addPlayerCash(10000);
                user.getTeam().addTeamCash(10000);

                user.message("10000 added to both you and your team's balance");
                break;
            case "build":
                if (args.length < 2) return false;

                player.getInventory()
                        .addItem(InventoryUtils.createItemWithNameAndLore(Material.LAPIS_ORE, 16, 0, args[1]));

                user.message("Added 16 " + args[1] + " build blocks to your inventory");
                break;
            case "start_game":
                user.message("Attempting to start a new game!");

                plugin.getCountdownHandler().stopCountdown();
                plugin.startGame();

                break;
            case "start_showdown":
                user.message("Attempting to start showdown");

                plugin.getCountdownHandler().stopCountdown();
                plugin.getGameInstance().startShowdown();
                break;
            case "base_location":
                Team team = user.getTeam();
                if (team == null) {
                    user.message("Your team is null");
                    break;
                }

                user.message("Base location: " + team.getBaseLocation());
                break;

        }

        return true;
    }
}
