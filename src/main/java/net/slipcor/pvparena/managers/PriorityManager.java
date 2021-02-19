package net.slipcor.pvparena.managers;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.arena.PlayerState;
import net.slipcor.pvparena.classes.PACheck;
import net.slipcor.pvparena.commands.PAA_Region;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.events.PAJoinEvent;
import net.slipcor.pvparena.events.PAStartEvent;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModule;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.loadables.ArenaRegion;
import net.slipcor.pvparena.loadables.ArenaRegion.RegionFlag;
import net.slipcor.pvparena.loadables.ArenaRegion.RegionType;
import net.slipcor.pvparena.runnables.InventoryRefillRunnable;
import net.slipcor.pvparena.runnables.PVPActivateRunnable;
import net.slipcor.pvparena.runnables.SpawnCampRunnable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * PVP Arena Check class
 * </pre>
 * <p/>
 * This class parses a complex check.
 * <p/>
 * It is called staticly to iterate over all needed/possible modules to return
 * one committing module (inside the result) and to make modules listen to the
 * checked events if necessary
 *
 * @author slipcor
 * @version v0.10.2
 */

public class PriorityManager {

    public static boolean handleCommand(final Arena arena, final CommandSender sender, final String[] args) {
        ArenaGoal goal = arena.getGoal();
        if(goal.checkCommand(args[0])) {
            goal.commitCommand(sender, args);
            return true;
        }

        for (final ArenaModule am : arena.getMods()) {
            if (am.checkCommand(args[0].toLowerCase())) {
                am.commitCommand(sender, args);
                return true;
            }
        }
        return false;
    }

    public static boolean handleEnd(final Arena arena, final boolean force) {
        debug(arena, "handleEnd: " + arena.getName() + "; force: " + force);

        ArenaGoal commit = arena.getGoal();
        PACheck res = commit.checkEnd(new PACheck());
        if (res.getPriority() <= 0) {
            // fail
            commit = null;
        }

        if (res.hasError()) {
            if (res.getError() != null && res.getError().length() > 1) {
                arena.msg(Bukkit.getConsoleSender(),
                        Language.parse(arena, MSG.ERROR_ERROR, res.getError()));
            }
            if (commit != null) {
                debug(arena, "error; committing end: " + commit.getName());
                commit.commitEnd(force);
                return true;
            }
            debug(arena, "error; FALSE!");
            return false;
        }

        if (commit == null) {
            debug(arena, "FALSE");
            return false;
        }

        debug(arena, "committing end: " + commit.getName());
        commit.commitEnd(force);
        return true;
    }

    public static int handleGetLives(final Arena arena, final ArenaPlayer aPlayer) {

        if (aPlayer.getStatus() == Status.LOUNGE || aPlayer.getStatus() == Status.WATCH) {
            return 0;
        }

        return arena.getGoal().getLives(aPlayer);
    }

    public static void handleInteract(final Arena arena, final Player player,
                                      final Cancellable event, final Block clickedBlock) {

        ArenaGoal commit = arena.getGoal();
        PACheck res = commit.checkInteract(new PACheck(), player, clickedBlock);
        if (res.getPriority() <= 0) {
            // fail
            commit = null;
        }

        if (res.hasError()) {
            arena.msg(Bukkit.getConsoleSender(),
                    Language.parse(arena, MSG.ERROR_ERROR, res.getError()));
            return;
        }

        if (commit == null) {
            return;
        }

        event.setCancelled(true);

        commit.commitInteract(player, clickedBlock);
    }

