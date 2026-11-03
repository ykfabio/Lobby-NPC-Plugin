package de.ykfabio.lobby;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class LobbyPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    private static final String MENU = ChatColor.DARK_GRAY + "Navigator";
    private final Set<UUID> hiddenPlayers = new HashSet<>();
    private YamlConfiguration settings;
    private YamlConfiguration messages;
    private YamlConfiguration permissions;
    private File settingsFile;
    private File messagesFile;
    private File permissionsFile;
    private boolean changingWeather;
    private File hologramsFile;
    private YamlConfiguration holograms;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCustomFiles();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
        Objects.requireNonNull(getCommand("lobby")).setExecutor(this::command);
        Objects.requireNonNull(getCommand("spawn")).setExecutor(this::command);
        Objects.requireNonNull(getCommand("setspawn")).setExecutor(this::command);
        Objects.requireNonNull(getCommand("setlobby")).setExecutor(this::command);
        Objects.requireNonNull(getCommand("holo")).setExecutor(this::holoCommand);
        loadHolograms();
        applyWorldSettings();
        getLogger().info("ykfabioLobby aktiviert.");
    }

    private void loadCustomFiles() {
        settingsFile = new File(getDataFolder(), "settings.yml");
        messagesFile = new File(getDataFolder(), "messages.yml");
        permissionsFile = new File(getDataFolder(), "permissions.yml");
        if (!settingsFile.exists()) saveResource("settings.yml", false);
        if (!messagesFile.exists()) saveResource("messages.yml", false);
        if (!permissionsFile.exists()) saveResource("permissions.yml", false);
        settings = YamlConfiguration.loadConfiguration(settingsFile);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        permissions = YamlConfiguration.loadConfiguration(permissionsFile);
    }

    private boolean command(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player player)) return tell(sender, "player-only");
            teleport(player, "locations.spawn");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("setspawn")) {
            if (!(sender instanceof Player player)) return tell(sender, "player-only");
            if (!allowed(player, "admin")) return noPerm(sender);
            saveLocation("locations.spawn", player.getLocation());
            return tell(sender, "spawn-set");
        }
        if (cmd.getName().equalsIgnoreCase("setlobby")) {
            if (!(sender instanceof Player player)) return tell(sender, "player-only");
            if (!allowed(player, "admin")) return noPerm(sender);
            saveLocation("locations.lobby", player.getLocation());
            return tell(sender, "lobby-set");
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) return tell(sender, "player-only");
            connect(player, getConfig().getString("network.lobby-server", "lobby"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> showHelp(sender);
            case "reload" -> {
                if (!allowed(sender, "reload")) return noPerm(sender);
                reloadConfig();
                loadCustomFiles();
                loadHolograms();
                applyWorldSettings();
                tell(sender, "reloaded");
            }
            case "spawn" -> {
                if (!(sender instanceof Player player)) return tell(sender, "player-only");
                teleport(player, "locations.spawn");
            }
            case "setspawn", "setlobby", "setvoid", "info" -> handleLobbyCommand(sender, sub);
            case "weather" -> handleWeather(sender, args);
            case "time" -> handleTime(sender, args);
            case "mobs" -> handleToggle(sender, args, "mobs", "world.mob-spawning", "mobs");
            case "pvp" -> handleToggle(sender, args, "pvp", "world.pvp", "pvp");
            case "damage" -> handleToggle(sender, args, "damage", "protection.no-damage", "damage");
            case "falldamage" -> handleFallDamage(sender, args);
            case "hunger" -> handleToggle(sender, args, "hunger", "protection.no-hunger", "hunger");
            case "fly" -> handleFly(sender, args);
            case "hearts" -> handleHearts(sender, args);
            case "level" -> handleLevel(sender, args);
            case "sethearts" -> handleDefaultHearts(sender, args);
            case "setlevel" -> handleDefaultLevel(sender, args);
            default -> showHelp(sender);
        }
        return true;
    }

    private void handleLobbyCommand(CommandSender sender, String sub) {
        if (sub.equals("info")) {
            if (!allowed(sender, "info")) { noPerm(sender); return; }
            World world = targetWorld(sender);
            sender.sendMessage(color("&3&l=== ykfabio Lobby ==="));
            sender.sendMessage(color("&7World: &f" + world.getName()));
            sender.sendMessage(color("&7Weather: &f" + weatherName(world)));
            sender.sendMessage(color("&7Time: &f" + world.getTime()));
            sender.sendMessage(color("&7Mob spawning: &f" + settings.getBoolean("world.mob-spawning", false)));
            sender.sendMessage(color("&7PvP: &f" + settings.getBoolean("world.pvp", false)));
            sender.sendMessage(color("&7Damage: &f" + !settings.getBoolean("protection.no-damage", true)));
            sender.sendMessage(color("&7Hunger: &f" + !settings.getBoolean("protection.no-hunger", true)));
            sender.sendMessage(color("&7Lobby fly: &f" + settings.getBoolean("features.lobby-fly", false)));
            return;
        }
        if (!allowed(sender, "locations")) { noPerm(sender); return; }
        if (!(sender instanceof Player player)) { tell(sender, "player-only"); return; }
        if (sub.equals("setspawn")) {
            saveLocation("locations.spawn", player.getLocation()); tell(sender, "spawn-set");
        } else if (sub.equals("setlobby")) {
            saveLocation("locations.lobby", player.getLocation()); tell(sender, "lobby-set");
        } else if (sub.equals("setvoid")) {
            settings.set("protection.void-y", player.getLocation().getY()); saveSettings(); tell(sender, "void-set");
        }
    }

    private void handleWeather(CommandSender sender, String[] args) {
        if (!allowed(sender, "weather")) { noPerm(sender); return; }
        if (args.length < 2) { tell(sender, "usage-weather"); return; }
        String type = args[1].toLowerCase();
        if (!List.of("clear", "rain", "thunder").contains(type)) { tell(sender, "usage-weather"); return; }
        changingWeather = true;
        try {
            for (World world : Bukkit.getWorlds()) {
                world.setStorm(!type.equals("clear"));
                world.setThundering(type.equals("thunder"));
                world.setWeatherDuration(20 * 60 * 60);
                world.setThunderDuration(20 * 60 * 60);
            }
        } finally { changingWeather = false; }
        settings.set("world.weather", type.toUpperCase()); saveSettings();
        tell(sender, "weather-set", "value", type);
    }

    private void handleTime(CommandSender sender, String[] args) {
        if (!allowed(sender, "time")) { noPerm(sender); return; }
        if (args.length < 2) { tell(sender, "usage-time"); return; }
        String value = args[1].toLowerCase();
        long ticks = switch (value) {
            case "day" -> 1000L;
            case "noon" -> 6000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            default -> -1L;
        };
        if (ticks < 0) { tell(sender, "usage-time"); return; }
        for (World world : Bukkit.getWorlds()) {
            world.setTime(ticks);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        }
        settings.set("world.time", value.toUpperCase()); saveSettings();
        tell(sender, "time-set", "value", value);
    }

    private void handleToggle(CommandSender sender, String[] args, String permissionKey, String settingPath, String kind) {
        if (!allowed(sender, permissionKey)) { noPerm(sender); return; }
        if (args.length < 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            tell(sender, "usage-toggle", "value", kind); return;
        }
        boolean enabled = args[1].equalsIgnoreCase("on");
        switch (kind) {
            case "mobs" -> {
                settings.set("world.mob-spawning", enabled);
                for (World world : Bukkit.getWorlds()) world.setGameRule(GameRule.DO_MOB_SPAWNING, enabled);
            }
            case "pvp" -> {
                settings.set("world.pvp", enabled);
                for (World world : Bukkit.getWorlds()) world.setPVP(enabled);
            }
            case "damage" -> settings.set(settingPath, !enabled);
            case "hunger" -> settings.set(settingPath, !enabled);
        }
        saveSettings();
        tell(sender, kind + "-set", "value", enabled ? "on" : "off");
    }

    private void handleFly(CommandSender sender, String[] args) {
        if (!allowed(sender, "fly")) { noPerm(sender); return; }
        if (args.length < 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            tell(sender, "usage-toggle", "value", "fly"); return;
        }
        boolean enabled = args[1].equalsIgnoreCase("on");
        settings.set("features.lobby-fly", enabled);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;
            player.setAllowFlight(enabled || settings.getBoolean("features.double-jump", true));
            if (!enabled && player.isFlying()) player.setFlying(false);
        }
        saveSettings();
        tell(sender, "fly-set", "value", enabled ? "on" : "off");
    }

    private void handleFallDamage(CommandSender sender, String[] args) {
        if (!allowed(sender, "falldamage")) { noPerm(sender); return; }
        if (args.length < 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            tell(sender, "usage-toggle", "value", "falldamage"); return;
        }
        boolean enabled = args[1].equalsIgnoreCase("on");
        settings.set("protection.fall-damage", enabled);
        saveSettings();
        tell(sender, "falldamage-set", "value", enabled ? "on" : "off");
    }

    private void handleHearts(CommandSender sender, String[] args) {
        if (!allowed(sender, "hearts")) { noPerm(sender); return; }
        if (args.length != 3) { tell(sender, "usage-hearts"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { tell(sender, "player-not-found"); return; }
        double hearts;
        try { hearts = Double.parseDouble(args[2].replace(',', '.')); }
        catch (NumberFormatException exception) { tell(sender, "usage-hearts"); return; }
        if (!Double.isFinite(hearts) || hearts < 1 || hearts > 512) { tell(sender, "hearts-range"); return; }
        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) { tell(sender, "hearts-failed"); return; }
        double healthPoints = hearts * 2.0;
        maxHealth.setBaseValue(healthPoints);
        target.setHealth(healthPoints);
        tell(sender, "hearts-set", "player", target.getName(), "value", String.valueOf(hearts));
        tell(target, "hearts-received", "value", String.valueOf(hearts));
    }

    private void handleLevel(CommandSender sender, String[] args) {
        if (!allowed(sender, "level")) { noPerm(sender); return; }
        if (args.length != 3) { tell(sender, "usage-level"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { tell(sender, "player-not-found"); return; }
        int level;
        try { level = Integer.parseInt(args[2]); }
        catch (NumberFormatException exception) { tell(sender, "usage-level"); return; }
        if (level < 0 || level > 21863) { tell(sender, "level-range"); return; }
        target.setLevel(level);
        target.setExp(0.0f);
        target.setTotalExperience(0);
        tell(sender, "level-set", "player", target.getName(), "value", String.valueOf(level));
        tell(target, "level-received", "value", String.valueOf(level));
    }

    private void handleDefaultHearts(CommandSender sender, String[] args) {
        if (!allowed(sender, "hearts")) { noPerm(sender); return; }
        if (args.length != 2) { sender.sendMessage(color("&cVerwendung: /lobby sethearts <anzahl>")); return; }
        try {
            double value = Double.parseDouble(args[1].replace(',', '.'));
            if (!Double.isFinite(value) || value < 1 || value > 512) throw new NumberFormatException();
            settings.set("players.default-hearts", value);
            saveSettings();
            for (Player online : Bukkit.getOnlinePlayers()) applyDefaultPlayerStats(online);
            sender.sendMessage(color("&aStandardherzen für alle Spieler: &e" + value));
        } catch (NumberFormatException ex) { sender.sendMessage(color("&cErlaubt sind 1 bis 512 Herzen.")); }
    }

    private void handleDefaultLevel(CommandSender sender, String[] args) {
        if (!allowed(sender, "level")) { noPerm(sender); return; }
        if (args.length != 2) { sender.sendMessage(color("&cVerwendung: /lobby setlevel <level>")); return; }
        try {
            int value = Integer.parseInt(args[1]);
            if (value < 0 || value > 21863) throw new NumberFormatException();
            settings.set("players.default-level", value);
            saveSettings();
            for (Player online : Bukkit.getOnlinePlayers()) applyDefaultPlayerStats(online);
            sender.sendMessage(color("&aStandardlevel für alle Spieler: &e" + value));
        } catch (NumberFormatException ex) { sender.sendMessage(color("&cErlaubt sind Level 0 bis 21863.")); }
    }

    private void applyDefaultPlayerStats(Player player) {
        if (settings.getBoolean("players.set-hearts-on-join", true)) {
            double hearts = settings.getDouble("players.default-hearts", 10.0);
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                double hp = Math.max(2.0, Math.min(1024.0, hearts * 2.0));
                maxHealth.setBaseValue(hp);
                player.setHealth(Math.min(hp, player.getHealth()));
                if (settings.getBoolean("players.heal-on-join", true)) player.setHealth(hp);
            }
        }
        if (settings.getBoolean("players.set-level-on-join", true)) {
            player.setLevel(settings.getInt("players.default-level", 0));
            player.setExp(0.0f);
            player.setTotalExperience(0);
        }
    }

    private boolean holoCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!allowed(sender, "hologram")) return noPerm(sender);
        if (args.length == 0) { sender.sendMessage(color("&e/holo create <name> <text> | edit <name> <zeile> <text> | addline <name> <text> | removeline <name> <zeile> | delete <name> | list | tp <name> | move <name> | reload")); return true; }
        String sub = args[0].toLowerCase();
        if (sub.equals("list")) {
            sender.sendMessage(color("&3&lHologramme: &f" + String.join(", ", holograms.getKeys(false)))); return true;
        }
        if (sub.equals("reload")) { loadHolograms(); sender.sendMessage(color("&aHologramme neu geladen.")); return true; }
        if (args.length < 2) { sender.sendMessage(color("&cName fehlt.")); return true; }
        String name = args[1].toLowerCase();
        if (sub.equals("create")) {
            if (!(sender instanceof Player player)) return tell(sender, "player-only");
            if (args.length < 3) { sender.sendMessage(color("&c/holo create <name> <text>")); return true; }
            if (holograms.contains("holograms." + name)) { sender.sendMessage(color("&cDieses Hologramm existiert bereits.")); return true; }
            List<String> lines = List.of(String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
            var section = holograms.createSection("holograms." + name);
            section.set("world", player.getWorld().getName()); section.set("x", player.getLocation().getX()); section.set("y", player.getLocation().getY()); section.set("z", player.getLocation().getZ()); section.set("lines", lines);
            saveHolograms(); spawnHologram(name); sender.sendMessage(color("&aHologramm &e" + name + " &aerstellt.")); return true;
        }
        if (sub.equals("delete")) { if (!holograms.contains("holograms." + name)) { sender.sendMessage(color("&cNicht gefunden.")); return true; } removeHologramEntities(name); holograms.set("holograms." + name, null); saveHolograms(); sender.sendMessage(color("&aHologramm gelöscht.")); return true; }
        if (sub.equals("tp") || sub.equals("move")) {
            if (!(sender instanceof Player player)) return tell(sender, "player-only");
            if (!holograms.contains("holograms." + name)) { sender.sendMessage(color("&cNicht gefunden.")); return true; }
            if (sub.equals("move")) { var l=player.getLocation(); holograms.set("holograms."+name+".world",l.getWorld().getName()); holograms.set("holograms."+name+".x",l.getX()); holograms.set("holograms."+name+".y",l.getY()); holograms.set("holograms."+name+".z",l.getZ()); saveHolograms(); spawnHologram(name); sender.sendMessage(color("&aHologramm verschoben.")); }
            else { Location l=holoLocation(name); if(l!=null) player.teleport(l); }
            return true;
        }
        if (sub.equals("edit") || sub.equals("addline") || sub.equals("removeline")) {
            if (!holograms.contains("holograms." + name)) { sender.sendMessage(color("&cNicht gefunden.")); return true; }
            List<String> lines = new java.util.ArrayList<>(holograms.getStringList("holograms." + name + ".lines"));
            try {
                if (sub.equals("edit")) { if(args.length<4) throw new Exception(); int index=Integer.parseInt(args[2])-1; if(index<0||index>=lines.size()) throw new Exception(); lines.set(index,String.join(" ",java.util.Arrays.copyOfRange(args,3,args.length))); }
                else if (sub.equals("addline")) { if(args.length<3) throw new Exception(); lines.add(String.join(" ",java.util.Arrays.copyOfRange(args,2,args.length))); }
                else { if(args.length<3) throw new Exception(); int index=Integer.parseInt(args[2])-1; if(index<0||index>=lines.size()) throw new Exception(); lines.remove(index); }
            } catch (Exception ex) { sender.sendMessage(color("&cZeile ungültig. Nutze /holo edit <name> <zeile> <text>, addline oder removeline.")); return true; }
            holograms.set("holograms." + name + ".lines", lines); saveHolograms(); spawnHologram(name); sender.sendMessage(color("&aHologramm aktualisiert.")); return true;
        }
        sender.sendMessage(color("&cUnbekannter Befehl. /holo für Hilfe.")); return true;
    }

    private void loadHolograms() {
        hologramsFile = new File(getDataFolder(), "holograms.yml");
        if (!hologramsFile.exists()) try { getDataFolder().mkdirs(); hologramsFile.createNewFile(); } catch (IOException e) { getLogger().warning("holograms.yml konnte nicht erstellt werden: " + e.getMessage()); }
        holograms = YamlConfiguration.loadConfiguration(hologramsFile);
        org.bukkit.configuration.ConfigurationSection section = holograms.getConfigurationSection("holograms");
        if (section != null) for (String name : section.getKeys(false)) spawnHologram(name);
    }

    private void saveHolograms() { try { holograms.save(hologramsFile); } catch (IOException e) { getLogger().warning("Hologramme konnten nicht gespeichert werden: " + e.getMessage()); } }
    private Location holoLocation(String name) { World w=Bukkit.getWorld(holograms.getString("holograms."+name+".world", "")); return w==null?null:new Location(w,holograms.getDouble("holograms."+name+".x"),holograms.getDouble("holograms."+name+".y"),holograms.getDouble("holograms."+name+".z")); }
    private void removeHologramEntities(String name) { for(World w:Bukkit.getWorlds()) for(Entity e:w.getEntities()) if(e instanceof ArmorStand a && name.equals(a.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(this,"holo"),org.bukkit.persistence.PersistentDataType.STRING))) a.remove(); }
    private void spawnHologram(String name) {
        removeHologramEntities(name); Location base=holoLocation(name); if(base==null) return;
        List<String> lines=holograms.getStringList("holograms."+name+".lines");
        for(int i=0;i<lines.size();i++) { final int lineIndex=i; Location at=base.clone().add(0,-0.27*i,0); String raw=lines.get(i).replace("{online}",String.valueOf(Bukkit.getOnlinePlayers().size())).replace("{max}",String.valueOf(Bukkit.getMaxPlayers())).replace("{name}",name);
            ArmorStand stand=(ArmorStand)base.getWorld().spawnEntity(at,EntityType.ARMOR_STAND); stand.setInvisible(true); stand.setMarker(true); stand.setGravity(false); stand.setCustomNameVisible(true); stand.setCustomName(color(raw)); stand.setPersistent(true); stand.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(this,"holo"),org.bukkit.persistence.PersistentDataType.STRING,name);
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(color("&3&l=== ykfabio Lobby Commands ==="));
        sender.sendMessage(color("&e/lobby &7- Zum Netzwerk-Lobbyserver"));
        sender.sendMessage(color("&e/lobby spawn &7- Zum lokalen Spawn"));
        sender.sendMessage(color("&e/lobby weather <clear|rain|thunder>"));
        sender.sendMessage(color("&e/lobby time <day|noon|night|midnight>"));
        sender.sendMessage(color("&e/lobby mobs <on|off> &7- Mob-Spawning"));
        sender.sendMessage(color("&e/lobby pvp <on|off>"));
        sender.sendMessage(color("&e/lobby damage <on|off>"));
        sender.sendMessage(color("&e/lobby falldamage <on|off>"));
        sender.sendMessage(color("&e/lobby hunger <on|off>"));
        sender.sendMessage(color("&e/lobby fly <on|off>"));
        sender.sendMessage(color("&e/lobby hearts <spieler> <herzen>"));
        sender.sendMessage(color("&e/lobby level <spieler> <level>"));
        sender.sendMessage(color("&e/lobby sethearts <anzahl> | setlevel <level>"));
        sender.sendMessage(color("&e/holo <create|edit|addline|removeline|delete|list|tp|move|reload>"));
        sender.sendMessage(color("&e/lobby setspawn | setlobby | setvoid | info | reload"));
        sender.sendMessage(color("&e/spawn &7- Zum lokalen Spawn"));
        sender.sendMessage(color("&e/setspawn &7- Spawn setzen"));
        sender.sendMessage(color("&e/setlobby &7- Lobby-Position setzen"));
    }

    private void applyWorldSettings() {
        boolean mobs = settings.getBoolean("world.mob-spawning", false);
        boolean pvp = settings.getBoolean("world.pvp", false);
        String weather = settings.getString("world.weather", "CLEAR").toUpperCase();
        String time = settings.getString("world.time", "DAY").toUpperCase();
        changingWeather = true;
        try {
            for (World world : Bukkit.getWorlds()) {
                world.setGameRule(GameRule.DO_MOB_SPAWNING, mobs);
                world.setGameRule(GameRule.KEEP_INVENTORY, settings.getBoolean("world.keep-inventory", true));
                world.setPVP(pvp);
                if (settings.getBoolean("world.lock-weather", true)) {
                    boolean rain = !weather.equals("CLEAR");
                    world.setStorm(rain);
                    world.setThundering(weather.equals("THUNDER"));
                }
                long ticks = switch (time) {
                case "NOON" -> 6000L;
                case "NIGHT" -> 13000L;
                case "MIDNIGHT" -> 18000L;
                default -> 1000L;
            };
                world.setTime(ticks);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            }
        } finally {
            changingWeather = false;
        }
    }

    private World targetWorld(CommandSender sender) {
        if (sender instanceof Player player) return player.getWorld();
        return Bukkit.getWorlds().get(0);
    }

    private String weatherName(World world) {
        if (world.isThundering()) return "thunder";
        if (world.hasStorm()) return "rain";
        return "clear";
    }

    private void saveLocation(String path, Location location) {
        getConfig().set(path + ".world", location.getWorld().getName());
        getConfig().set(path + ".x", location.getX());
        getConfig().set(path + ".y", location.getY());
        getConfig().set(path + ".z", location.getZ());
        getConfig().set(path + ".yaw", location.getYaw());
        getConfig().set(path + ".pitch", location.getPitch());
        saveConfig();
    }

    private void teleport(Player player, String path) {
        String worldName = getConfig().getString(path + ".world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) { tell(player, "spawn-missing"); return; }
        Location location = new Location(world, getConfig().getDouble(path + ".x"), getConfig().getDouble(path + ".y"),
                getConfig().getDouble(path + ".z"), (float) getConfig().getDouble(path + ".yaw"),
                (float) getConfig().getDouble(path + ".pitch"));
        player.teleport(location);
    }

    private void connect(Player player, String server) {
        if (server.equalsIgnoreCase("local")) { teleport(player, "locations.lobby"); return; }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect"); out.writeUTF(server);
            player.sendPluginMessage(this, "BungeeCord", bytes.toByteArray());
        } catch (IOException exception) { tell(player, "connect-failed"); }
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) { }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        applyDefaultPlayerStats(player);
        if (getConfig().getBoolean("join.clear-inventory", true)) player.getInventory().clear();
        giveLobbyItems(player);
        if (settings.getBoolean("features.lobby-fly", false) && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) player.setAllowFlight(true);
        else if (settings.getBoolean("features.double-jump", true) && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) player.setAllowFlight(true);
        for (UUID viewerId : new HashSet<>(hiddenPlayers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && !isStaff(player)) viewer.hidePlayer(this, player);
        }
        if (getConfig().getBoolean("join.teleport-to-lobby", true)) Bukkit.getScheduler().runTask(this, () -> teleport(player, "locations.lobby"));
    }

    private void giveLobbyItems(Player player) {
        if (!getConfig().getBoolean("join.give-items", true)) return;
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta compassMeta = compass.getItemMeta();
        compassMeta.setDisplayName(color("&b&lNavigator"));
        compassMeta.setLore(List.of(color("&7Rechtsklick zum Öffnen")));
        if (getConfig().getBoolean("navigator.glow", true)) compassMeta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        compass.setItemMeta(compassMeta);
        player.getInventory().setItem(getConfig().getInt("navigator.slot", 4), compass);

        ItemStack visibility = new ItemStack(Material.CLOCK);
        ItemMeta visibilityMeta = visibility.getItemMeta();
        visibilityMeta.setDisplayName(color("&e&lSpieler anzeigen/verstecken"));
        visibility.setItemMeta(visibilityMeta);
        player.getInventory().setItem(8, visibility);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || !event.getAction().isRightClick()) return;
        if (event.getItem().getType() == Material.COMPASS && getConfig().getBoolean("navigator.enabled", true)) {
            event.setCancelled(true); openMenu(event.getPlayer());
        } else if (event.getItem().getType() == Material.CLOCK) {
            event.setCancelled(true); toggleVisibility(event.getPlayer());
        }
    }

    private void toggleVisibility(Player player) {
        UUID uuid = player.getUniqueId();
        if (hiddenPlayers.remove(uuid)) {
            for (Player other : Bukkit.getOnlinePlayers()) if (!other.equals(player)) player.showPlayer(this, other);
            tell(player, "players-shown");
        } else {
            hiddenPlayers.add(uuid);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player)) continue;
                if (isStaff(other)) player.showPlayer(this, other); else player.hidePlayer(this, other);
            }
            tell(player, "players-hidden");
        }
    }

    private boolean isStaff(Player player) {
        List<String> staffPermissions = permissions.getStringList("staff-permissions");
        for (String permission : staffPermissions) if (rankHasPermission(player, permission)) return true;
        return player.isOp();
    }

    private void openMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, MENU);
        setMenuItem(inventory, 10, Material.COMPASS, "&b&lSpawn", "&7Zum lokalen Spawn teleportieren");
        setMenuItem(inventory, 13, Material.GRASS_BLOCK, "&a&lCityBuild", "&7Zum CityBuild-Netzwerkserver");
        setMenuItem(inventory, 16, Material.BARRIER, "&c&lComing Soon", "&7Demnächst verfügbar");
        player.openInventory(inventory);
    }

    private void setMenuItem(Inventory inventory, int slot, Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name)); meta.setLore(List.of(color(lore)));
        item.setItemMeta(meta); inventory.setItem(slot, item);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MENU)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) return;
        switch (event.getRawSlot()) {
            case 10 -> teleport(player, "locations.spawn");
            case 13 -> connect(player, getConfig().getString("network.citybuild-server", "citybuild"));
            case 16 -> tell(player, "coming-soon");
            default -> { }
        }
    }

    @EventHandler public void onDrop(PlayerDropItemEvent event) { if (settings.getBoolean("protection.anti-drop", true) && !bypass(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void onPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player && settings.getBoolean("protection.anti-pickup", true) && !bypass(player)) event.setCancelled(true); }
    @EventHandler public void onBreak(BlockBreakEvent event) { if (settings.getBoolean("protection.block-break", true) && !bypass(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent event) { if (settings.getBoolean("protection.block-place", true) && !bypass(event.getPlayer())) event.setCancelled(true); }
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && !settings.getBoolean("protection.fall-damage", false)) {
            event.setCancelled(true);
            return;
        }
        if (!bypass(player) && settings.getBoolean("protection.no-damage", true)) event.setCancelled(true);
    }
    @EventHandler public void onFood(FoodLevelChangeEvent event) { if (event.getEntity() instanceof Player player && settings.getBoolean("protection.no-hunger", true) && !bypass(player)) event.setCancelled(true); }

    @EventHandler
    public void onVoid(PlayerMoveEvent event) {
        if (!settings.getBoolean("protection.void-protection", true) || bypass(event.getPlayer()) || event.getTo() == null) return;
        if (event.getTo().getY() <= settings.getDouble("protection.void-y", -10)) teleport(event.getPlayer(), "locations.lobby");
    }

    @EventHandler
    public void onPvP(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (event.getEntity() instanceof Player && damager instanceof Player attacker && !settings.getBoolean("world.pvp", false) && !bypass(attacker)) event.setCancelled(true);
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent event) {
        if (settings.getBoolean("world.lock-weather", true) && !changingWeather) event.setCancelled(true);
    }

    @EventHandler
    public void onDoubleJump(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!settings.getBoolean("features.double-jump", true) || settings.getBoolean("features.lobby-fly", false)
                || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        Vector velocity = player.getLocation().getDirection().normalize().multiply(settings.getDouble("features.double-jump-forward", 0.7));
        velocity.setY(settings.getDouble("features.double-jump-power", 0.8));
        player.setVelocity(velocity);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (settings.getBoolean("features.double-jump", true) && !settings.getBoolean("features.lobby-fly", false)
                && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                && player.isOnGround() && !player.getAllowFlight()) player.setAllowFlight(true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player player = event.getPlayer();
            if (player.isOnline() && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                    && (settings.getBoolean("features.double-jump", true) || settings.getBoolean("features.lobby-fly", false))) player.setAllowFlight(true);
        }, 1L);
    }

    private boolean allowed(CommandSender sender, String key) {
        String permission = permissions.getString("commands." + key, "ykfabio.lobby." + key);
        if (sender instanceof Player player && rankHasPermission(player, permission)) return true;
        return sender.hasPermission(permission) || sender.hasPermission("ykfabio.lobby.admin") || sender.hasPermission("ranksystem.admin") || sender.isOp();
    }

    private boolean bypass(Player player) {
        if (!settings.getBoolean("protection.admin-bypass", true)) return false;
        return rankHasPermission(player, permissions.getString("bypass", "ykfabio.lobby.bypass"))
                || player.hasPermission("ykfabio.lobby.bypass") || player.hasPermission("ranksystem.admin") || player.isOp();
    }

    private boolean rankHasPermission(Player player, String permission) {
        try {
            for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                if (!plugin.getClass().getName().equals("de.ykfabio.ranksystem.paper.RankSystemPaper")) continue;
                java.lang.reflect.Method accessor = plugin.getClass().getDeclaredMethod("manager");
                accessor.setAccessible(true);
                Object manager = accessor.invoke(plugin);
                java.lang.reflect.Method check = manager.getClass().getMethod("hasPermission", UUID.class, String.class);
                return Boolean.TRUE.equals(check.invoke(manager, player.getUniqueId(), permission));
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            getLogger().warning("RankSystem permission check failed: " + exception.getMessage());
        }
        return player.hasPermission(permission);
    }

    private void saveSettings() {
        try { settings.save(settingsFile); }
        catch (IOException exception) { getLogger().severe("settings.yml konnte nicht gespeichert werden: " + exception.getMessage()); }
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    private boolean tell(CommandSender sender, String key, String... replacements) {
        String text = messages.getString("messages." + key, "&cMissing message: " + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) text = text.replace("{" + replacements[i] + "}", replacements[i + 1]);
        sender.sendMessage(color(messages.getString("prefix", "&3&lykfabio &8» ") + text));
        return true;
    }

    private boolean noPerm(CommandSender sender) { return tell(sender, "no-permission"); }
}
