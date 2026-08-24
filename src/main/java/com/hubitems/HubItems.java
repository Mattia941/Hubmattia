package com.hubitems;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class HubItems extends JavaPlugin implements Listener {

    // Nome del mondo aggiornato a HUB
    private final String HUB_WORLD_NAME = "HUB";

    // Tracciamento stato giocatori
    private final Set<UUID> pvpActive = new HashSet<>();
    private final Set<UUID> playersHidden = new HashSet<>();
    private final Map<UUID, BukkitTask> pvpTimers = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("HubItems Pro attivato per il mondo HUB!");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        giveHubItems(event.getPlayer());
        updateVisibilityFor(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        giveHubItems(player);
        if (!player.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) {
            disablePvPMode(player);
        }
    }

    private void giveHubItems(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) return;

        player.getInventory().clear();
        disablePvPMode(player);

        // Slot 1 (Indice 0): Spada Diamante
        ItemStack spada = createItem(Material.DIAMOND_SWORD, ChatColor.RED + "" + ChatColor.BOLD + "Spada PvP (Tieni 3s)");
        player.getInventory().setItem(0, spada);

        // Slot 2 (Indice 1): Lana Temporanea
        ItemStack lana = createItem(Material.WHITE_WOOL, ChatColor.YELLOW + "Lana Temporanea (5 sec)", 64);
        player.getInventory().setItem(1, lana);

        // Slot 8 (Indice 7): Visibilità Player
        updateVisibilityItem(player);

        // Slot 9 (Indice 8): Ender Bat
        ItemStack enderBat = createItem(Material.ENDER_PEARL, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Ender Bat");
        player.getInventory().setItem(8, enderBat);
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

    // --- GESTIONE PVP E SPADA ---

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) return;

        // Annulla il timer precedente se il giocatore cambia slot prima dei 3 secondi
        if (pvpTimers.containsKey(player.getUniqueId())) {
            pvpTimers.get(player.getUniqueId()).cancel();
            pvpTimers.remove(player.getUniqueId());
        }

        ItemStack newSlotItem = player.getInventory().getItem(event.getNewSlot());

        // Se seleziona lo Slot 1 con la spada di diamante
        if (event.getNewSlot() == 0 && newSlotItem != null && newSlotItem.getType() == Material.DIAMOND_SWORD) {
            player.sendMessage(ChatColor.YELLOW + "Tieni la spada per 3 secondi per attivare il PvP...");
            
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    enablePvPMode(player);
                }
            }.runTaskLater(this, 60L); // 60 tick = 3 secondi

            pvpTimers.put(player.getUniqueId(), task);
        } else {
            // Se sposta lo slot o toglie la spada, rimuove modalità e armatura
            if (pvpActive.contains(player.getUniqueId())) {
                disablePvPMode(player);
                player.sendMessage(ChatColor.RED + "Modalità PvP e armatura disattivate!");
            }
        }
    }

    private void enablePvPMode(Player player) {
        pvpActive.add(player.getUniqueId());
        
        // Equipaggia Armatura in Chainmail (Maglia di ferro)
        player.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        player.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
        player.getEquipment().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
        player.getEquipment().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));

        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Modalità PvP Attivata! Ora puoi combattere.");
    }

    private void disablePvPMode(Player player) {
        pvpActive.remove(player.getUniqueId());
        if (player.getEquipment() != null) {
            player.getEquipment().setArmorContents(null);
        }
    }

    // Bypassa le Region per consentire il PvP se entrambi i giocatori hanno la modalità attiva
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        if (attacker.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) {
            if (pvpActive.contains(attacker.getUniqueId()) && pvpActive.contains(victim.getUniqueId())) {
                event.setCancelled(false); // Bypassa il blocco della Region
            } else {
                event.setCancelled(true);
            }
        }
    }

    // --- GESTIONE BLOCCHI DI LANA TEMPORANEI (5 SECONDI) ---

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) return;

        if (event.getBlockPlaced().getType() == Material.WHITE_WOOL) {
            event.setCancelled(false); // Bypassa la protezione della Region

            Block block = event.getBlockPlaced();

            // Rimuove il blocco dopo 5 secondi (100 tick)
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (block.getType() == Material.WHITE_WOOL) {
                        block.setType(Material.AIR);
                    }
                }
            }.runTaskLater(this, 100L);
        }
    }

    // --- GESTIONE ENDER BAT (SLOT 9) & VISIBILITÀ PLAYER (SLOT 8) ---

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        // Slot 9: Ender Bat (Supera restrizioni region)
        if (item.getType() == Material.ENDER_PEARL) {
            event.setCancelled(false); // Permette il lancio bypassando la region
        }

        // Slot 8: Colorante Visibilità
        if (item.getType() == Material.LIME_DYE || item.getType() == Material.GRAY_DYE) {
            event.setCancelled(true);

            if (playersHidden.contains(player.getUniqueId())) {
                // Rendi visibili
                playersHidden.remove(player.getUniqueId());
                for (Player target : Bukkit.getOnlinePlayers()) {
                    player.showPlayer(this, target);
                }
                player.sendMessage(ChatColor.GREEN + "Giocatori resi VISIBILI!");
            } else {
                // Rendi invisibili
                playersHidden.add(player.getUniqueId());
                for (Player target : Bukkit.getOnlinePlayers()) {
                    if (!target.equals(player)) {
                        player.hidePlayer(this, target);
                    }
                }
                player.sendMessage(ChatColor.GRAY + "Giocatori NASCOSTI!");
            }
            updateVisibilityItem(player);
        }
    }

    private void updateVisibilityItem(Player player) {
        boolean hidden = playersHidden.contains(player.getUniqueId());
        Material dyeMat = hidden ? Material.GRAY_DYE : Material.LIME_DYE;
        String name = hidden ? ChatColor.RED + "Giocatori: NASCOSTI" : ChatColor.GREEN + "Giocatori: VISIBILI";

        ItemStack dye = createItem(dyeMat, name);
        player.getInventory().setItem(7, dye);
    }

    private void updateVisibilityFor(Player joinedPlayer) {
        for (UUID uuid : playersHidden) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.hidePlayer(this, joinedPlayer);
            }
        }
    }

    // Nasconde la chat dei giocatori se si sceglie di nasconderli
    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().getWorld().getName().equalsIgnoreCase(HUB_WORLD_NAME)) return;

        event.getRecipients().removeIf(recipient -> playersHidden.contains(recipient.getUniqueId()));
    }
}
