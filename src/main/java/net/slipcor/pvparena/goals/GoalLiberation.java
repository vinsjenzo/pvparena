package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.ArenaClass;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.classes.PACheck;
import net.slipcor.pvparena.commands.PAA_Region;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.managers.InventoryManager;
import net.slipcor.pvparena.managers.SpawnManager;
import net.slipcor.pvparena.managers.TeamManager;
import net.slipcor.pvparena.runnables.EndRunnable;
import net.slipcor.pvparena.runnables.InventoryRefillRunnable;
import net.slipcor.pvparena.runnables.RespawnRunnable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

import static java.util.Optional.ofNullable;
import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Goal class "Liberation"
 * </pre>
 * <p/>
 * Players have lives. When every life is lost, the player is teleported
 * to the killer's team's jail. Once every player of a team is jailed, the
 * team is out.
 *
 * @author slipcor
 */

public class GoalLiberation extends ArenaGoal {
    public GoalLiberation() {
        super("Liberation");
    }

    private EndRunnable endRunner;
    private String flagName = "";

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    private static final int PRIORITY = 10;

    @Override
    public PACheck checkCommand(final PACheck res, final String string) {
        if (res.getPriority() > PRIORITY) {
            return res;
        }

        for (final ArenaTeam team : this.arena.getTeams()) {
            final String sTeam = team.getName();
            if (string.contains(sTeam + "button")) {
                res.setPriority(this, PRIORITY);
            }
        }

        return res;
    }

    @Override
    public List<String> getMain() {
        final List<String> result = new ArrayList<>();
        if (this.arena != null) {
            for (final ArenaTeam team : this.arena.getTeams()) {
                final String sTeam = team.getName();
                result.add(sTeam + "button");
            }
        }
        return result;
    }

    @Override
    public PACheck checkEnd(final PACheck res) {
        debug(this.arena, "checkEnd - " + this.arena.getName());
        if (res.getPriority() > PRIORITY) {
            debug(this.arena, res.getPriority() + ">" + PRIORITY);
            return res;
        }

        if (!this.arena.isFreeForAll()) {
            debug(this.arena, "TEAMS!");
            final int count = TeamManager.countActiveTeams(this.arena);
            debug(this.arena, "count: " + count);

            if (count <= 1) {
                res.setPriority(this, PRIORITY); // yep. only one team left. go!
            }
            return res;
        }

        PVPArena.getInstance().getLogger().warning("Liberation goal running in FFA mode: " + this.arena.getName());

        return res;
    }

    @Override
    public String checkForMissingSpawns(final Set<String> list) {
        if (!this.arena.isFreeForAll()) {
            final String team = this.checkForMissingTeamSpawn(list);
            if (team != null) {
                return team;
            }

            return this.checkForMissingTeamCustom(list, "jail");
        }
        PVPArena.getInstance().getLogger().warning("Liberation goal running in FFA mode: " + this.arena.getName());
        return null;
    }

