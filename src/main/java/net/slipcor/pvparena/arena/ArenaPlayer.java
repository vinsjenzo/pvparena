package net.slipcor.pvparena.arena;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.classes.PALocation;
import net.slipcor.pvparena.classes.PAStatMap;
import net.slipcor.pvparena.core.ColorUtils;
import net.slipcor.pvparena.core.Config;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.events.PAPlayerClassChangeEvent;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.managers.ArenaManager;
import net.slipcor.pvparena.managers.InventoryManager;
import net.slipcor.pvparena.managers.SpawnManager;
import net.slipcor.pvparena.managers.StatisticsManager.Type;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.util.*;

import static java.util.Optional.ofNullable;
import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Player class
 * </pre>
 * <p/>
 * contains Arena Player methods and variables for quicker access
 *
 * @author slipcor
 * @version v0.10.2
 */

public class ArenaPlayer {
    private static final Map<String, ArenaPlayer> totalPlayers = new HashMap<>();

    private final String name;
    private boolean telePass;
    private boolean ignoreAnnouncements;
    private boolean teleporting;
    private boolean mayDropInventory;

    private Boolean flying;

    private Arena arena;
    private ArenaClass aClass;
    private ArenaClass naClass;
    private PlayerState state;
    private PALocation location;
    private Status status = Status.NULL;

    private ItemStack[] savedInventory;
    private final Set<PermissionAttachment> tempPermissions = new HashSet<>();
    private final Map<String, PAStatMap> statistics = new HashMap<>();

    private Scoreboard backupBoard;
    private String backupBoardTeam;

    /**
     * Status
     *
     * <pre>
     * - NULL = not part of an arena
     * - WARM = not part of an arena, warmed up
     * - LOUNGE = inside an arena lobby mode
     * - READY = inside an arena lobby mode, readied up
     * - FIGHT = fighting inside an arena
     * - WATCH = watching a fight from the spectator area
     * - DEAD = dead and soon respawning
     * - LOST = lost and thus spectating
     * </pre>
     */
    public enum Status {
        NULL, WARM, LOUNGE, READY, FIGHT, WATCH, DEAD, LOST
    }


    /**
     * PlayerPrevention
     *
     * <pre>
     * BREAK - Block break
     * PLACE - Block placement
     * TNT - TNT usage
     * TNTBREAK - TNT block break
     * DROP - dropping items
     * INVENTORY - accessing inventory
     * PICKUP - picking up stuff
     * CRAFT - crafting stuff
     * </pre>
     */
    public enum PlayerPrevention {
        BREAK, PLACE, TNT, TNTBREAK, DROP, INVENTORY, PICKUP, CRAFT;

        public static boolean has(int value, PlayerPrevention s) {
            return (((int) Math.pow(2, s.ordinal()) & value) > 0);
        }
    }

    private boolean publicChatting = true;
    private final PABlockLocation[] selection = new PABlockLocation[2];

    private ArenaPlayer(final String playerName) {
        this.name = playerName;
    }

    public static Set<ArenaPlayer> getAllArenaPlayers() {
        return new HashSet<>(totalPlayers.values());
    }

    public boolean getFlyState() {
        return this.flying != null && this.flying;
    }

    /**
     * try to find the last damaging player
     *
     * @param eEvent the Event
     * @return the player instance if found, null otherwise
     */
    public static Player getLastDamagingPlayer(final Event eEvent, final Player damagee) {
        debug(damagee, "trying to get the last damaging player");
        if (eEvent instanceof EntityDamageByEntityEvent) {
            debug(damagee, "there was an EDBEE");
            final EntityDamageByEntityEvent event = (EntityDamageByEntityEvent) eEvent;

            Entity eDamager = event.getDamager();

            if (event.getCause() == DamageCause.PROJECTILE
                    && eDamager instanceof Projectile) {

                final ProjectileSource p = ((Projectile) eDamager).getShooter();

                if (p instanceof LivingEntity) {

                    eDamager = (LivingEntity) p;


                    debug(damagee, "killed by projectile, shooter is found");
                }
            }

            if (event.getEntity() instanceof Wolf) {
                final Wolf wolf = (Wolf) event.getEntity();
                if (wolf.getOwner() != null) {
                    eDamager = (Entity) wolf.getOwner();
                    debug(damagee, "tamed wolf is found");
                }
            }

            if (eDamager instanceof Player) {
                debug(damagee, "it was a player!");
                return (Player) eDamager;
            }
        }
        debug(damagee, "last damaging player is null");
        debug(damagee, "last damaging event: {}", ofNullable(eEvent).map(Event::getEventName).orElse("unknown cause"));
        return null;
    }