    public static boolean handleJoin(final Arena arena,
                                  final CommandSender sender, final String[] args) {
        debug(arena, "handleJoin!");
        int priority = 0;
        PACheck res = new PACheck();

        ArenaModule commModule = null;

        for (final ArenaModule mod : arena.getMods()) {
            res = mod.checkJoin(sender, res, true);
            if (res.getPriority() > priority && priority >= 0) {
                // success and higher priority
                debug(arena, "higher priority, commModule := "+mod.getName());
                priority = res.getPriority();
                commModule = mod;
            } else if (res.getPriority() < 0 || priority < 0) {
                // fail
                priority = res.getPriority();
                commModule = null;
            }
        }

        if (commModule != null
                && !ArenaManager.checkJoin((Player) sender, arena)) {
            res.setError(commModule,
                    Language.parse(arena, MSG.ERROR_JOIN_REGION));
        }

        if (res.hasError() && !"LateLounge".equals(res.getModName())) {
            arena.msg(sender,
                    Language.parse(arena, MSG.ERROR_ERROR, res.getError()));
            return false;
        }

        if (res.hasError()) {
            arena.msg(sender,
                    Language.parse(arena, MSG.NOTICE_NOTICE, res.getError()));
            return false;
        }

        ArenaGoal commGoal = arena.getGoal();
        res = commGoal.checkJoin(sender, res, args);
        if (res.getPriority() <= 0) {
            // fail
            commGoal = null;
        }

        if (commGoal != null && !ArenaManager.checkJoin((Player) sender, arena)) {
            res.setError(commGoal, Language.parse(arena, MSG.ERROR_JOIN_REGION));
        }

        if (res.hasError()) {
            arena.msg(sender,
                    Language.parse(arena, MSG.ERROR_ERROR, res.getError()));
            return false;
        }

        final ArenaTeam team;

        if (args.length < 1) {
            // usage: /pa {arenaname} join | join an arena

            team = arena.getTeam(TeamManager.calcFreeTeam(arena));
        } else if(arena.getTeam(args[0]) == null) {
            arena.msg(sender, Language.parse(arena, MSG.ERROR_TEAMNOTFOUND, args[0]));
            return false;
        } else {
            ArenaTeam aTeam = arena.getTeam(args[0]);

            int maxPlayers = arena.getArenaConfig().getInt(CFG.READY_MAXPLAYERS);
            int maxTeamPlayers = arena.getArenaConfig().getInt(CFG.READY_MAXTEAMPLAYERS);

            if (maxPlayers > 0 && arena.getFighters().size() > maxPlayers) {
                arena.msg(sender, Language.parse(arena, MSG.ERROR_JOIN_ARENA_FULL));
                return false;
            } else if (maxTeamPlayers > 0 && aTeam.getTeamMembers().size() > maxTeamPlayers) {
                arena.msg(sender, Language.parse(arena, MSG.ERROR_JOIN_TEAM_FULL, aTeam.getColoredName()));
                return false;
            } else {
                team = aTeam;
            }
        }

        if (team == null) {
            arena.msg(sender, Language.parse(arena, MSG.ERROR_JOIN_ARENA_FULL));
            return false;
        }

        final ArenaPlayer player = ArenaPlayer.parsePlayer(sender.getName());

        ArenaModuleManager.choosePlayerTeam(arena, (Player) sender,
                team.getColoredName());

        arena.markPlayedPlayer(sender.getName());

        player.setPublicChatting(!arena.getArenaConfig().getBoolean(
                CFG.CHAT_DEFAULTTEAM));

        if (commModule == null || commGoal == null) {

            if (commModule != null) {
                debug(arena, "calling event #1");

                final PAJoinEvent event = new PAJoinEvent(arena, (Player) sender, false);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    debug(arena, "! Join event cancelled by plugin !");
                    return false;
                }
                commModule.commitJoin((Player) sender, team);

                ArenaModuleManager.parseJoin(arena, (Player) sender, team);
                return true;
            }
            if (!ArenaManager.checkJoin((Player) sender, arena)) {
                arena.msg(sender, Language.parse(arena, MSG.ERROR_JOIN_REGION));
                return false;
            }
            // both null, just put the joiner to some spawn

            debug(arena, "calling event #2");

            final PAJoinEvent event = new PAJoinEvent(arena, (Player) sender, false);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                debug(arena, "! Join event cancelled by plugin !");
                return false;
            }