    /**
     * hook into an interacting player
     *
     * @param res    the PACheck instance
     * @param player the interacting player
     * @param block  the block being clicked
     * @return the PACheck instance
     */
    @Override
    public PACheck checkInteract(final PACheck res, final Player player, final Block block) {
        if (block == null || res.getPriority() > PRIORITY) {
            return res;
        }
        debug(this.arena, player, "checking interact");

        if (block.getType() != Material.STONE_BUTTON) {
            debug(this.arena, player, "block, but not button");
            return res;
        }
        debug(this.arena, player, "button click!");

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        final ArenaTeam pTeam = aPlayer.getArenaTeam();
        if (pTeam == null) {
            return res;
        }
        final Set<ArenaTeam> setTeam = new HashSet<>();

        for (final ArenaTeam team : this.arena.getTeams()) {
            setTeam.add(team);
        }

        Vector vFlag = null;
        for (final ArenaTeam team : setTeam) {
            final String aTeam = team.getName();

            if (aTeam.equals(pTeam.getName())) {
                debug(this.arena, player, "equals!OUT! ");
                continue;
            }
            if (team.getTeamMembers().size() < 1) {
                debug(this.arena, player, "size!OUT! ");
                continue; // dont check for inactive teams
            }
            debug(this.arena, player, "checking for flag of team " + aTeam);
            Vector vLoc = block.getLocation().toVector();
            debug(this.arena, player, "block: " + vLoc);
            if (!SpawnManager.getBlocksStartingWith(this.arena, aTeam + "button").isEmpty()) {
                vFlag = SpawnManager
                        .getBlockNearest(
                                SpawnManager.getBlocksStartingWith(this.arena, aTeam
                                        + "button"),
                                new PABlockLocation(player.getLocation()))
                        .toLocation().toVector();
            }
            if (vFlag != null && vLoc.distance(vFlag) < 2) {
                debug(this.arena, player, "button found!");
                debug(this.arena, player, "vFlag: " + vFlag);

                boolean success = false;

                for (final ArenaPlayer jailedPlayer : pTeam.getTeamMembers()) {
                    if (jailedPlayer.getStatus() == Status.DEAD) {
                        SpawnManager.respawn(this.arena, jailedPlayer, null);
                        final List<ItemStack> iList = new ArrayList<>();

                        for (final ItemStack item : jailedPlayer.getArenaClass().getItems()) {
                            if (item == null) {
                                continue;
                            }
                            iList.add(item.clone());
                        }
                        new InventoryRefillRunnable(this.arena, jailedPlayer.get(), iList);
                        if (this.arena.getArenaConfig().getBoolean(CFG.GOAL_LIBERATION_JAILEDSCOREBOARD)) {
                            player.getScoreboard().getObjective("lives").getScore(player.getName()).setScore(0);
                        }
                        success = true;
                    }
                }

                if (success) {

                    this.arena.broadcast(ChatColor.YELLOW + Language
                            .parse(this.arena, MSG.GOAL_LIBERATION_LIBERATED,
                                    pTeam.getColoredName()
                                            + ChatColor.YELLOW));

                    final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "trigger:" + player.getName());
                    Bukkit.getPluginManager().callEvent(gEvent);
                }

                return res;
            }
        }