    /**
     * supply a player with class items and eventually wool head
     *
     * @param player the player to supply
     */
    public static void givePlayerFightItems(final Arena arena, final Player player) {
        final ArenaPlayer aPlayer = parsePlayer(player.getName());

        final ArenaClass playerClass = aPlayer.aClass;
        if (playerClass == null) {
            return;
        }
        debug(player, "giving items to player '{}', class '{}'", player, playerClass);

        playerClass.equip(player);

        if (arena.getArenaConfig().getBoolean(CFG.USES_WOOLHEAD)) {
            final ArenaTeam aTeam = aPlayer.getArenaTeam();
            final ChatColor color = aTeam.getColor();
            debug(player, "forcing woolhead: {}/{}", aTeam.getName(), color);
            player.getInventory().setHelmet(new ItemStack(ColorUtils.getWoolMaterialFromChatColor(color)));
        }
    }

    /**
     * get an ArenaPlayer from a player name
     *
     * @param name the playername to use
     * @return an ArenaPlayer instance belonging to that player
     */
    public static ArenaPlayer parsePlayer(final String name) {
        synchronized (ArenaPlayer.class) {
            Player player = Bukkit.getPlayerExact(name);

            // Offline player or NPC
            if (player == null) {
                return new ArenaPlayer(name);
            }

            if (!totalPlayers.containsKey(name)) {
                ArenaPlayer ap = new ArenaPlayer(player.getName());
                totalPlayers.putIfAbsent(name, ap);
            }
            return totalPlayers.get(name);
        }
    }

    /**
     * add an ArenaPlayer (used to load statistics)
     *
     * @param name the playername to use
     * @return an ArenaPlayer instance belonging to that player
     */
    public static ArenaPlayer addPlayer(final String name) {
        synchronized (ArenaPlayer.class) {
            ArenaPlayer aPlayer = new ArenaPlayer(name);
            totalPlayers.putIfAbsent(name, aPlayer);
            return totalPlayers.get(name);
        }
    }

    /**
     * prepare a player's inventory, back it up and clear it
     *
     * @param player the player to save
     */
    public static void backupAndClearInventory(final Arena arena, final Player player) {
        debug(player, "saving player inventory: {}", player);

        final ArenaPlayer aPlayer = parsePlayer(player.getName());
        aPlayer.savedInventory = player.getInventory().getContents().clone();
        InventoryManager.clearInventory(player);
    }

