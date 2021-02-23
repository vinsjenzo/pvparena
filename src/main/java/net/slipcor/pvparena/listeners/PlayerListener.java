package net.slipcor.pvparena.listeners;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.arena.PlayerState;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.classes.PASpawn;
import net.slipcor.pvparena.commands.PAA_Setup;
import net.slipcor.pvparena.commands.PAG_Arenaclass;
import net.slipcor.pvparena.commands.PAG_Leave;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.exceptions.GameplayException;
import net.slipcor.pvparena.loadables.ArenaModule;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.loadables.ArenaRegion;
import net.slipcor.pvparena.loadables.ArenaRegion.RegionProtection;
import net.slipcor.pvparena.loadables.ArenaRegion.RegionType;
import net.slipcor.pvparena.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.*;

import static java.util.Arrays.asList;
import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Player Listener class
 * </pre>
 *
 * @author slipcor
 * @version v0.10.2
 */

public class PlayerListener implements Listener {

    private boolean checkAndCommitCancel(final Arena arena, final Player player,
                                         final Cancellable event) {

        if (this.willBeCancelled(player, event)) {
            return true;
        }

        if (!(event instanceof PlayerInteractEvent)) {
            return false;
        }
        final PlayerInteractEvent pie = (PlayerInteractEvent) event;
        final Block block = pie.getClickedBlock();
        final Material check = arena == null ? Material.IRON_BLOCK : arena.getReadyBlock();

        if (block != null && (block.getState() instanceof Sign || block.getType() == check)) {
            debug(player, "signs and ready blocks allowed!");
            debug(player, "> false");
            return false;
        }

        debug(player, "checkAndCommitCancel");
        if (arena == null || player.hasPermission("pvparena.admin")) {
            debug(player, "no arena or admin");
            debug(player, "> false");
            return false;
        }

        if (arena.getArenaConfig().getBoolean(CFG.PERMS_LOUNGEINTERACT)) {
            return false;
        }

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        if ((aPlayer.getStatus() == Status.WATCH || aPlayer.getStatus() == Status.LOST) &&
                arena.getArenaConfig().getBoolean(CFG.PERMS_SPECINTERACT)) {
            return false;
        }

        if (!arena.isFightInProgress()) {
            debug(arena, player, "arena != null and fight not in progress => cancel");
            debug(arena, player, "> true");

            PriorityManager.handleInteract(arena, player, pie);
            event.setCancelled(true);
            return true;
        }

        if (aPlayer.getStatus() != Status.FIGHT) {
            debug(player, "not fighting => cancel");
            debug(player, "> true");
            event.setCancelled(true);
            return true;
        }

        debug(player, "> false");
        return false;
    }