        return res;
    }

    @Override
    public PACheck checkJoin(final CommandSender sender, final PACheck res, final String[] args) {
        if (res.getPriority() >= PRIORITY) {
            return res;
        }

        final int maxPlayers = this.arena.getArenaConfig().getInt(CFG.READY_MAXPLAYERS);
        final int maxTeamPlayers = this.arena.getArenaConfig().getInt(
                CFG.READY_MAXTEAMPLAYERS);

        if (maxPlayers > 0 && this.arena.getFighters().size() >= maxPlayers) {
            res.setError(this, Language.parse(this.arena, MSG.ERROR_JOIN_ARENA_FULL));
            return res;
        }

        if (args == null || args.length < 1) {
            return res;
        }

        if (!this.arena.isFreeForAll()) {
            final ArenaTeam team = this.arena.getTeam(args[0]);

            if (team != null && maxTeamPlayers > 0
                    && team.getTeamMembers().size() >= maxTeamPlayers) {
                res.setError(this, Language.parse(this.arena, MSG.ERROR_JOIN_TEAM_FULL, team.getName()));
                return res;
            }
        }

        res.setPriority(this, PRIORITY);
        return res;
    }

    @Override
    public PACheck checkSetBlock(final PACheck res, final Player player, final Block block) {

        if (res.getPriority() > PRIORITY
                || !PAA_Region.activeSelections.containsKey(player.getName())) {
            return res;
        }
        if (block == null
                || block.getType() != Material.STONE_BUTTON) {
            return res;
        }

        if (!PVPArena.hasAdminPerms(player)
                && !PVPArena.hasCreatePerms(player, this.arena)) {
            return res;
        }
        res.setPriority(this, PRIORITY); // success :)

        return res;
    }

    @Override
    public PACheck checkPlayerDeath(final PACheck res, final Player player) {
        if (res.getPriority() <= PRIORITY) {
            res.setPriority(this, PRIORITY);
            final int pos = this.getLifeMap().get(player.getName());
            debug(this.arena, player, "lives before death: " + pos);
            if (pos <= 1) {
                this.getLifeMap().put(player.getName(), 1);

                final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

                final ArenaTeam team = aPlayer.getArenaTeam();

                boolean someoneAlive = false;

                for (final ArenaPlayer temp : team.getTeamMembers()) {
                    if (temp.getStatus() == Status.FIGHT) {
                        someoneAlive = true;
                        break;
                    }
                }

                if (!someoneAlive) {
                    res.setError(this, "0");
                }

            }
        }
        return res;
    }

    @Override
    public void commitCommand(final CommandSender sender, final String[] args) {
        if (args[0].contains("button")) {
            for (final ArenaTeam team : this.arena.getTeams()) {
                final String sTeam = team.getName();
                if (args[0].contains(sTeam + "button")) {
                    this.flagName = args[0];
                    PAA_Region.activeSelections.put(sender.getName(), this.arena);

                    this.arena.msg(sender,
                            Language.parse(this.arena, MSG.GOAL_LIBERATION_TOSET, this.flagName));
                }
            }
        }
    }

    @Override
    public void commitEnd(final boolean force) {
        if (this.endRunner != null) {
            return;
        }
        if (this.arena.realEndRunner != null) {
            debug(this.arena, "[LIBERATION] already ending");
            return;
        }

        final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "");
        Bukkit.getPluginManager().callEvent(gEvent);
        for (final ArenaTeam team : this.arena.getTeams()) {
            for (final ArenaPlayer ap : team.getTeamMembers()) {
                if (ap.getStatus() != Status.FIGHT) {
                    continue;
                }
                if (this.arena.isFreeForAll()) {
                    ArenaModuleManager.announce(this.arena,
                            Language.parse(this.arena, MSG.PLAYER_HAS_WON, ap.getName()),
                            "END");

                    ArenaModuleManager.announce(this.arena,
                            Language.parse(this.arena, MSG.PLAYER_HAS_WON, ap.getName()),
                            "WINNER");

                    this.arena.broadcast(Language.parse(this.arena, MSG.PLAYER_HAS_WON,
                            ap.getName()));
                } else {

                    ArenaModuleManager.announce(
                            this.arena,
                            Language.parse(this.arena, MSG.TEAM_HAS_WON,
                                    team.getColoredName()), "WINNER");

                    this.arena.broadcast(Language.parse(this.arena, MSG.TEAM_HAS_WON,
                            team.getColoredName()));
                    break;
                }
            }

            if (ArenaModuleManager.commitEnd(this.arena, team)) {
                return;
            }
        }

        this.endRunner = new EndRunnable(this.arena, this.arena.getArenaConfig().getInt(
                CFG.TIME_ENDCOUNTDOWN));
    }

    @Override
    public void commitPlayerDeath(final Player player, final boolean doesRespawn,
                                  final String error, final PlayerDeathEvent event) {

        if (!this.getLifeMap().containsKey(player.getName())) {
            debug(this.arena, player, "cmd: not in life map!");
            return;
        }
        final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "playerDeath:" + player.getName());
        Bukkit.getPluginManager().callEvent(gEvent);
        int lives = this.getLifeMap().get(player.getName());
        debug(this.arena, player, "lives before death: " + lives);
        if (lives <= 1) {
            this.getLifeMap().put(player.getName(), 1);

            final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

            aPlayer.setStatus(Status.DEAD);

            final ArenaTeam team = aPlayer.getArenaTeam();

            boolean someoneAlive = false;

            for (final ArenaPlayer temp : team.getTeamMembers()) {
                if (temp.getStatus() == Status.FIGHT) {
                    someoneAlive = true;
                    break;
                }
            }

            if (someoneAlive) {

                final ArenaTeam respawnTeam = ArenaPlayer.parsePlayer(player.getName())
                        .getArenaTeam();
                if (this.arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                    this.broadcastSimpleDeathMessage(player, event);
                }
                final List<ItemStack> returned;

                if (this.arena.getArenaConfig().getBoolean(
                        CFG.PLAYER_DROPSINVENTORY)) {
                    returned = InventoryManager.drop(player);
                    event.getDrops().clear();
                } else {
                    returned = new ArrayList<>(event.getDrops());
                }
                new InventoryRefillRunnable(this.arena, aPlayer.get(), returned);

                String teamName = aPlayer.getArenaTeam().getName();

                Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), new RespawnRunnable(this.arena, aPlayer, teamName + "jail"), 1L);

                this.arena.unKillPlayer(aPlayer.get(), ofNullable(aPlayer.get().getLastDamageCause()).map(EntityDamageEvent::getCause).orElse(null), aPlayer.get().getKiller());

                if (this.arena.getArenaConfig().getBoolean(CFG.GOAL_LIBERATION_JAILEDSCOREBOARD)) {
                    aPlayer.get().getScoreboard().getObjective("lives").getScore(aPlayer.getName()).setScore(101);
                }
            } else {
                this.getLifeMap().remove(player.getName());
                final List<ItemStack> returned;

                if (this.arena.getArenaConfig().getBoolean(
                        CFG.PLAYER_DROPSINVENTORY)) {
                    returned = InventoryManager.drop(player);
                    event.getDrops().clear();
                } else {
                    returned = new ArrayList<>(event.getDrops());
                }

                PACheck.handleRespawn(this.arena,
                        ArenaPlayer.parsePlayer(player.getName()), returned);

                ArenaPlayer.parsePlayer(player.getName()).setStatus(Status.LOST);

                final ArenaTeam respawnTeam = ArenaPlayer.parsePlayer(player.getName())
                        .getArenaTeam();
                if (this.arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                    this.broadcastSimpleDeathMessage(player, event);
                }

                PACheck.handleEnd(this.arena, false);
            }

        } else {
            lives--;
            this.getLifeMap().put(player.getName(), lives);

            final ArenaTeam respawnTeam = ArenaPlayer.parsePlayer(player.getName())
                    .getArenaTeam();
            if (this.arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                this.broadcastDeathMessage(MSG.FIGHT_KILLED_BY_REMAINING, player, event, lives);
            }

            final List<ItemStack> returned;

            if (this.arena.getArenaConfig().getBoolean(
                    CFG.PLAYER_DROPSINVENTORY)) {
                returned = InventoryManager.drop(player);
                event.getDrops().clear();
            } else {
                returned = new ArrayList<>(event.getDrops());
            }

            PACheck.handleRespawn(this.arena,
                    ArenaPlayer.parsePlayer(player.getName()), returned);

        }
    }

    @Override
    public boolean commitSetFlag(final Player player, final Block block) {

        debug(this.arena, player, "trying to set a button");

        // command : /pa redbutton1
        // location: redbutton1:

        SpawnManager.setBlock(this.arena, new PABlockLocation(block.getLocation()),
                this.flagName);

        this.arena.msg(player, Language.parse(this.arena, MSG.GOAL_LIBERATION_SET, this.flagName));

        PAA_Region.activeSelections.remove(player.getName());
        this.flagName = "";

        return true;
    }

    @Override
    public void displayInfo(final CommandSender sender) {
        sender.sendMessage("lives: "
                + this.arena.getArenaConfig().getInt(CFG.GOAL_LLIVES_LIVES));
    }

    @Override
    public PACheck getLives(final PACheck res, final ArenaPlayer aPlayer) {
        if (res.getPriority() <= PRIORITY + 1000) {
            res.setError(
                    this,
                    String.valueOf(this.getLifeMap().getOrDefault(aPlayer.getName(), 0))
            );
        }
        return res;
    }

    @Override
    public boolean hasSpawn(final String string) {
        if (this.arena.isFreeForAll()) {
            PVPArena.getInstance().getLogger().warning("Liberation goal running in FFA mode! /pa " + this.arena.getName() + " !gm team");
            return false;
        }
        for (final String teamName : this.arena.getTeamNames()) {
            if (string.toLowerCase().startsWith(
                    teamName.toLowerCase() + "spawn")) {
                return true;
            }
            if (this.arena.getArenaConfig().getBoolean(CFG.GENERAL_CLASSSPAWN)) {
                for (final ArenaClass aClass : this.arena.getClasses()) {
                    if (string.toLowerCase().startsWith(teamName.toLowerCase() +
                            aClass.getName().toLowerCase() + "spawn")) {
                        return true;
                    }
                }
            }
            if (string.toLowerCase().startsWith(
                    teamName.toLowerCase() + "jail")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void initiate(final Player player) {
        this.getLifeMap().put(player.getName(),
                this.arena.getArenaConfig().getInt(CFG.GOAL_LLIVES_LIVES));
    }

    @Override
    public void parseLeave(final Player player) {
        if (player == null) {
            PVPArena.getInstance().getLogger().warning(
                    this.getName() + ": player NULL");
            return;
        }
        this.getLifeMap().remove(player.getName());
    }

    @Override
    public void parseStart() {
        for (final ArenaTeam team : this.arena.getTeams()) {
            for (final ArenaPlayer ap : team.getTeamMembers()) {
                this.getLifeMap().put(ap.getName(),
                        this.arena.getArenaConfig().getInt(CFG.GOAL_LLIVES_LIVES));
            }
        }
        if (this.arena.getArenaConfig().getBoolean(CFG.GOAL_LIBERATION_JAILEDSCOREBOARD)) {
            this.arena.addCustomScoreBoardEntry(null, Language.parse(this.arena, MSG.GOAL_LIBERATION_SCOREBOARD_HEADING), 102);
            this.arena.addCustomScoreBoardEntry(null, Language.parse(this.arena, MSG.GOAL_LIBERATION_SCOREBOARD_SEPARATOR), 100);
        }
    }

    @Override
    public void reset(final boolean force) {
        this.endRunner = null;
        this.getLifeMap().clear();
        if (this.arena.getArenaConfig().getBoolean(CFG.GOAL_LIBERATION_JAILEDSCOREBOARD)) {
            this.arena.removeCustomScoreBoardEntry(null, 102);
            this.arena.removeCustomScoreBoardEntry(null, 100);
        }
    }

    @Override
    public void setDefaults(final YamlConfiguration config) {
        if (this.arena.isFreeForAll()) {
            return;
        }

        if (config.get("teams.free") != null) {
            config.set("teams", null);
        }
        if (config.get("teams") == null) {
            debug(this.arena, "no teams defined, adding custom red and blue!");
            config.addDefault("teams.red", ChatColor.RED.name());
            config.addDefault("teams.blue", ChatColor.BLUE.name());
        }
    }

    @Override
    public void setPlayerLives(final int value) {
        final Set<String> plrs = new HashSet<>();

        for (final String name : this.getLifeMap().keySet()) {
            plrs.add(name);
        }

        for (final String s : plrs) {
            this.getLifeMap().put(s, value);
        }
    }

    @Override
    public void setPlayerLives(final ArenaPlayer aPlayer, final int value) {
        this.getLifeMap().put(aPlayer.getName(), value);
    }

    @Override
    public Map<String, Double> timedEnd(final Map<String, Double> scores) {

        for (final ArenaPlayer ap : this.arena.getFighters()) {
            double score = this.getLifeMap().containsKey(ap.getName()) ? this.getLifeMap().get(ap.getName())
                    : 0;
            if (this.arena.isFreeForAll()) {

                if (scores.containsKey(ap.getName())) {
                    scores.put(ap.getName(), scores.get(ap.getName()) + score);
                } else {
                    scores.put(ap.getName(), score);
                }
            } else {
                if (ap.getArenaTeam() == null) {
                    continue;
                }
                if (scores.containsKey(ap.getArenaTeam().getName())) {
                    scores.put(ap.getArenaTeam().getName(),
                            scores.get(ap.getName()) + score);
                } else {
                    scores.put(ap.getArenaTeam().getName(), score);
                }
            }
        }

        return scores;
    }

    @Override
    public void unload(final Player player) {
        this.getLifeMap().remove(player.getName());
    }
}