            if (!arena.tryJoin((Player) sender, team)) {
                return false;
            }

            if (arena.isFreeForAll()) {
                arena.msg(sender,
                        arena.getArenaConfig().getString(CFG.MSG_YOUJOINED));
                arena.broadcastExcept(
                        sender,
                        Language.parse(arena, CFG.MSG_PLAYERJOINED,
                                sender.getName()));
            } else {
                arena.msg(
                        sender,
                        arena.getArenaConfig()
                                .getString(CFG.MSG_YOUJOINEDTEAM)
                                .replace(
                                        "%1%",
                                        team.getColoredName()
                                                + ChatColor.COLOR_CHAR + 'r'));
                arena.broadcastExcept(
                        sender,
                        Language.parse(arena, CFG.MSG_PLAYERJOINEDTEAM,
                                sender.getName(), team.getColoredName()
                                        + ChatColor.COLOR_CHAR + 'r'));
            }
            ArenaModuleManager.parseJoin(arena, (Player) sender, team);

            arena.getGoal().initiate(((Player) sender));
            ArenaModuleManager.initiate(arena, (Player) sender);

            if (arena.getFighters().size() > 1
                    && arena.getFighters().size() >= arena.getArenaConfig()
                    .getInt(CFG.READY_MINPLAYERS)) {
                arena.setFightInProgress(true);
                for (final ArenaTeam ateam : arena.getTeams()) {
                    SpawnManager.distribute(arena, ateam);
                }

                arena.getGoal().parseStart();

                for (final ArenaModule mod : arena.getMods()) {
                    mod.parseStart();
                }
            }

            if (player.getArenaClass() != null && arena.startRunner != null) {
                player.setStatus(Status.READY);
            }