    private static boolean willBeCancelled(final Player player, final Cancellable event) {
        if (event instanceof PlayerInteractEvent) {
            PlayerInteractEvent e = (PlayerInteractEvent) event;
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                debug(player, "Allowing right click interact");
                return false;
            }
        }
        if (ArenaPlayer.parsePlayer(player.getName()).getStatus() == Status.LOST) {
            debug(player, "cancelling because LOST");
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(final AsyncPlayerChatEvent event) {

        final Player player = event.getPlayer();

        if (PAA_Setup.activeSetups.containsKey(player.getName())) {
            PAA_Setup.chat(player, event.getMessage());
            return;
        }

        final Arena arena = ArenaPlayer.parsePlayer(player.getName()).getArena();
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        if (arena == null) {
            return; // no fighting player => OUT
        }
        final ArenaTeam team = aPlayer.getArenaTeam();
        if (team == null ||
                aPlayer.getStatus() == Status.DEAD && aPlayer.get() == null ||
                aPlayer.getStatus() == Status.LOST ||
                aPlayer.getStatus() == Status.WATCH) {
            if (!arena.getArenaConfig().getBoolean(CFG.PERMS_SPECTALK)) {
                event.setCancelled(true);
            }
            return; // no fighting player => OUT
        }
        debug(arena, player, "fighting player chatting!");
        final String sTeam = team.getName();

        if (!arena.getArenaConfig().getBoolean(CFG.CHAT_ONLYPRIVATE)) {
            if (!arena.getArenaConfig().getBoolean(CFG.CHAT_ENABLED)) {
                return; // no chat editing
            }

            if (aPlayer.isPublicChatting()) {
                return; // player not privately chatting
            }

            String toGlobal = arena.getArenaConfig().getString(CFG.CHAT_TOGLOBAL);

            if (!toGlobal.equalsIgnoreCase("none")) {
                if (event.getMessage().toLowerCase().startsWith(
                        toGlobal.toLowerCase())) {
                    event.setMessage(event.getMessage().substring(toGlobal.length()));
                    return;
                }
            }

            arena.tellTeam(sTeam, event.getMessage(), team.getColor(),
                    event.getPlayer());
            event.setCancelled(true);
            return;
        }

        if (arena.getArenaConfig().getBoolean(CFG.CHAT_ENABLED)
                && !aPlayer.isPublicChatting()) {
            arena.tellTeam(sTeam, event.getMessage(), team.getColor(),
                    event.getPlayer());
            event.setCancelled(true);
            return;
        }

        arena.broadcastColored(event.getMessage(), team.getColor(),
                event.getPlayer()); //
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        final Player player = event.getPlayer();

        if (PAA_Setup.activeSetups.containsKey(player.getName())) {
            PAA_Setup.chat(player, event.getMessage().substring(1));
            return;
        }

        final Arena arena = ArenaPlayer.parsePlayer(player.getName()).getArena();
        if (arena == null || player.isOp() || PVPArena.hasAdminPerms(player)
                || PVPArena.hasCreatePerms(player, arena)) {
            return; // no fighting player => OUT
        }

        final List<String> list = PVPArena.getInstance().getConfig().getStringList(
                "whitelist");
        list.add("pa");
        list.add("pvparena");
        debug(arena, player, "checking command whitelist");

        boolean wildcard = PVPArena.getInstance().getConfig().getBoolean("whitelist_wildcard", false);

        for (final String s : list) {
            if ("*".equals(s) ||
                    ((wildcard || s.endsWith(" ")) && event.getMessage().toLowerCase().startsWith('/' + s)) ||
                    (!wildcard && event.getMessage().toLowerCase().startsWith('/' + s +' '))) {
                debug(arena, player, "command allowed: " + s);
                return;
            }
        }

        list.clear();
        list.addAll(arena.getArenaConfig().getStringList(
                CFG.LISTS_CMDWHITELIST.getNode(), new ArrayList<String>()));

        if (list.size() < 1) {
            list.clear();
            list.add("ungod");
            arena.getArenaConfig().set(CFG.LISTS_CMDWHITELIST, list);
            arena.getArenaConfig().save();
        }

        list.add("pa");
        list.add("pvparena");
        debug(arena, player, "checking command whitelist");

        for (final String s : list) {
            if (event.getMessage().toLowerCase().startsWith('/' + s)) {
                debug(arena, player, "command allowed: " + s);
                return;
            }
        }

        debug(arena, player, "command blocked: " + event.getMessage());
        arena.msg(player,
                Language.parse(arena, MSG.ERROR_COMMAND_BLOCKED, event.getMessage()));
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public static void onPlayerCraft(final CraftItemEvent event) {

        final Player player = (Player) event.getWhoClicked();

        final Arena arena = ArenaPlayer.parsePlayer(player.getName()).getArena();
        if (arena == null || player.isOp() || PVPArena.hasAdminPerms(player)
                || PVPArena.hasCreatePerms(player, arena)) {
            return; // no fighting player => OUT
        }

        try {
            arena.getGoal().checkCraft(event);
        } catch (GameplayException e) {
            debug(player, "onPlayerCraft cancelled by goal: " + arena.getGoal().getName());
            return;
        }

        if (!BlockListener.isProtected(player.getLocation(), event, RegionProtection.CRAFT)) {
            return; // no craft protection
        }

        debug(arena, player, "onCraftItemEvent: fighting player");
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(final PlayerDropItemEvent event) {
        final Player player = event.getPlayer();

        if (this.willBeCancelled(player, event)) {
            return;
        }

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        final Arena arena = aPlayer.getArena();
        if (arena == null) {
            return; // no fighting player => OUT
        }
        if (aPlayer.getStatus() == Status.READY
                || aPlayer.getStatus() == Status.LOUNGE) {
            event.setCancelled(true);
            arena.msg(player, Language.parse(arena, MSG.NOTICE_NO_DROP_ITEM));
            return;
        }

        try {
            arena.getGoal().checkDrop(event);
        } catch (GameplayException e) {
            debug(player, "onPlayerDropItem cancelled by goal: " + arena.getGoal().getName());
            return;
        }

        if (!BlockListener.isProtected(player.getLocation(), event,
                RegionProtection.DROP)) {
            return; // no drop protection
        }

        if (Bukkit.getPlayer(player.getName()) == null || aPlayer.getStatus() == Status.DEAD || aPlayer.getStatus() == Status.LOST) {
            debug(arena, "Player is dead. allowing drops!");
            return;
        }

        debug(arena, player, "onPlayerDropItem: fighting player");
        arena.msg(player, Language.parse(arena, MSG.NOTICE_NO_DROP_ITEM));
        event.setCancelled(true);
        // cancel the drop event for fighting players, with message
    }

    @EventHandler
    public void onPlayerGoal(final PAGoalEvent event) {
        /*
         * content[X].contains(playerDeath) => "playerDeath:playerName"
         * content[X].contains(playerKill) => "playerKill:playerKiller:playerKilled"
         * content[X].contains(trigger) => "trigger:playerName" triggered a score
         * content[X].equals(tank) => player is tank
         * content[X].equals(infected) => player is infected
         * content[X].equals(doesRespawn) => player will respawn
         * content[X].contains(score) => "score:player:team:value"
         */
        String[] args = event.getContents();
        for (String content : args) {
            if (content != null) {
                if (content.startsWith("playerDeath")||content.startsWith("trigger")||content.startsWith("playerKill")||content.startsWith("score")) {
                    event.getArena().updateScoreboards();
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        final Player player = event.getEntity();
        final Arena arena = ArenaPlayer.parsePlayer(player.getName()).getArena();
        if (arena == null) {
            return;
        }
        PriorityManager.handlePlayerDeath(arena, player, event);
    }

    /**
     * pretend a player death
     *
     * @param arena  the arena the player is playing in
     * @param player the player to kill
     * @param eEvent the event triggering the death
     */
    public static void finallyKillPlayer(final Arena arena, final Player player,
                                         final Event eEvent) {
        EntityDamageEvent cause = null;

        if (eEvent instanceof EntityDeathEvent) {
            cause = player.getLastDamageCause();
        } else if (eEvent instanceof EntityDamageEvent) {
            cause = (EntityDamageEvent) eEvent;
        }

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        final ArenaTeam team = aPlayer.getArenaTeam();

        final String playerName = (team == null) ? player.getName() : team.colorizePlayer(player);
        if (arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
            arena.broadcast(Language.parse(arena,
                    MSG.FIGHT_KILLED_BY,
                    playerName + ChatColor.YELLOW,
                    arena.parseDeathCause(
                            player,
                            cause == null ? EntityDamageEvent.DamageCause.VOID : cause.getCause(),
                            ArenaPlayer.getLastDamagingPlayer(cause, player)
                    )
            ));
        }

        if (arena.getArenaConfig().getBoolean(CFG.PLAYER_DROPSINVENTORY)) {
            InventoryManager.drop(player);
            if (eEvent instanceof EntityDeathEvent) {
                ((EntityDeathEvent) eEvent).getDrops().clear();
            }
        }

        // Trick to avoid death screen
        Bukkit.getScheduler().scheduleSyncDelayedTask(PVPArena.getInstance(), player::closeInventory, 1);

        if (!aPlayer.hasCustomClass()) {
            InventoryManager.clearInventory(player);
        }

        arena.removePlayer(player, arena.getArenaConfig().getString(CFG.TP_DEATH), true, false);

        aPlayer.setStatus(Status.LOST);
        aPlayer.addDeath();

        PlayerState.fullReset(arena, player);

        class RunLater implements Runnable {

            @Override
            public void run() {

                boolean found = false;
                for (final ArenaModule mod : arena.getMods()) {
                    if (mod.getName().contains("Spectate")) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    new PAG_Leave().commit(arena, player, new String[0]);
                }
            }
        }
        Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), new RunLater(), 5L);

        ArenaManager.checkAndCommit(arena, false);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerHunger(final FoodLevelChangeEvent event) {
        if (event.getEntityType() != EntityType.PLAYER) {
            return;
        }

        final Player player = (Player) event.getEntity();

        final ArenaPlayer ap = ArenaPlayer.parsePlayer(player.getName());

        if (ap.getStatus() == Status.READY || ap.getStatus() == Status.LOUNGE || ap.getArena() != null && !ap.getArena().getArenaConfig().getBoolean(CFG.PLAYER_HUNGER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        debug(player, "onPlayerInteract");

        if (event.getAction() == Action.PHYSICAL) {
            debug(player, "returning: physical");
            return;
        }

        if (event.getHand().equals(EquipmentSlot.OFF_HAND)) {
            debug(player, "exiting: offhand");
            return;
        }

        debug(player, "event pre cancelled: " + event.isCancelled()
        );

        Arena arena = null;

        if (event.hasBlock()) {
            debug(player, "block: " + event.getClickedBlock().getType().name());

            arena = ArenaManager.getArenaByRegionLocation(new PABlockLocation(
                    event.getClickedBlock().getLocation()));
            if (this.checkAndCommitCancel(arena, event.getPlayer(), event)) {
                if (arena != null) {
                    PriorityManager.handleInteract(arena, player, event);
                }
                return;
            }
        }

        if (arena != null && ArenaModuleManager.onPlayerInteract(arena, event)) {
            debug(player, "returning: #1");
            return;
        }

        if (PriorityManager.handleSetFlag(player, event.getClickedBlock())) {
            debug(player, "returning: #2");
            event.setCancelled(true);
            return;
        }

        if (ArenaRegion.checkRegionSetPosition(event, player)) {
            debug(player, "returning: #3");
            return;
        }

        arena = ArenaPlayer.parsePlayer(player.getName()).getArena();
        if (arena == null) {
            debug(player, "returning: #4");
            ArenaManager.trySignJoin(event, player);
            return;
        }

        PriorityManager.handleInteract(arena, player, event);

        debug(arena, player, "event post cancelled: " + event.isCancelled());

        //TODO: seriously, why?
        final boolean whyMe = arena.isFightInProgress()
                && !arena.getGoal().allowsJoinInBattle();

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        final ArenaTeam team = aPlayer.getArenaTeam();

        if (aPlayer.getStatus() == Status.WATCH &&
                arena.getArenaConfig().getBoolean(CFG.PERMS_SPECINTERACT)) {
            debug(arena, "allowing spectator interaction due to config setting!");
            return;
        }

        if (aPlayer.getStatus() != Status.FIGHT) {
            if (whyMe) {
                debug(arena, player, "exiting! fight in progress AND no INBATTLEJOIN arena!");
                return;
            }
            if (asList(Status.LOUNGE, Status.READY).contains(aPlayer.getStatus()) &&
                    arena.getArenaConfig().getBoolean(CFG.PERMS_LOUNGEINTERACT)) {
                debug(arena, "allowing lounge interaction due to config setting!");
                event.setCancelled(false);
            } else if (aPlayer.getStatus() != Status.LOUNGE && aPlayer.getStatus() != Status.READY) {
                debug(arena, player, "cancelling: not fighting nor in the lounge");
                event.setCancelled(true);
            } else if (aPlayer.getArena() != null && team != null) {
                // fighting player inside the lobby!
                event.setCancelled(true);
            }
        }

        if (team == null) {
            debug(arena, player, "returning: no team");
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK ||
                event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            final Block block = event.getClickedBlock();
            debug(arena, player, "player team: " + team.getName());
            if (block.getState() instanceof Sign) {
                debug(arena, player, "sign click!");
                final Sign sign = (Sign) block.getState();

                if ("custom".equalsIgnoreCase(sign.getLine(0)) || arena
                        .getClass(sign.getLine(0)) != null) {
                    if (arena.isFightInProgress()) {
                        PAG_Arenaclass ac = new PAG_Arenaclass();
                        ac.commit(arena, player, new String[]{sign.getLine(0)});
                    } else {
                        arena.chooseClass(player, sign, sign.getLine(0));
                    }
                } else {
                    debug(arena, player, '|' + sign.getLine(0) + '|');
                    debug(arena, player, arena.getClass(sign.getLine(0)));
                    debug(arena, player, team);

                    if (whyMe) {
                        debug(arena, player, "exiting! fight in progress AND no INBATTLEJOIN arena!");
                    }
                }
                return;
            }

            if (whyMe) {
                debug(arena, player, "exiting! fight in progress AND no INBATTLEJOIN arena!");
                return;
            }
            debug(arena, player, "block click!");

            final Material mMat = arena.getReadyBlock();
            debug(arena, player, "clicked " + block.getType().name() + ", is it " + mMat.name()
                        + '?');
            if (block.getType() == mMat) {
                debug(arena, player, "clicked ready block!");
                if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    debug(arena, player, "out: offhand!");
                    return; // double event
                }
                if (aPlayer.getArenaClass() == null || aPlayer.getArenaClass().getName() != null && aPlayer.getArenaClass().getName().isEmpty()) {
                    arena.msg(player, Language.parse(arena, MSG.ERROR_READY_NOCLASS));
                    return; // not chosen class => OUT
                }
                if (arena.startRunner != null) {
                    return; // counting down => OUT
                }
                if (aPlayer.getStatus() != Status.LOUNGE && aPlayer.getStatus() != Status.READY) {
                    return;
                }
                event.setCancelled(true);
                debug(arena, "Cancelled ready block click event to prevent itemstack consumation");
                Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), new Runnable() {
                    @Override
                    public void run() {
                        aPlayer.get().updateInventory();
                    }
                }, 1L);
                final boolean alreadyReady = aPlayer.getStatus() == Status.READY;

                debug(arena, player, "===============");
                String msg = "===== class: " + (aPlayer.getArenaClass() == null ? "null" : aPlayer.getArenaClass().getName()) + " =====";
                debug(arena, player, msg);
                debug(arena, player, "===============");

                if (!arena.isFightInProgress()) {
                    if (aPlayer.getStatus() != Status.READY) {
                        arena.msg(player, Language.parse(arena, MSG.READY_DONE));
                        if (!alreadyReady) {
                            arena.broadcast(Language.parse(arena, MSG.PLAYER_READY, aPlayer
                                    .getArenaTeam().colorizePlayer(aPlayer.get())));
                        }
                    }
                    aPlayer.setStatus(Status.READY);
                    if (!alreadyReady && aPlayer.getArenaTeam().isEveryoneReady()) {
                        arena.broadcast(Language.parse(arena, MSG.TEAM_READY, aPlayer
                                .getArenaTeam().getColoredName()));
                    }

                    if (arena.getArenaConfig().getBoolean(CFG.USES_EVENTEAMS)
                            && !TeamManager.checkEven(arena)) {
                        arena.msg(player,
                                Language.parse(arena, MSG.NOTICE_WAITING_EQUAL));
                        return; // even teams desired, not done => announce
                    }

                    if (!ArenaRegion.checkRegions(arena)) {
                        arena.msg(player,
                                Language.parse(arena, MSG.NOTICE_WAITING_FOR_ARENA));
                        return;
                    }

                    final String error = arena.ready();

                    if (error == null) {
                        arena.start();
                    } else if (error.isEmpty()) {
                        arena.countDown();
                    } else {
                        arena.msg(player, error);
                    }
                    return;
                }

                final Set<PASpawn> spawns = new HashSet<>();
                if (arena.getArenaConfig().getBoolean(CFG.GENERAL_CLASSSPAWN)) {
                    final String arenaClass = aPlayer.getArenaClass().getName();
                    spawns.addAll(SpawnManager.getPASpawnsStartingWith(arena, team.getName() + arenaClass + "spawn"));
                } else if (arena.isFreeForAll()) {
                    if ("free".equals(team.getName())) {
                        spawns.addAll(SpawnManager.getPASpawnsStartingWith(arena, "spawn"));
                    } else {
                        spawns.addAll(SpawnManager.getPASpawnsStartingWith(arena, team.getName()));
                    }
                } else {
                    spawns.addAll(SpawnManager.getPASpawnsStartingWith(arena, team.getName() + "spawn"));
                }

                int pos = new Random().nextInt(spawns.size());

                for (final PASpawn spawn : spawns) {

                    if (--pos < 0) {
                        arena.tpPlayerToCoordName(aPlayer, spawn.getName());
                        break;
                    }
                }

                ArenaPlayer.parsePlayer(player.getName()).setStatus(
                        Status.FIGHT);

                ArenaModuleManager.lateJoin(arena, player);
                arena.getGoal().lateJoin(player);
            } else if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                arena = ArenaManager.getArenaByRegionLocation(new PABlockLocation(block.getLocation()));
                if (arena != null) {

                    final Set<ArenaRegion> bl_regions = arena.getRegionsByType(RegionType.BL_INV);
                    out:
                    if (!event.isCancelled() && bl_regions != null && !bl_regions.isEmpty()) {
                        for (final ArenaRegion region : bl_regions) {
                            if (region.getShape().contains(new PABlockLocation(block.getLocation()))) {
                                if (region.getRegionName().toLowerCase().contains(team.getName().toLowerCase())
                                        || region.getRegionName().toLowerCase().contains(
                                        aPlayer.getArenaClass().getName().toLowerCase())) {
                                    event.setCancelled(true);
                                    break out;
                                }
                            }
                        }
                    }
                    final Set<ArenaRegion> wl_regions = arena.getRegionsByType(RegionType.WL_INV);
                    out:
                    if (!event.isCancelled() && wl_regions != null && !wl_regions.isEmpty()) {
                        event.setCancelled(true);
                        for (final ArenaRegion region : wl_regions) {
                            if (region.getShape().contains(new PABlockLocation(block.getLocation()))) {
                                if (region.getRegionName().toLowerCase().contains(team.getName().toLowerCase())
                                        || region.getRegionName().toLowerCase().contains(
                                        aPlayer.getArenaClass().getName().toLowerCase())) {
                                    event.setCancelled(false);
                                    break out;
                                }
                            }
                        }
                    }


                    if (!event.isCancelled() && arena.getArenaConfig().getBoolean(CFG.PLAYER_QUICKLOOT)) {
                        final Chest c = (Chest) block.getState();
                        InventoryManager.transferItems(player, c.getBlockInventory());
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerItemConsume(final PlayerItemConsumeEvent event) {
        ArenaPlayer arenaPlayer = ArenaPlayer.parsePlayer(event.getPlayer().getName());
        if (arenaPlayer.getArena() != null && arenaPlayer.getStatus() != Status.FIGHT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public static void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        if (player.isDead()) {
            return;
        }

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        aPlayer.setArena(null);
        // instantiate and/or reset a player. This fixes issues with leaving
        // players
        // and makes sure every player is an arenaplayer ^^

        aPlayer.readDump();
        final Arena arena = aPlayer.getArena();

        if (arena != null) {
            arena.playerLeave(player, CFG.TP_EXIT, true, true, false);
        }

        debug(player, "OP joins the game");
        if (player.isOp() && PVPArena.getInstance().getUpdateChecker() != null) {
            PVPArena.getInstance().getUpdateChecker().displayMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKicked(final PlayerKickEvent event) {
        final Player player = event.getPlayer();
        final Arena arena = ArenaPlayer.parsePlayer(player.getName()).getArena();
        if (arena == null) {
            return; // no fighting player => OUT
        }
        arena.playerLeave(player, CFG.TP_EXIT, false, true, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(final PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        // aPlayer.setArena(null);
        // instantiate and/or reset a player. This fixes issues with leaving
        // players and makes sure every player is an arenaplayer ^^


        if (aPlayer.getArena() != null && aPlayer.getStatus() == Status.FIGHT) {
            Arena arena = aPlayer.getArena();
            debug(arena, "Trying to override a rogue RespawnEvent!");
        }

        aPlayer.debugPrint();

        // aPlayer.readDump();
        final Arena arena = aPlayer.getArena();
        if (arena != null) {
            arena.playerLeave(player, CFG.TP_EXIT, true, false, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPickupItem(final EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        final Player player = (Player) event.getEntity();

        if (willBeCancelled(player, event)) {
            return;
        }

        final Arena arena = ArenaPlayer.parsePlayer(player.getName()).getArena();

        if (arena != null) {

            try {
                arena.getGoal().checkPickup(event);
            } catch (GameplayException e) {
                debug(player, "onPlayerPickupItem cancelled by goal: " + arena.getGoal().getName());
                return;
            }
        }

        if (arena == null || !BlockListener.isProtected(player.getLocation(), event, RegionProtection.PICKUP)) {
            return; // no fighting player or no powerups => OUT
        }
        arena.getGoal().onPlayerPickUp(event);
        ArenaModuleManager.onPlayerPickupItem(arena, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final Arena arena = ArenaPlayer.parsePlayer(player.getName()).getArena();
        if (arena == null) {
            return; // no fighting player => OUT
        }
        arena.playerLeave(player, CFG.TP_EXIT, false, true, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        final Player player = event.getPlayer();
        Arena arena = ArenaPlayer.parsePlayer(player.getName()).getArena();

        if (arena == null) {
            if (event.getTo() == null) {

                PVPArena.getInstance().getLogger().warning("Player teleported to NULL: " + event.getPlayer());

                return;
            }
            arena = ArenaManager.getArenaByRegionLocation(new PABlockLocation(
                    event.getTo()));

            if (arena == null) {
                return; // no fighting player and no arena location => OUT
            }

            final Set<ArenaRegion> regs = arena.getRegionsByType(RegionType.BATTLE);
            boolean contained = false;
            for (final ArenaRegion reg : regs) {
                if (reg.getShape().contains(new PABlockLocation(event.getTo()))) {
                    contained = true;
                    break;
                }
            }
            if (!contained) {
                return;
            }
        }

        debug(arena, player, "onPlayerTeleport: fighting player '"
                + event.getPlayer().getName() + "' (uncancel)");
        event.setCancelled(false); // fighting player - first recon NOT to
        // cancel!

        if (player.getGameMode() == GameMode.SPECTATOR && event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return; // ignore spectators
        }

        debug(arena, player, "aimed location: " + event.getTo());


        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && ArenaPlayer.parsePlayer(player.getName()).getStatus() != Status.FIGHT) {
            debug(arena, player, "onPlayerTeleport: ender pearl when not fighting, cancelling!");
            event.setCancelled(true); // cancel and out
            return;
        }

        if (ArenaPlayer.parsePlayer(player.getName()).isTelePass()
                || player.hasPermission("pvparena.telepass")) {

            this.maybeFixInvisibility(arena, player);

            return; // if allowed => OUT
        }
        debug(arena, player, "telepass: no!!");

        final Set<ArenaRegion> regions = arena
                .getRegionsByType(RegionType.BATTLE);

        if (regions == null || regions.size() < 0) {
            this.maybeFixInvisibility(arena, player);

            return;
        }

        for (final ArenaRegion r : regions) {
            if (r.getShape().contains(new PABlockLocation(event.getTo()))
                    || r.getShape().contains(new PABlockLocation(event.getFrom()))) {
                // teleport inside the arena, allow, unless:
                if (r.getProtections().contains(RegionProtection.TELEPORT)) {
                    continue;
                }
                this.maybeFixInvisibility(arena, player);

                return;
            }
        }

        debug(arena, player, "onPlayerTeleport: no tele pass, cancelling!");
        event.setCancelled(true); // cancel and tell
        arena.msg(player, Language.parse(arena, MSG.NOTICE_NO_TELEPORT));
    }

    private void maybeFixInvisibility(final Arena arena, final Player player) {
        if (arena.getArenaConfig().getBoolean(CFG.USES_EVILINVISIBILITYFIX)) {
            class RunLater implements Runnable {

                @Override
                public void run() {
                    for (final ArenaPlayer otherPlayer : arena.getFighters()) {
                        if (otherPlayer.get() != null) {
                            otherPlayer.get().showPlayer(PVPArena.getInstance(), player);
                        }
                    }
                }

            }
            try {
                Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), new RunLater(), 5L);
            } catch (final IllegalPluginAccessException e) {

            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerVelocity(final PlayerVelocityEvent event) {
        final Player player = event.getPlayer();

        final Arena arena = ArenaPlayer.parsePlayer(player.getName()).getArena();
        if (arena == null) {
            return; // no fighting player or no powerups => OUT
        }
        ArenaModuleManager.onPlayerVelocity(arena, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerVelocity(final ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            final Player player = (Player) event.getEntity().getShooter();
            final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
            final Arena arena = aPlayer.getArena();
            if (arena == null) {
                return; // no fighting player => OUT
            }
            if (aPlayer.getStatus() == Status.FIGHT || aPlayer.getStatus() == Status.NULL) {
                return;
            }
            event.setCancelled(true);
        }
    }

}