    public static void reloadInventory(final Arena arena, final Player player, final boolean instant) {

        if (player == null) {
            return;
        }

        debug(player, "resetting inventory");

        final ArenaPlayer aPlayer = parsePlayer(player.getName());

        if (arena.getArenaConfig().getYamlConfiguration().get(CFG.ITEMS_TAKEOUTOFGAME.getNode()) != null) {
            final ItemStack[] items =
                    arena.getArenaConfig().getItems(CFG.ITEMS_TAKEOUTOFGAME);

            final List<Material> allowedMats = new ArrayList<>();

            for (final ItemStack item : items) {
                allowedMats.add(item.getType());
            }

            final List<ItemStack> keepItems = new ArrayList<>();
            for (final ItemStack item : player.getInventory().getContents()) {
                if (item == null) {
                    continue;
                }
                if (allowedMats.contains(item.getType())) {
                    keepItems.add(item.clone());
                }
            }

            class GiveLater implements Runnable {

                @Override
                public void run() {
                    for (final ItemStack item : keepItems) {
                        player.getInventory().addItem(item.clone());
                    }
                    keepItems.clear();
                }

            }

            try {
                Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), new GiveLater(), 60L);
            } catch (final Exception e) {

            }
        }
        InventoryManager.clearInventory(player);

        if (aPlayer.savedInventory == null) {
            debug(player, "saved inventory null!");
            return;
        }
        // AIR AIR AIR AIR instead of contents !!!!

        if (instant) {

            debug(player, "adding saved inventory");
            player.getInventory().setContents(aPlayer.savedInventory);
        } else {
            class GiveLater implements Runnable {
                final ItemStack[] inv;

                GiveLater(final ItemStack[] inv) {
                    this.inv = inv.clone();
                }

                @Override
                public void run() {
                    debug(player, "adding saved inventory");
                    player.getInventory().setContents(this.inv);
                }
            }
            final GiveLater gl = new GiveLater(aPlayer.savedInventory);
            try {
                Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), gl, 60L);
            } catch (final Exception e) {
                gl.run();
            }
        }
    }

    public void addDeath() {
        this.getStatistics(this.arena).incStat(Type.DEATHS);
    }

    public void addKill() {
        this.getStatistics(this.arena).incStat(Type.KILLS);
    }

    public void addLosses() {
        this.getStatistics(this.arena).incStat(Type.LOSSES);
    }

    public void addStatistic(final String arenaName, final Type type,
                             final int value) {
        if (!this.statistics.containsKey(arenaName)) {
            this.statistics.put(arenaName, new PAStatMap());
        }

        this.statistics.get(arenaName).incStat(type, value);
    }

    public void addWins() {
        this.getStatistics(this.arena).incStat(Type.WINS);
    }

    private void clearDump() {
        debug(this.get(), "clearing dump of {}", this.name);
        this.debugPrint();
        final File file = new File(PVPArena.getInstance().getDataFolder().getPath()
                + "/dumps/" + this.name + ".yml");
        if (!file.exists()) {
            return;
        }
        file.delete();
    }

    public void clearFlyState() {
        this.flying = null;
    }

    /**
     * save the player state
     *
     * @param player the player to save
     */
    public void createState(final Player player) {
        this.state = new PlayerState(player);
        this.mayDropInventory = true;
    }

    public boolean didValidSelection() {
        return this.selection[0] != null && this.selection[1] != null;
    }

    public void debugPrint() {
        if (this.status == null || this.location == null) {
            debug(this.get(), "DEBUG PRINT OUT:");
            debug(this.get(), this.name);
            debug(this.get(), String.valueOf(this.status));
            debug(this.get(), String.valueOf(this.location));
            debug(this.get(), String.valueOf(this.selection[0]));
            debug(this.get(), String.valueOf(this.selection[1]));
            return;
        }
        debug(this.get(), "------------------");
        debug(this.get(), "Player: {}", this.name);
        debug(this.get(), "telepass: {} | mayDropInv: {} | chatting: {}", this.telePass, this.mayDropInventory, this.publicChatting);
        debug(this.get(), "arena: {}", (this.arena == null ? "null" : this.arena.getName()));
        debug(this.get(), "aClass: {}", (this.aClass == null ? "null" : this.aClass.getName()));
        debug(this.get(), "location: {}", this.location);
        debug(this.get(), "status: {}", this.status.name());
        debug(this.get(), "tempPermissions:");
        for (final PermissionAttachment pa : this.tempPermissions) {
            debug(this.get(), "> {}", pa);
        }
        debug(this.get(), "------------------");
    }

    public void dump() {
        debug(this.get(), "dumping...");
        this.debugPrint();
        final File file = new File(PVPArena.getInstance().getDataFolder().getPath()
                + "/dumps/" + this.name + ".yml");
        try {
            file.createNewFile();
        } catch (final Exception e) {
            e.printStackTrace();
            return;
        }

        final YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("arena", this.arena.getName());
        if (this.state != null) {
            this.state.dump(cfg);
        }

        try {
            cfg.set("inventory", this.savedInventory);
            cfg.set("loc", Config.parseToString(this.location));

            cfg.save(file);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * return the PVP Arena bukkit player
     *
     * @return the bukkit player instance
     */
    public Player get() {
        return Bukkit.getPlayerExact(this.name);
    }

    /**
     * return the arena
     *
     * @return the arena
     */
    public Arena getArena() {
        return this.arena;
    }

    /**
     * return the arena class
     *
     * @return the arena class
     */
    public ArenaClass getArenaClass() {
        return this.aClass;
    }

    public ArenaClass getNextArenaClass() {
        return this.naClass;
    }

    public ArenaTeam getArenaTeam() {
        if (this.arena == null) {
            return null;
        }
        for (final ArenaTeam team : this.arena.getTeams()) {
            if (team.getTeamMembers().contains(this)) {
                return team;
            }
        }
        return null;
    }

    public Scoreboard getBackupScoreboard() {
        return this.backupBoard;
    }

    public String getBackupScoreboardTeam() {
        return this.backupBoardTeam;
    }

    public PALocation getSavedLocation() {
        debug(this.get(), "reading loc!");
        if (this.location != null) {
            debug(this.get(), ": {}", this.location);
        }
        return this.location;
    }

    /**
     * return the player name
     *
     * @return the player name
     */
    public String getName() {
        return this.name;
    }

    public PABlockLocation[] getSelection() {
        return this.selection.clone();
    }

    /**
     * return the player state
     *
     * @return the player state
     */
    public PlayerState getState() {
        return this.state;
    }

    public PAStatMap getStatistics() {
        return this.getStatistics(this.arena);
    }

    public PAStatMap getStatistics(final Arena arena) {
        if (arena == null) {
            return new PAStatMap();
        }
        if (this.statistics.get(arena.getName()) == null) {
            this.statistics.put(arena.getName(), new PAStatMap());
        }
        return this.statistics.get(arena.getName());
    }

    public Status getStatus() {
        return this.status;
    }

    /**
     * hand over a player's tele pass
     *
     * @return true if may pass, false otherwise
     */
    public boolean isTelePass() {
        return this.hasTelePass();
    }

    public boolean isTeleporting() {
        return this.teleporting;
    }

    public boolean mayDropInventory() {
        return this.mayDropInventory;
    }

    public Set<PermissionAttachment> getTempPermissions() {
        return this.tempPermissions;
    }

    public int getTotalStatistics(final Type statType) {
        int sum = 0;

        for (final PAStatMap stat : this.statistics.values()) {
            sum += stat.getStat(statType);
        }

        return sum;
    }

    public boolean hasBackupScoreboard() {
        return this.backupBoard != null;
    }

    public boolean hasTelePass() {
        return this.telePass;
    }

    public boolean isIgnoringAnnouncements() {
        return this.ignoreAnnouncements;
    }

    public boolean isPublicChatting() {
        return this.publicChatting;
    }

    public boolean hasCustomClass() {
        return this.getArenaClass() != null && "custom".equalsIgnoreCase(this.getArenaClass().getName());
    }

    public void readDump() {
        debug(this.get(), "reading dump: {}", this.name);
        this.debugPrint();
        final File file = new File(PVPArena.getInstance().getDataFolder().getPath()
                + "/dumps/" + this.name + ".yml");
        if (!file.exists()) {
            debug(this.get(), "no dump!");
            return;
        }

        final YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(file);
        } catch (final Exception e) {
            e.printStackTrace();
            return;
        }

        this.arena = ArenaManager.getArenaByName(cfg.getString("arena"));
        this.savedInventory = cfg.getList("inventory").toArray(new ItemStack[0]);
                /*StringParser.getItemStacksFromString(cfg.getString(
                "inventory", "AIR"));*/
        this.location = Config.parseLocation(cfg.getString("loc"));

        if (this.arena != null) {
            final String goTo = this.arena.getArenaConfig().getString(CFG.TP_EXIT);
            if (!"old".equals(goTo)) {
                this.location = SpawnManager.getSpawnByExactName(this.arena, "exit");
            }

            if (Bukkit.getPlayer(this.name) == null) {
                debug(this.get(), "player offline, OUT!");
                return;
            }
            this.state = PlayerState.undump(cfg, this.name);
        }

        file.delete();
        this.debugPrint();
    }

    /**
     * save and reset a player instance
     */
    public void reset() {
        debug(this.get(), "destroying arena player {}", this.name);
        this.debugPrint();
        final YamlConfiguration cfg = new YamlConfiguration();
        try {
            if (PVPArena.getInstance().getConfig().getBoolean("stats")) {

                final String file = PVPArena.getInstance().getDataFolder()
                        + "/players.yml";
                cfg.load(file);

                if (this.arena != null) {
                    final String arenaName = this.arena.getName();
                    cfg.set(arenaName + '.' + this.name + ".losses", this.getStatistics()
                            .getStat(Type.LOSSES)
                            + this.getTotalStatistics(Type.LOSSES));
                    cfg.set(arenaName + '.' + this.name + ".wins",
                            this.getStatistics()
                                    .getStat(Type.WINS)
                                    + this.getTotalStatistics(Type.WINS));
                    cfg.set(arenaName + '.' + this.name + ".kills",
                            this.getStatistics().getStat(
                                    Type.KILLS)
                                    + this.getTotalStatistics(Type.KILLS));
                    cfg.set(arenaName + '.' + this.name + ".deaths", this.getStatistics()
                            .getStat(Type.DEATHS)
                            + this.getTotalStatistics(Type.DEATHS));
                    cfg.set(arenaName + '.' + this.name + ".damage", this.getStatistics()
                            .getStat(Type.DAMAGE)
                            + this.getTotalStatistics(Type.DAMAGE));
                    cfg.set(arenaName + '.' + this.name + ".maxdamage",
                            this.getStatistics().getStat(
                                    Type.MAXDAMAGE)
                                    + this.getTotalStatistics(Type.MAXDAMAGE));
                    cfg.set(arenaName + '.' + this.name + ".damagetake",
                            this.getStatistics().getStat(
                                    Type.DAMAGETAKE)
                                    + this.getTotalStatistics(Type.DAMAGETAKE));
                    cfg.set(arenaName + '.' + this.name + ".maxdamagetake",
                            this.getStatistics().getStat(
                                    Type.MAXDAMAGETAKE)
                                    + this.getTotalStatistics(Type.MAXDAMAGETAKE));
                }

                cfg.save(file);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

        if (this.get() == null) {
            debug(this.get(), "reset() ; out! null");
            return;
        }

        this.telePass = false;

        if (this.state != null) {
            this.state.reset();
            this.state = null;
        }
        // location = null;

        this.setStatus(Status.NULL);
        this.naClass = null;

        if (this.arena != null) {
            final ArenaTeam team = this.getArenaTeam();
            if (team != null) {
                team.remove(this);
            }
        }
        this.arena = null;
        this.aClass = null;
        this.get().setFireTicks(0);
        try {
            Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), new Runnable() {
                @Override
                public void run() {
                    if (ArenaPlayer.this.get() != null && ArenaPlayer.this.get().getFireTicks() > 0) {
                        ArenaPlayer.this.get().setFireTicks(0);
                    }
                }
            }, 5L);
        } catch (Exception e) {
        }

        this.clearDump();
    }

    /**
     * set the player's arena
     *
     * @param arena the arena to set
     */
    public final void setArena(final Arena arena) {
        this.arena = arena;
    }

    /**
     * set the player's arena class
     *
     * @param aClass the arena class to set
     */
    public void setArenaClass(final ArenaClass aClass) {
        final PAPlayerClassChangeEvent event = new PAPlayerClassChangeEvent(this.arena, this.get(), aClass);
        Bukkit.getServer().getPluginManager().callEvent(event);
        this.aClass = event.getArenaClass();
        if (this.arena != null && this.getStatus() != Status.NULL) {
            ArenaModuleManager.parseClassChange(this.arena, this.get(), this.aClass);
        }
    }

    /**
     * set a player's arena class by name
     *
     * @param className an arena class name
     */
    public void setArenaClass(final String className) {

        for (final ArenaClass ac : this.arena.getClasses()) {
            if (ac.getName().equalsIgnoreCase(className)) {
                this.setArenaClass(ac);
                return;
            }
        }
        PVPArena.getInstance().getLogger().warning(
                "[PA-debug] failed to set unknown class " + className + " to player "
                        + this.name);
    }

    public void setBackupScoreboard(Scoreboard board) {
        this.backupBoard = board;
    }

    public void setBackupScoreboardTeam(String sbTeamName) {
        this.backupBoardTeam = sbTeamName;
    }

    public void setMayDropInventory(boolean value) {
        this.mayDropInventory = value;
    }

    public void setNextArenaClass(ArenaClass aClass) {
        this.naClass = aClass;
    }

    public void setFlyState(boolean flyState) {
        this.flying = flyState;
    }

    public void setIgnoreAnnouncements(final boolean value) {
        this.ignoreAnnouncements = value;
    }

    public void setLocation(final PALocation location) {
        this.location = location;
    }

    public void setPublicChatting(final boolean chatPublic) {
        this.publicChatting = chatPublic;
    }

    public void setSelection(final Location loc, final boolean second) {
        if (second) {
            this.selection[1] = new PABlockLocation(loc);
        } else {
            this.selection[0] = new PABlockLocation(loc);
        }
    }

    public void setStatistic(final String arenaName, final Type type,
                             final int value) {
        if (!this.statistics.containsKey(arenaName)) {
            this.statistics.put(arenaName, new PAStatMap());
        }

        final PAStatMap map = this.statistics.get(arenaName);
        map.setStat(type, value);
    }

    public void setStatus(final Status status) {
        debug(this.get(),"{}>{}", this.name, status.name());
        this.status = status;
    }

    /**
     * hand over a player's tele pass
     *
     * @param canTeleport true if may pass, false otherwise
     */
    public void setTelePass(final boolean canTeleport) {
        if (this.arena != null) {
            debug(this.arena, "TelePass := {}", canTeleport);
        }
        this.telePass = canTeleport;
    }

    public void setTeleporting(final boolean isTeleporting) {
        this.teleporting = isTeleporting;
    }

    public void showBloodParticles() {
        Player player = this.get();
        player.getLocation()
                .getWorld()
                .playEffect(player.getEyeLocation(), Effect.STEP_SOUND, Material.NETHER_WART_BLOCK);

    }

    @Override
    public String toString() {
        final ArenaTeam team = this.getArenaTeam();

        return team == null ? this.name : team.getColorCodeString() + this.name + ChatColor.RESET;
    }

    public void unsetSelection() {
        this.selection[0] = null;
        this.selection[1] = null;
    }
}
