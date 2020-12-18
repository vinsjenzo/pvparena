package net.slipcor.pvparena.loadables;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.commands.PAA_Region;
import net.slipcor.pvparena.commands.PAG_Join;
import net.slipcor.pvparena.core.Config;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Debug;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.listeners.PlayerListener;
import net.slipcor.pvparena.managers.ArenaManager;
import net.slipcor.pvparena.managers.SpawnManager;
import net.slipcor.pvparena.runnables.RegionRunnable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

import static java.util.Arrays.asList;

public class ArenaRegion {

    private static final Debug debug = new Debug(34);
    private final String world;
    private Arena arena;
    private String name;
    private RegionType type;
    private BukkitTask runningTask;
    private final Set<RegionFlag> flags = new HashSet<>();
    private final Set<RegionProtection> protections = new HashSet<>();
    private final Map<String, Location> playerLocations = new HashMap<>();

    private static final Set<Material> NOWOOLS = new HashSet<>();

    public final PABlockLocation[] locs;

    private final ArenaRegionShape shape;

    static {
        NOWOOLS.add(Material.CHEST);
    }

    /**
     * RegionType
     * <p/>
     * <pre>
     * CUSTOM => a module added region
     * WATCH  => the spectator region
     * LOUNGE => the ready lounge region
     * BATTLE => the battlefield region
     * JOIN   => the join region
     * SPAWN  => the spawn region
     * BL_INV => blacklist inventory
     * WL_INV => whitelist inventory
     * </pre>
     */
    public enum RegionType {
        CUSTOM, WATCH, LOUNGE, BATTLE, JOIN, SPAWN, BL_INV, WL_INV;

        public static RegionType guessFromName(final String regionName) {
            final String name = regionName.toUpperCase();
            for (final RegionType rt : values()) {
                if (name.endsWith(rt.name()) || name.startsWith(rt.name())) {
                    return rt;
                }
            }
            return CUSTOM;
        }
    }

    /**
     * RegionFlag for tick events
     * <p/>
     * <pre>
     * NOCAMP -   players not moving will be damaged
     * DEATH -    players being here will die
     * WIN -      players being here will win
     * LOSE -     players being here will lose
     * NODAMAGE - players being here will receive no damage
     * </pre>
     */
    public enum RegionFlag {
        NOCAMP, DEATH, WIN, LOSE, NODAMAGE
    }

    /**
     * RegionProtection
     * <p/>
     * <pre>
     * BREAK - Block break
     * FIRE - Fire
     * MOBS - Mob spawning
     * NATURE - Environment changes (leaves, shrooms, water, lava)
     * PAINTING - Painting placement/destruction
     * PISTON - Piston triggering
     * PLACE - Block placement
     * TNT - TNT usage
     * TNTBREAK - TNT block break
     * DROP - Player dropping items
     * INVENTORY - Player accessing inventory
     * PICKUP - Player picking up stuff
     * CRAFT - Player crafting stuff
     * TELEPORT - Player teleporting
     * </pre>
     */
    public enum RegionProtection {
        BREAK, FIRE, MOBS, NATURE, PAINTING, PISTON, PLACE, TNT, TNTBREAK, DROP, INVENTORY, PICKUP, CRAFT, TELEPORT
    }

    /**
     * region position for physical orientation
     * <p/>
     * <pre>
     * CENTER = in the battlefield center
     * NORTH = north end of the battlefield
     * EAST = east end of the battlefield
     * SOUTH = south end of the battlefield
     * WEST = west end of the battlefield
     * TOP = on top of the battlefield
     * BOTTOM = under the battlefield
     * INSIDE = inside the battlefield
     * OUTSIDE = around the battlefield
     * </pre>
     */
    public enum RegionPosition {
        CENTER, NORTH, EAST, SOUTH, WEST, TOP, BOTTOM, INSIDE, OUTSIDE
    }

