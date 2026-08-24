package com.hubitems;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public final class HubItems extends JavaPlugin implements Listener, CommandExecutor {

    private final String HUB_WORLD_NAME = "HUB";

    private final Set<UUID> pvpActive = new HashSet<>();
    private final Set<UUID> playersHidden = new HashSet<>();
    private final Map<UUID, BukkitTask> pvpTimers = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("hubitemhub") != null) {
            getCommand("hubitemhub").setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("hubitemhub")) {
            Player target = null;
            if (args.length > 0) {
                target = Bukkit.getPlayer(args[0]);
            } else if (sender instanceof Player) {
                target = (Player) sender;
            }

            if (target != null && target.isOnline()) {
                giveHubItems(target);
            }
            return true;
        }
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    giveHubItems(player);
                }
            }
        }.runTaskLater(this, 20L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    giveHubItems(player);
                }
            }
        }.runTaskLater(this, 10L);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    giveHubItems(player);
                }
            }
        }.runTaskLater(this, 10L);
    }

    public void giveHubItems(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) {
            disablePvPMode(player);
            return;
        }

        player.getInventory().clear();
        disablePvPMode(player);

        // Slot 1 (Indice 0): Spada
        player.getInventory().setItem(0, createItem(Material.DIAMOND_SWORD, ChatColor.RED + "Spada PvP (Tieni 3s)"));

        // Slot 2 (Indice 1): Lana (Scompare dopo 3 sec)
        player.getInventory().setItem(1, createItem(Material.WHITE_WOOL, ChatColor.YELLOW + "Lana Temporanea (3 sec)", 64));

        // Slot 5 (Indice 4): Menu Modalità (Palla)
        player.getInventory().setItem(4, createItem(Material.HEART_OF_THE_SEA, ChatColor.GRAY + "" + ChatColor.BOLD + "ᴍᴇɴᴜ ᴍᴏᴅᴀʟɪᴛᴀ̀ " + ChatColor.DARK_GRAY + "(Tasto Destro)"));

        // Slot 8 (Indice 7): Visibilità
        updateVisibilityItem(player);

        // Slot 9 (Indice 8): Ender Bat
        player.getInventory().setItem(8, createItem(Material.ENDER_PEARL, ChatColor.LIGHT_PURPLE + "Ender Bat"));

        player.updateInventory();
    }

    private ItemStack createItem(Material mat, String name) {
        return createItem(mat, name, 1);
    }

    private ItemStack createItem(Material mat, String name, int amount) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    // BLOCCO DROP OGGETTI NEL MONDO HUB
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (event.getPlayer().getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) return;

        if (pvpTimers.containsKey(player.getUniqueId())) {
            pvpTimers.get(player.getUniqueId()).cancel();
            pvpTimers.remove(player.getUniqueId());
        }

        ItemStack item = player.getInventory().getItem(event.getNewSlot());

        if (event.getNewSlot() == 0 && item != null && item.getType() == Material.DIAMOND_SWORD) {
            player.sendMessage(ChatColor.YELLOW + "Tieni la spada per 3 secondi...");

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    enablePvPMode(player);
                }
            }.runTaskLater(this, 60L);

            pvpTimers.put(player.getUniqueId(), task);
        } else {
            if (pvpActive.contains(player.getUniqueId())) {
                disablePvPMode(player);
            }
        }
    }

    private void enablePvPMode(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) return;

        pvpActive.add(player.getUniqueId());
        if (player.getEquipment() != null) {
            player.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
            player.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
            player.getEquipment().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
            player.getEquipment().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));
        }
        player.sendMessage(ChatColor.GREEN + "PvP Attivato!");
    }

    private void disablePvPMode(Player player) {
        pvpActive.remove(player.getUniqueId());
        if (player.getEquipment() != null) {
            player.getEquipment().setArmorContents(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();

            if (!attacker.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) return;

            if (pvpActive.contains(attacker.getUniqueId()) && pvpActive.contains(victim.getUniqueId())) {
                event.setCancelled(false);
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) return;

        if (event.getBlockPlaced().getType() == Material.WHITE_WOOL) {
            event.setCancelled(false);
            Block block = event.getBlockPlaced();

            player.getInventory().setItem(1, createItem(Material.WHITE_WOOL, ChatColor.YELLOW + "Lana Temporanea (3 sec)", 64));

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (block.getType() == Material.WHITE_WOOL) {
                        block.setType(Material.AIR);
                    }
                }
            }.runTaskLater(this, 60L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (item.getType() == Material.ENDER_PEARL) {
            event.setCancelled(true);

            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Vector dir = player.getLocation().getDirection().multiply(1.8);
                dir.setY(0.6);
                player.setVelocity(dir);
            }
            return;
        }

        if (item.getType() == Material.LIME_DYE || item.getType() == Material.GRAY_DYE) {
            event.setCancelled(true);
            if (playersHidden.contains(player.getUniqueId())) {
                playersHidden.remove(player.getUniqueId());
                for (Player target : Bukkit.getOnlinePlayers()) player.showPlayer(this, target);
            } else {
                playersHidden.add(player.getUniqueId());
                for (Player target : Bukkit.getOnlinePlayers()) {
                    if (!target.equals(player)) player.hidePlayer(this, target);
                }
            }
            updateVisibilityItem(player);
        }
    }

    private void updateVisibilityItem(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) return;

        boolean hidden = playersHidden.contains(player.getUniqueId());
        Material dyeMat = hidden ? Material.GRAY_DYE : Material.LIME_DYE;
        String name = hidden ? ChatColor.RED + "Giocatori: NASCOSTI" : ChatColor.GREEN + "Giocatori: VISIBILI";
        player.getInventory().setItem(7, createItem(dyeMat, name));
        player.updateInventory();
    }
}