            return true;
        }

        debug(arena, "calling event #3");

        final PAJoinEvent event = new PAJoinEvent(arena, (Player) sender, false);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            debug(arena, "! Join event cancelled by plugin !");
            return false;
        }

        commModule.commitJoin((Player) sender, team);

        ArenaModuleManager.parseJoin(arena, (Player) sender, team);

        if (player.getArenaClass() != null && arena.startRunner != null) {
            player.setStatus(Status.READY);
        }
        return true;
    }

    public static void handlePlayerDeath(final Arena arena,
                                         final Player player, final PlayerDeathEvent event) {

        ArenaGoal goal = arena.getGoal();
        boolean doesRespawn = true;
        boolean goalHandlesDeath = true;
        if(goal.checkPlayerDeath(player) == null) {
            goalHandlesDeath = false;
        } else {
            doesRespawn = goal.checkPlayerDeath(player);
        }

        StatisticsManager.kill(arena, player.getKiller(), player, doesRespawn);
        if (arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGES) ||
                arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGESCUSTOM)) {
            event.setDeathMessage("");
        }

        if (player.getKiller() != null) {
            player.getKiller().setFoodLevel(
                    player.getKiller().getFoodLevel()
                            + arena.getArenaConfig().getInt(
                            CFG.PLAYER_FEEDFORKILL));
            if (arena.getArenaConfig().getBoolean(CFG.PLAYER_HEALFORKILL)) {
                PlayerState.playersetHealth(player.getKiller(), (int) player.getKiller().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            }
            if (arena.getArenaConfig().getBoolean(CFG.PLAYER_REFILLFORKILL)) {
                InventoryManager.clearInventory(player.getKiller());
                ArenaPlayer.parsePlayer(player.getKiller().getName()).getArenaClass().equip(player.getKiller());
            }
            if (arena.getArenaConfig().getItems(CFG.PLAYER_ITEMSONKILL) != null) {
                ItemStack[] items = arena.getArenaConfig().getItems(CFG.PLAYER_ITEMSONKILL);
                for (ItemStack item : items) {
                    if (item != null) {
                        player.getKiller().getInventory().addItem(item.clone());
                    }
                }
            }
            if (arena.getArenaConfig().getBoolean(CFG.USES_TELEPORTONKILL)) {
                SpawnManager.respawn(arena, ArenaPlayer.parsePlayer(player.getKiller().getName()), null);
            }
        }

        if (!goalHandlesDeath) {
            debug(arena, player, "no mod handles player deaths");


            List<ItemStack> returned = null;
            if (arena.getArenaConfig().getBoolean(
                    CFG.PLAYER_DROPSINVENTORY)) {
                returned = InventoryManager.drop(player);
                final int exp = event.getDroppedExp();
                event.getDrops().clear();
                if (arena.getArenaConfig().getBoolean(CFG.PLAYER_PREVENTDEATH)) {
                    InventoryManager.dropExp(player, exp);
                } else if (arena.getArenaConfig().getBoolean(CFG.PLAYER_DROPSEXP)) {
                    debug(arena, player, "exp: " + exp);
                    event.setDroppedExp(exp);
                }
            }
            final ArenaTeam respawnTeam = ArenaPlayer.parsePlayer(
                    player.getName()).getArenaTeam();

            if (arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                arena.broadcast(Language.parse(arena, MSG.FIGHT_KILLED_BY,
                        respawnTeam.colorizePlayer(player) + ChatColor.YELLOW,
                        arena.parseDeathCause(player, event.getEntity()
                                .getLastDamageCause().getCause(), event
                                .getEntity().getKiller())));
            }

            ArenaModuleManager.parsePlayerDeath(arena, player, event
                    .getEntity().getLastDamageCause());

            if (returned == null) {
                if (arena.getArenaConfig().getBoolean(
                        CFG.PLAYER_DROPSINVENTORY)) {
                    returned = InventoryManager.drop(player);
                    event.getDrops().clear();
                } else {
                    returned = new ArrayList<>();
                    returned.addAll(event.getDrops());
                    event.getDrops().clear();
                }
            }

            handleRespawn(arena, ArenaPlayer.parsePlayer(player.getName()), returned);


            arena.getGoal().parsePlayerDeath(player, player.getLastDamageCause());

            return;
        }

        debug(arena, player, "handled by: " + goal.getName());
        final int exp = event.getDroppedExp();

        goal.commitPlayerDeath(player, doesRespawn, event);
        debug(arena, player, "parsing death: " + goal.getName());
        goal.parsePlayerDeath(player, player.getLastDamageCause());

        ArenaModuleManager.parsePlayerDeath(arena, player,
                player.getLastDamageCause());

        if (!arena.getArenaConfig().getBoolean(CFG.PLAYER_DROPSINVENTORY) || !ArenaPlayer.parsePlayer(player.getName()).mayDropInventory()) {
            event.getDrops().clear();
        }
        if (doesRespawn
                || arena.getArenaConfig().getBoolean(CFG.PLAYER_PREVENTDEATH)) {
            InventoryManager.dropExp(player, exp);
        } else if (arena.getArenaConfig().getBoolean(CFG.PLAYER_DROPSEXP)) {
            event.setDroppedExp(exp);
            debug(arena, player, "exp: " + exp);
        }
    }

    public static void handleRespawn(final Arena arena,
                                     final ArenaPlayer aPlayer, final List<ItemStack> drops) {

        for (final ArenaModule mod : arena.getMods()) {
            if (mod.tryDeathOverride(aPlayer, drops)) {
                return;
            }
        }
        debug(arena, aPlayer.get(), "handleRespawn!");
        new InventoryRefillRunnable(arena, aPlayer.get(), drops);
        SpawnManager.respawn(arena, aPlayer, null);
        arena.unKillPlayer(aPlayer.get(),
                aPlayer.get().getLastDamageCause() == null ? null : aPlayer
                        .get().getLastDamageCause().getCause(), aPlayer.get()
                        .getKiller());

    }

    /**
     * try to set a flag
     *
     * @param player the player trying to set
     * @param block  the block being set
     * @return true if the handling is successful and if the event should be
     * cancelled
     */
    public static boolean handleSetFlag(final Player player, final Block block) {
        final Arena arena = PAA_Region.activeSelections.get(player.getName());

        if (arena == null) {
            return false;
        }

        ArenaGoal commit = arena.getGoal();
        PACheck res = commit.checkSetBlock(new PACheck(), player, block);
        if (res.getPriority() <= 0) {
            // fail
            commit = null;
        }

        if (res.hasError()) {
            arena.msg(Bukkit.getConsoleSender(),
                    Language.parse(arena, MSG.ERROR_ERROR, res.getError()));
            return false;
        }

        if (commit == null) {
            return false;
        }

        return commit.commitSetFlag(player, block);
    }

    public static boolean handleSpectate(final Arena arena,
                                      final CommandSender sender) {
        PACheck res = new PACheck();

        debug(arena, sender, "handling spectator");

        // priority will be set by flags, the max priority will be called

        ArenaModule commit = null;

        int priority = 0;
        for (final ArenaModule mod : arena.getMods()) {
            res = mod.checkJoin(sender, res, false);
            if (res.getPriority() > priority && priority >= 0) {
                debug(arena, sender, "success and higher priority");
                priority = res.getPriority();
                commit = mod;
            } else if (res.getPriority() < 0 || priority < 0) {
                debug(arena, sender, "fail");
                priority = res.getPriority();
                commit = null;
            }
        }

        if (res.hasError()) {
            arena.msg(sender,
                    Language.parse(arena, MSG.ERROR_ERROR, res.getError()));
            return false;
        }

        if (commit == null) {
            debug(arena, sender, "commit null");
            return false;
        }

        final PAJoinEvent event = new PAJoinEvent(arena, (Player) sender, true);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            debug(arena, "! Spectate event cancelled by plugin !");
            return false;
        }

        commit.commitSpectate((Player) sender);
        return true;
    }

    public static Boolean handleStart(final Arena arena,
                                      final CommandSender sender) {
        return handleStart(arena, sender, false);
    }

    public static Boolean handleStart(final Arena arena, final CommandSender sender, final boolean force) {
        debug(arena, "handling start!");

        ArenaGoal commit = arena.getGoal();
        PACheck res = commit.checkStart(new PACheck());
        if (res.getPriority() <= 0) {
            // fail
            commit = null;
        }

        if (!force && res.hasError()) {
            debug(arena, "not forcing and we have error: " + res.getError());
            if (sender == null) {
                arena.msg(Bukkit.getConsoleSender(),
                        Language.parse(arena, MSG.ERROR_ERROR, res.getError()));
            } else {
                arena.msg(sender,
                        Language.parse(arena, MSG.ERROR_ERROR, res.getError()));
            }
            return null;
        }

        if (!force && arena.getFighters().size() < 2
                || arena.getFighters().size() < arena.getArenaConfig().getInt(
                CFG.READY_MINPLAYERS)) {
            debug(arena, "not forcing and we have less than minplayers");
            return null;
        }

        final PAStartEvent event = new PAStartEvent(arena);
        Bukkit.getPluginManager().callEvent(event);
        if (!force && event.isCancelled()) {
            debug(arena, "not forcing and cancelled by other plugin");
            return false;
        }

        debug(arena, sender, "teleporting all players to their spawns");

        if (commit == null) {
            for (final ArenaTeam team : arena.getTeams()) {
                SpawnManager.distribute(arena, team);
            }
        } else {
            commit.commitStart(); // override spawning
        }

        debug(arena, sender, "teleported everyone!");

        arena.broadcast(Language.parse(arena, MSG.FIGHT_BEGINS));
        arena.setFightInProgress(true);

        arena.getGoal().parseStart();

        for (final ArenaModule x : arena.getMods()) {
            x.parseStart();
        }

        final SpawnCampRunnable scr = new SpawnCampRunnable(arena);
        arena.spawnCampRunnerID = Bukkit.getScheduler()
                .scheduleSyncRepeatingTask(PVPArena.getInstance(), scr, 100L,
                        arena.getArenaConfig().getInt(CFG.TIME_REGIONTIMER));
        scr.setId(arena.spawnCampRunnerID);

        final Set<ArenaRegion> battleRegions = arena.getRegionsByType(RegionType.BATTLE);

        for (final ArenaRegion region : arena.getRegions()) {
            final Set<RegionFlag> flags = region.getFlags();
            if (flags.contains(RegionFlag.DEATH) || flags.contains(RegionFlag.WIN) || flags.contains(RegionFlag.LOSE) || flags.contains(RegionFlag.NOCAMP)) {
                region.initTimer();
                continue;
            }

            final RegionType type = region.getType();
            if (type == RegionType.BATTLE || type == RegionType.WATCH || type == RegionType.LOUNGE) {
                region.initTimer();
                continue;
            }

            if (battleRegions.size() == 1 && region.getType().equals(RegionType.BATTLE)) {
                region.initTimer();
            }
        }
        if (battleRegions.size() > 1) {
            final Set<ArenaRegion> removals = new HashSet<>();

            for (final ArenaRegion checkRegion : battleRegions) {
                for (final ArenaRegion checkRegion2 : battleRegions) {
                    if (checkRegion.equals(checkRegion2)) {
                        continue;
                    }
                    if (!removals.contains(checkRegion) && checkRegion.containsRegion(checkRegion2) && removals.size() < battleRegions.size()) {
                        // prevent regions from erasing each other if size is the same!
                        // first catch removes
                        // don't check removed ones for containing others, they are out and inner ones will be caught!
                        removals.add(checkRegion2);
                    }
                }
            }
            battleRegions.removeAll(removals);

            for (final ArenaRegion region : battleRegions) {
                region.initTimer();
            }
        }

        if (arena.getArenaConfig().getInt(CFG.TIME_PVP) > 0) {
            arena.pvpRunner = new PVPActivateRunnable(arena, arena
                    .getArenaConfig().getInt(CFG.TIME_PVP));
        }

        arena.setStartingTime();
        arena.updateScoreboards();
        return true;
    }
}
/*
 * AVAILABLE PACheckResults:
 * 
 * ArenaGoal.checkCommand() => ArenaGoal.commitCommand() ( onCommand() ) >
 * default: nothing
 * 
 * 
 * ArenaGoal.checkEnd() => ArenaGoal.commitEnd() (
 * ArenaGoalManager.checkEndAndCommit(arena) ) < used > 1: PlayerLives > 2:
 * PlayerDeathMatch > 3: TeamLives > 4: TeamDeathMatch > 5: Flags
 * 
 * ArenaGoal.checkInteract() => ArenaGoal.commitInteract() (
 * PlayerListener.onPlayerInteract() ) > 5: Flags
 * 
 * ArenaGoal.checkJoin() => ArenaGoal.commitJoin() ( PAG_Join ) < used >
 * default: tp inside
 * 
 * ArenaGoal.checkPlayerDeath() => ArenaGoal.commitPlayerDeath() (
 * PlayerLister.onPlayerDeath() ) > 1: PlayerLives > 2: PlayerDeathMatch > 3:
 * TeamLives > 4: TeamDeathMatch > 5: Flags
 * 
 * ArenaGoal.checkSetFlag() => ArenaGoal.commitSetFlag() (
 * PlayerListener.onPlayerInteract() ) > 5: Flags
 * 
 * =================================
 * 
 * ArenaModule.checkJoin() ( PAG_Join | PAG_Spectate ) < used > 1:
 * StandardLounge > 2: BattlefieldJoin > default: nothing
 * 
 * ArenaModule.checkStart() ( PAI_Ready | StartRunnable.commit() ) < used >
 * default: tp players to (team) spawns
 */