    /**
     * check if an arena has overlapping battlefield region with another arena
     *
     * @param region1 the arena to check
     * @param region2 the arena to check
     * @return true if it does not overlap, false otherwise
     */
    public static boolean checkRegion(final Arena region1, final Arena region2) {

        final Set<ArenaRegion> ars1 = region1.getRegionsByType(RegionType.BATTLE);
        final Set<ArenaRegion> ars2 = region2.getRegionsByType(RegionType.BATTLE);

        if (ars1.size() < 0 || ars2.size() < 1) {
            return true;
        }

        for (final ArenaRegion ar1 : ars1) {
            for (final ArenaRegion ar2 : ars2) {
                if (ar1.shape.overlapsWith(ar2)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * check if other running arenas are interfering with this arena
     *
     * @return true if no running arena is interfering with this arena, false
     * otherwise
     */
    public static boolean checkRegions(final Arena arena) {
        if (!arena.getArenaConfig().getBoolean(CFG.USES_OVERLAPCHECK)) {
            return true;
        }
        arena.getDebugger().i("checking regions");

        return ArenaManager.checkRegions(arena);
    }

    /**
     * check if an admin tries to set an arena position
     *
     * @param event  the interact event to hand over
     * @param player the player interacting
     * @return true if the position is being saved, false otherwise
     */
    public static boolean checkRegionSetPosition(final PlayerInteractEvent event,
                                                 final Player player) {
        if (!PAA_Region.activeSelections.containsKey(player.getName())) {
            return false;
        }
        final Arena arena = PAA_Region.activeSelections.get(player.getName());
        if (arena != null
                && (PVPArena.hasAdminPerms(player) || PVPArena.hasCreatePerms(
                player, arena))
                && player.getEquipment().getItemInMainHand() != null
                && player.getEquipment().getItemInMainHand().getType().toString().equals(arena
                .getArenaConfig().getString(CFG.GENERAL_WAND))) {
            // - modify mode is active
            // - player has admin perms
            // - player has wand in hand
            arena.getDebugger().i("modify&adminperms&wand", player);
            final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                aPlayer.setSelection(event.getClickedBlock().getLocation(), false);
                arena.msg(player, Language.parse(arena, MSG.REGION_POS1));
                event.setCancelled(true); // no destruction in creative mode :)
                return true; // left click => pos1
            }

            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                aPlayer.setSelection(event.getClickedBlock().getLocation(), true);
                arena.msg(player, Language.parse(arena, MSG.REGION_POS2));
                return true; // right click => pos2
            }
        }
        return false;
    }

    public boolean containsRegion(ArenaRegion other) {
        List<PABlockLocation> checkList = other.getShape().getContainBlockCheckList();
        for (PABlockLocation block : checkList) {
            if (!this.getShape().contains(block)) {
                return false;
            }
        }

        // All points are inside
        // This will include all edge cases to account for:

        // CUBE - absolute maximum due to maximum X&Y&Z and minimum X&Y&Z
        // CYLINDER - absolute maximum due to maximum X&Y,Y&Z and minimum X&Y,Y&Z
        // SPHERE - current minimum with only minimum X, Y, Z and minimum X, Y, Z

        return true;
    }

    /**
     * Creates a new Arena Region
     *
     * @param arena the arena to bind to
     * @param name  the region name
     * @param shape the shape (to be cloned)
     * @param locs  the defining locations
     *              <p/>
     *              Does NOT save the region! use region.saveToConfig();
     */
    public ArenaRegion(final Arena arena, final String name,
                       final ArenaRegionShape shape, final PABlockLocation[] locs) {

        this.arena = arena;
        this.name = name;
        this.locs = locs;
        this.shape = shape.clone();
        this.type = RegionType.CUSTOM;
        this.world = locs[0].getWorldName();
        arena.addRegion(this);
        this.shape.initialize(this);
    }

    /**
     * is a player to far away to join?
     *
     * @param player the player to check
     * @return true if the player is too far away, false otherwise
     */
    public static boolean tooFarAway(final Arena arena, final Player player) {
        final int joinRange = arena.getArenaConfig().getInt(CFG.JOIN_RANGE);
        if (joinRange < 1) {
            return false;
        }
        final Set<ArenaRegion> ars = arena
                .getRegionsByType(RegionType.BATTLE);

        if (ars.size() < 1) {
            final PABlockLocation bLoc = SpawnManager.getRegionCenter(arena);
            if (!bLoc.getWorldName().equals(player.getWorld().getName())) {
                return true;
            }
            return bLoc.getDistanceSquared(
                    new PABlockLocation(player.getLocation())) > joinRange * joinRange;
        }

        for (final ArenaRegion ar : ars) {
            if (!ar.world.equals(player.getWorld().getName())) {
                return true;
            }
            if (!ar.shape.tooFarAway(joinRange, player.getLocation())) {
                return false;
            }
        }

        return true;
    }

    public void applyFlags(final int flags) {
        for (final RegionFlag rf : RegionFlag.values()) {
            if ((flags & (int) Math.pow(2, rf.ordinal())) != 0) {
                this.flags.add(rf);
            }
        }
    }

    public void applyProtections(final int protections) {
        for (final RegionProtection rp : RegionProtection.values()) {
            if ((protections & (int) Math.pow(2, rp.ordinal())) == 0) {
                this.protections.remove(rp);
            } else {
                this.protections.add(rp);
            }
        }
    }

    public void flagAdd(final RegionFlag regionFlag) {
        this.flags.add(regionFlag);
    }

    public boolean flagToggle(final RegionFlag regionFlag) {
        if (this.flags.contains(regionFlag)) {
            this.flags.remove(regionFlag);
        } else {
            this.flags.add(regionFlag);
        }
        return this.flags.contains(regionFlag);
    }

    public void flagRemove(final RegionFlag regionFlag) {
        this.flags.remove(regionFlag);
    }

    public Arena getArena() {
        return this.arena;
    }

    public Set<RegionFlag> getFlags() {
        return this.flags;
    }

    public Set<RegionProtection> getProtections() {
        return this.protections;
    }

    public String getRegionName() {
        return this.name;
    }

    public ArenaRegionShape getShape() {
        return this.shape;
    }

    public RegionType getType() {
        return this.type;
    }

    public World getWorld() {
        return Bukkit.getWorld(this.world);
    }

    public String getWorldName() {
        return this.world;
    }

    public void initTimer() {

        if (this.runningTask != null && !this.runningTask.isCancelled()) {
            if (!asList(RegionType.JOIN, RegionType.WATCH, RegionType.LOUNGE).contains(this.type)) {
                this.runningTask.cancel();
            }
        }

        final RegionRunnable regionRunner = new RegionRunnable(this);
        final int timer = this.arena.getArenaConfig().getInt(CFG.TIME_REGIONTIMER);
        this.runningTask = regionRunner.runTaskTimer(PVPArena.getInstance(), timer, timer);
    }

    public boolean isInNoWoolSet(final Block block) {
        return NOWOOLS.contains(block.getType());
    }

    public boolean isInRange(final int offset, final PABlockLocation loc) {
        if (!this.world.equals(loc.getWorldName())) {
            return false;
        }

        return offset * offset < this.shape.getCenter().getDistanceSquared(loc);
    }

    public void protectionAdd(final RegionProtection regionProtection) {
        if (regionProtection == null) {
            this.protectionSetAll(true);
            return;
        }
        this.protections.add(regionProtection);
    }

    public boolean protectionSetAll(final Boolean value) {
        for (final RegionProtection rp : RegionProtection.values()) {
            if (rp == null) {
                this.arena.msg(Bukkit.getConsoleSender(),
                        "&cWarning! RegionProtection is null!");
                return false;
            }
            if (value == null) {
                this.protectionToggle(rp);
            } else if (value) {
                this.protectionAdd(rp);
            } else {
                this.protectionRemove(rp);
            }
        }

        return true;
    }

    public boolean protectionToggle(final RegionProtection regionProtection) {
        if (regionProtection == null) {
            return this.protectionSetAll(null);
        }
        if (this.protections.contains(regionProtection)) {
            this.protections.remove(regionProtection);
        } else {
            this.protections.add(regionProtection);
        }
        return this.protections.contains(regionProtection);
    }

    public void protectionRemove(final RegionProtection regionProtection) {
        if (regionProtection == null) {
            this.protectionSetAll(false);
            return;
        }
        this.protections.remove(regionProtection);
    }

    public void reset() {
        this.removeEntities();
    }

    public void removeEntities() {
        if (this.getWorld() == null || this.getWorld().getEntities().isEmpty()) {
            return;
        }

        for (final Entity entity : this.getWorld().getEntities()) {
            if (entity instanceof Player || !this.shape.contains(new PABlockLocation(entity.getLocation()))) {
                continue;
            }

            if (entity instanceof Hanging) {
                continue;
            }

            if (entity.hasMetadata("NPC")) {
                continue;
            }

            if (this.arena.getArenaConfig().getStringList(CFG.GENERAL_REGIONCLEAREXCEPTIONS.getNode(), new ArrayList<String>()).contains(entity.getType().name())) {
                continue;
            }

            entity.remove();
        }
        if (this.type == RegionType.JOIN || this.type == RegionType.WATCH) {
            return;
        }

        if(this.runningTask != null && !this.runningTask.isCancelled()) {
            this.runningTask.cancel();
        }
    }

    public void saveToConfig() {
        this.arena.getArenaConfig().setManually("arenaregion." + this.name,
                Config.parseToString(this, this.flags, this.protections));
        this.arena.getArenaConfig().save();
    }

    public final void setArena(final Arena arena) {
        this.arena = arena;
    }

    public final void setName(final String name) {
        this.name = name;
    }

    public void setType(final RegionType type) {
        this.type = type;
    }

    public void tick() {
        for (final ArenaPlayer ap : this.arena.getEveryone()) {
            if (ap.get() == null || ap.isTeleporting()) {
                continue;
            }
            final PABlockLocation pLoc = new PABlockLocation(ap.get().getLocation());
            if (this.flags.contains(RegionFlag.DEATH) && this.shape.contains(pLoc)) {
                Arena.pmsg(ap.get(), Language.parse(this.arena, MSG.NOTICE_YOU_DEATH));
                for (final ArenaGoal goal : this.arena.getGoals()) {
                    if (goal.getName().endsWith("DeathMatch")) {
                        if (goal.lifeMap.containsKey(ap.getName())) {
                            final int lives = goal.lifeMap.get(ap.getName()) + 1;
                            goal.lifeMap.put(ap.getName(), lives);
                        } else if (goal.getLifeMap().containsKey(ap.getArenaTeam().getName())) {
                            final int lives = goal.lifeMap.get(ap.getArenaTeam().getName()) + 1;
                            goal.lifeMap.put(ap.getArenaTeam().getName(), lives);
                        }
                    }
                }
                ap.get().setLastDamageCause(
                        new EntityDamageEvent(ap.get(), DamageCause.CUSTOM,
                                1003.0));
                ap.get().damage(1000);
            }
            if (this.flags.contains(RegionFlag.WIN) && this.shape.contains(pLoc)) {
                for (final ArenaTeam team : this.arena.getTeams()) {
                    if (!this.arena.isFreeForAll()
                            && team.getTeamMembers().contains(ap)) {
                        // skip winning team
                        continue;
                    }
                    for (final ArenaPlayer ap2 : team.getTeamMembers()) {
                        if (this.arena.isFreeForAll()
                                && ap2.getName().equals(ap.getName())) {
                            continue;
                        }
                        if (ap2.getStatus() == Status.FIGHT) {
                            Bukkit.getWorld(this.world).strikeLightningEffect(
                                    ap2.get().getLocation());
                            final EntityDamageEvent event = new EntityDamageEvent(
                                    ap2.get(), DamageCause.LIGHTNING, 10.0);
                            PlayerListener.finallyKillPlayer(this.arena,
                                    ap2.get(), event);
                        }
                    }
                    return;
                }
            }
            if (this.flags.contains(RegionFlag.LOSE) && this.shape.contains(pLoc)) {
                if (this.arena.isFreeForAll()) {
                    if (ap.getStatus() == Status.FIGHT) {
                        Bukkit.getWorld(this.world).strikeLightningEffect(
                                ap.get().getLocation());
                        final EntityDamageEvent event = new EntityDamageEvent(
                                ap.get(), DamageCause.LIGHTNING, 10.0);
                        PlayerListener
                                .finallyKillPlayer(this.arena, ap.get(), event);
                    }
                } else {
                    for (final ArenaTeam team : this.arena.getTeams()) {
                        if (!team.getTeamMembers().contains(ap)) {
                            // skip winner
                            continue;
                        }
                        for (final ArenaPlayer ap2 : team.getTeamMembers()) {
                            if (ap2.getStatus() == Status.FIGHT) {
                                Bukkit.getWorld(this.world)
                                        .strikeLightningEffect(
                                                ap2.get().getLocation());
                                final EntityDamageEvent event = new EntityDamageEvent(
                                        ap2.get(), DamageCause.LIGHTNING,10.0);
                                PlayerListener.finallyKillPlayer(this.arena,
                                        ap2.get(), event);
                            }
                        }
                        return;
                    }
                }
            }
            if (this.flags.contains(RegionFlag.NOCAMP)) {
                if (this.shape.contains(pLoc)) {
                    final Location loc = this.playerLocations.get(ap.getName());
                    if (loc == null) {
                        Arena.pmsg(ap.get(),
                                Language.parse(this.arena, MSG.NOTICE_YOU_NOCAMP));
                    } else {
                        if (loc.distance(ap.get().getLocation()) < 3) {
                            ap.get().setLastDamageCause(
                                    new EntityDamageEvent(ap.get(),
                                            DamageCause.CUSTOM, this.arena.getArenaConfig().getInt(CFG.DAMAGE_SPAWNCAMP)));
                            ap.get().damage(
                                    this.arena.getArenaConfig().getInt(
                                            CFG.DAMAGE_SPAWNCAMP));
                        }
                    }
                    this.playerLocations.put(ap.getName(), ap.get()
                            .getLocation().getBlock().getLocation());
                } else {
                    this.playerLocations.remove(ap.getName());
                }
            }
            if (this.type == RegionType.BATTLE) {
                if (ap.getStatus() != Status.FIGHT) {
                    continue;
                }

                boolean found = false;

                for (final ArenaRegion region : this.arena.getRegionsByType(RegionType.BATTLE)) {
                    if (region.shape.contains(pLoc)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    debug.i("escape due to '!found' #1");
                    debug.i("location: "+pLoc.toString());
                    Arena.pmsg(ap.get(), Language.parse(this.arena, MSG.NOTICE_YOU_ESCAPED));
                    if (this.arena.getArenaConfig().getBoolean(
                            CFG.GENERAL_LEAVEDEATH)) {
                        ap.get().setLastDamageCause(
                                new EntityDamageEvent(ap.get(),
                                        DamageCause.CUSTOM, 1004.0));
                        // ap.get().setHealth(0);
                        ap.get().damage(1000);
                    } else {
                        this.arena.playerLeave(ap.get(), CFG.TP_EXIT, false, false, false);
                    }
                }
            } else if (this.type == RegionType.WATCH) {

                if (ap.getStatus() != Status.WATCH) {
                    continue;
                }
                Set<ArenaRegion> regions = this.arena.getRegionsByType(RegionType.WATCH);

                boolean found = false;

                for (ArenaRegion region : regions) {
                    if (region.getShape().contains(pLoc)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    debug.i("escape due to '!found' #2");
                    Arena.pmsg(ap.get(), Language.parse(this.arena, MSG.NOTICE_YOU_ESCAPED));
                    debug.i("location: "+pLoc.toString());
                    this.arena.playerLeave(ap.get(), CFG.TP_EXIT, false, false, false);
                }
            } else if (this.type == RegionType.LOUNGE) {
                if (ap.getStatus() != Status.READY
                        && ap.getStatus() != Status.LOUNGE) {
                    continue;
                }

                debug.i("LOUNGE TICK");
                Set<ArenaRegion> regions = this.arena.getRegionsByType(RegionType.LOUNGE);

                boolean found = false;

                for (ArenaRegion region : regions) {
                    if (region.getShape().contains(pLoc)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    debug.i("escape due to '!found' #3");
                    Arena.pmsg(ap.get(), Language.parse(this.arena, MSG.NOTICE_YOU_ESCAPED));
                    debug.i("location: "+pLoc.toString());
                    this.arena.playerLeave(ap.get(), CFG.TP_EXIT, false, false, false);
                }
            }
        }
        if (this.arena.getArenaConfig().getBoolean(CFG.JOIN_FORCE)
                && this.type == RegionType.JOIN && !this.arena.isFightInProgress()
                && !this.arena.isLocked()) {
            for (final Player p : Bukkit.getOnlinePlayers()) {
                final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(p.getName());
                if (aPlayer.getArena() != null) {
                    continue;
                }
                if (this.shape.contains(new PABlockLocation(p.getLocation()))) {
                    final PAG_Join cmd = new PAG_Join();
                    cmd.commit(this.arena, p,
                            new String[]{this.name.replace("-join", "")});
                }
            }
        }
    }

    public String update(final String key, final String value) {
        // usage: /pa {arenaname} region [regionname] radius [number]
        // usage: /pa {arenaname} region [regionname] height [number]
        // usage: /pa {arenaname} region [regionname] position [position]
        // usage: /pa {arenaname} region [regionname] flag [flag]
        // usage: /pa {arenaname} region [regionname] type [regiontype]

        if ("height".equalsIgnoreCase(key)) {
            final int height;
            try {
                height = Integer.parseInt(value);
            } catch (final Exception e) {
                return Language.parse(this.arena, MSG.ERROR_NOT_NUMERIC, value);
            }

            this.locs[0].setY(this.shape.getCenter().getY() - (height >> 1));
            this.locs[1].setY(this.locs[0].getY() + height);

            return Language.parse(this.arena, MSG.REGION_HEIGHT, value);
        }
        if (key.equalsIgnoreCase("radius")) {
            int radius;
            try {
                radius = Integer.parseInt(value);
            } catch (Exception e) {
                return Language.parse(this.arena, MSG.ERROR_NOT_NUMERIC, value);
            }

            final PABlockLocation loc = this.shape.getCenter();

            this.locs[0].setX(loc.getX() - radius);
            this.locs[0].setY(loc.getY() - radius);
            this.locs[0].setZ(loc.getZ() - radius);

            this.locs[1].setX(loc.getX() + radius);
            this.locs[1].setY(loc.getY() + radius);
            this.locs[1].setZ(loc.getZ() + radius);

            return Language.parse(this.arena, MSG.REGION_RADIUS, value);
        }
        if (key.equalsIgnoreCase("position")) {
            return null; // TODO insert function to align the arena based on a
            // position setting.
            // TODO see SETUP.creole
        }

        return Language.parse(this.arena, MSG.ERROR_ARGUMENT, key,
                "height | radius | position");
    }
}
