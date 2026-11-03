package de.ykfabio.npc;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

import java.io.*;
import java.util.*;

/**
 * Citizens-free NPC implementation. The NPC is a persistent armor-stand statue
 * with the requested player's real Minecraft skin on its head. This avoids NMS
 * and external NPC plugins, while remaining compatible with the Paper API.
 */
public final class NPCPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey npcIdKey;
    private NamespacedKey hologramKey;

    @Override public void onEnable() {
        saveDefaultConfig();
        npcIdKey = new NamespacedKey(this, "npc_id");
        hologramKey = new NamespacedKey(this, "hologram");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        Objects.requireNonNull(getCommand("npc")).setExecutor(this);
        Bukkit.getScheduler().runTaskLater(this, this::restoreNPCs, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::updateHolograms, 100L, 100L);
        getLogger().info("ykfabioNPC aktiviert – Citizens wird nicht benötigt.");
    }

    private void restoreNPCs() {
        var section = getConfig().getConfigurationSection("npcs");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            UUID entityId;
            try { entityId = UUID.fromString(getConfig().getString("npcs." + id + ".entity", "")); }
            catch (IllegalArgumentException ex) { entityId = null; }
            Entity existing = entityId == null ? null : Bukkit.getEntity(entityId);
            if (existing instanceof ArmorStand stand && stand.isValid()) {
                stand.getPersistentDataContainer().set(npcIdKey, PersistentDataType.STRING, id);
                applySkin(stand, getConfig().getString("npcs." + id + ".skin", "Steve"));
                createHologram(id, stand.getLocation());
            } else {
                Location location = readLocation("npcs." + id + ".location");
                if (location != null) spawnNPC(id, location);
            }
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isAdmin(sender) && !sender.hasPermission("ykfabio.npc.admin")) {
            sender.sendMessage("§cKeine Berechtigung."); return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§b/npc create <name> [skin] §7| §bremove <id> §7| §blist §7| §btp <id> §7| §baction <id> <message|command|server> §7| §bset <id> <skin|message|command|server> <wert> §7| §breload");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    sender.sendMessage("§c/npc create <name> [skin]"); return true;
                }
                String id = nextId();
                String name = args[1];
                String skin = args.length >= 3 ? args[2] : name;
                String path = "npcs." + id;
                getConfig().set(path + ".name", name);
                getConfig().set(path + ".skin", skin);
                getConfig().set(path + ".action", "message");
                getConfig().set(path + ".message", "&eWillkommen im ykfabio Network!");
                writeLocation(path + ".location", player.getLocation());
                spawnNPC(id, player.getLocation());
                saveConfig();
                player.sendMessage("§aNPC erstellt. ID: §f" + id + " §7(Skin: " + skin + ")");
            }
            case "remove" -> {
                if (args.length < 2) { sender.sendMessage("§c/npc remove <id>"); return true; }
                String id = args[1];
                removeNPC(id);
                getConfig().set("npcs." + id, null); saveConfig();
                sender.sendMessage("§aNPC " + id + " entfernt.");
            }
            case "list" -> {
                var section = getConfig().getConfigurationSection("npcs");
                if (section == null || section.getKeys(false).isEmpty()) { sender.sendMessage("§7Keine NPCs gespeichert."); return true; }
                for (String id : section.getKeys(false)) sender.sendMessage("§b" + id + " §7- §f" + getConfig().getString("npcs." + id + ".name") + " §8(Skin: " + getConfig().getString("npcs." + id + ".skin") + ")");
            }
            case "tp" -> {
                if (!(sender instanceof Player player) || args.length < 2) return true;
                Entity entity = getNPCEntity(args[1]);
                if (entity != null) player.teleport(entity.getLocation()); else player.sendMessage("§cNPC nicht gefunden.");
            }
            case "action" -> {
                if (args.length < 3) { sender.sendMessage("§c/npc action <id> <message|command|server>"); return true; }
                String action = args[2].toLowerCase(Locale.ROOT);
                if (!Set.of("message", "command", "server").contains(action)) { sender.sendMessage("§cUngültige Aktion."); return true; }
                getConfig().set("npcs." + args[1] + ".action", action); saveConfig(); sender.sendMessage("§aAktion gespeichert.");
            }
            case "set" -> {
                if (args.length < 4) { sender.sendMessage("§c/npc set <id> <skin|message|command|server> <wert>"); return true; }
                String id = args[1], key = args[2].toLowerCase(Locale.ROOT);
                if (!Set.of("skin", "message", "command", "server").contains(key)) { sender.sendMessage("§cUngültiger Schlüssel."); return true; }
                String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                getConfig().set("npcs." + id + "." + key, value); saveConfig();
                if (key.equals("skin")) { Entity entity = getNPCEntity(id); if (entity instanceof ArmorStand stand) applySkin(stand, value); }
                sender.sendMessage("§aEinstellung gespeichert.");
            }
            case "reload" -> { reloadConfig(); restoreNPCs(); sender.sendMessage("§aConfig neu geladen."); }
            default -> sender.sendMessage("§cUnbekannter Unterbefehl. /npc");
        }
        return true;
    }

    private String nextId() {
        var section = getConfig().getConfigurationSection("npcs");
        int id = 1;
        if (section != null) while (section.contains(String.valueOf(id))) id++;
        return String.valueOf(id);
    }

    private void spawnNPC(String id, Location location) {
        removeNPC(id);
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setCustomName(color(getConfig().getString("npcs." + id + ".name", "NPC")));
        stand.setCustomNameVisible(true);
        stand.setArms(true); stand.setBasePlate(false); stand.setGravity(false);
        stand.setInvulnerable(true); stand.setPersistent(true); stand.setCanPickupItems(false);
        stand.getPersistentDataContainer().set(npcIdKey, PersistentDataType.STRING, id);
        getConfig().set("npcs." + id + ".entity", stand.getUniqueId().toString());
        applySkin(stand, getConfig().getString("npcs." + id + ".skin", "Steve"));
        createHologram(id, location);
        saveConfig();
    }

    private void applySkin(ArmorStand stand, String skinName) {
        if (skinName == null || skinName.isBlank()) return;
        // Paper resolves the Mojang profile and its skin texture asynchronously.
        PlayerProfile profile = Bukkit.createPlayerProfile(skinName);
        profile.update().thenAccept(updated -> Bukkit.getScheduler().runTask(this, () -> {
            if (!stand.isValid()) return;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwnerProfile(updated);
            head.setItemMeta(meta);
            stand.getEquipment().setHelmet(head);
        })).exceptionally(error -> {
            getLogger().warning("Skin für '" + skinName + "' konnte nicht geladen werden: " + error.getMessage());
            return null;
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractAtEntityEvent event) {
        String id = event.getRightClicked().getPersistentDataContainer().get(npcIdKey, PersistentDataType.STRING);
        if (id == null) return;
        event.setCancelled(true);
        runClickAction(id, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        String id = event.getEntity().getPersistentDataContainer().get(npcIdKey, PersistentDataType.STRING);
        if (id == null) return;
        event.setCancelled(true);
        runClickAction(id, player);
    }

    private void runClickAction(String id, Player player) {
        String path = "npcs." + id + ".";
        switch (getConfig().getString(path + "action", "message").toLowerCase(Locale.ROOT)) {
            case "command" -> {
                String cmd = getConfig().getString(path + "command", "").replace("%player%", player.getName());
                if (!cmd.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
            case "server" -> connect(player, getConfig().getString(path + "server", "lobby"));
            default -> player.sendMessage(color(getConfig().getString(path + "message", "&eWillkommen im ykfabio Network!")));
        }
    }

    private void createHologram(String id, Location location) {
        removeHologram(id);
        List<String> lines = new ArrayList<>();
        if (getConfig().getBoolean("holograms.show-player-count", true)) lines.add("&bOnline: &f%online%/&f%max%");
        if (getConfig().getBoolean("holograms.show-status", true)) lines.add("&a● &fOnline");
        lines.addAll(getConfig().getStringList("npcs." + id + ".hologram"));
        int index = 0;
        for (String line : lines) {
            Location at = location.clone().add(0, 2.35 - (index++ * 0.25), 0);
            ArmorStand stand = (ArmorStand) at.getWorld().spawnEntity(at, EntityType.ARMOR_STAND);
            stand.setInvisible(true); stand.setMarker(true); stand.setGravity(false);
            stand.setCustomNameVisible(true); stand.setInvulnerable(true); stand.setPersistent(true);
            stand.setCustomName(color(line.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size())).replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))));
            stand.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, id);
        }
    }

    private void updateHolograms() {
        for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            if (stand.getPersistentDataContainer().has(hologramKey, PersistentDataType.STRING)) {
                String name = stand.getCustomName();
                if (name != null && name.contains("Online:")) stand.setCustomName(color("&bOnline: &f" + Bukkit.getOnlinePlayers().size() + "/&f" + Bukkit.getMaxPlayers()));
            }
        }
    }

    private void removeHologram(String id) {
        for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(hologramKey, PersistentDataType.STRING)
                    && id.equals(entity.getPersistentDataContainer().get(hologramKey, PersistentDataType.STRING))) entity.remove();
        }
    }

    private void removeNPC(String id) {
        Entity entity = getNPCEntity(id);
        if (entity != null) entity.remove();
        removeHologram(id);
    }

    private Entity getNPCEntity(String id) {
        String raw = getConfig().getString("npcs." + id + ".entity", "");
        try { return Bukkit.getEntity(UUID.fromString(raw)); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private void connect(Player player, String server) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect"); out.writeUTF(server);
            player.sendPluginMessage(this, "BungeeCord", bytes.toByteArray());
        } catch (IOException ex) { player.sendMessage("§cServerwechsel fehlgeschlagen."); }
    }

    private void writeLocation(String path, Location location) {
        getConfig().set(path + ".world", location.getWorld().getName());
        getConfig().set(path + ".x", location.getX()); getConfig().set(path + ".y", location.getY()); getConfig().set(path + ".z", location.getZ());
        getConfig().set(path + ".yaw", location.getYaw()); getConfig().set(path + ".pitch", location.getPitch());
    }

    private Location readLocation(String path) {
        World world = Bukkit.getWorld(getConfig().getString(path + ".world", ""));
        if (world == null) return null;
        return new Location(world, getConfig().getDouble(path + ".x"), getConfig().getDouble(path + ".y"), getConfig().getDouble(path + ".z"),
                (float) getConfig().getDouble(path + ".yaw"), (float) getConfig().getDouble(path + ".pitch"));
    }

    private boolean isAdmin(CommandSender sender) {
        if (sender instanceof Player player) {
            try {
                for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                    if (!plugin.getClass().getName().equals("de.ykfabio.ranksystem.paper.RankSystemPaper")) continue;
                    var accessor = plugin.getClass().getDeclaredMethod("manager"); accessor.setAccessible(true);
                    Object manager = accessor.invoke(plugin);
                    var check = manager.getClass().getMethod("hasPermission", UUID.class, String.class);
                    if (Boolean.TRUE.equals(check.invoke(manager, player.getUniqueId(), "ranksystem.admin"))) return true;
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                getLogger().warning("RankSystem-API konnte nicht abgefragt werden: " + ex.getMessage());
            }
        }
        return sender.hasPermission("ranksystem.admin") || sender.isOp();
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
    @Override public void onDisable() { /* NPCs and holograms remain persistent and are restored on next start. */ }
}
