package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.ArenaClass;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PACheck;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.core.StringParser;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.listeners.PlayerListener;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.managers.InventoryManager;
import net.slipcor.pvparena.managers.TeamManager;
import net.slipcor.pvparena.runnables.EndRunnable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Goal class "PlayerLives"
 * </pre>
 * <p/>
 * The first Arena Goal. Players have lives. When every life is lost, the player
 * is teleported to the spectator spawn to watch the rest of the fight.
 *
 * @author slipcor
 */

public class GoalPlayerLives extends ArenaGoal {
    public GoalPlayerLives() {
        super("PlayerLives");
    }

    private EndRunnable endRunner;

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    private static final int PRIORITY = 2;

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

        final int count = this.getLifeMap().size();

        debug(this.arena, "lives: " + StringParser.joinSet(this.getLifeMap().keySet(), "|"));

        if (count <= 1) {
            res.setPriority(this, PRIORITY); // yep. only one player left. go!
        }
        if (count == 0) {
            res.setError(this, "");
        }

        return res;
    }

    @Override
    public String checkForMissingSpawns(final Set<String> list) {
        if (!this.arena.isFreeForAll()) {
            return this.checkForMissingTeamSpawn(list);
        }
        return this.checkForMissingSpawn(list);
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
    public PACheck checkPlayerDeath(final PACheck res, final Player player) {
        if (res.getPriority() <= PRIORITY) {
            res.setPriority(this, PRIORITY);

            final int pos = this.getLifeMap().get(player.getName());
            debug(this.arena, player, "lives before death: " + pos);
            if (pos <= 1) {
                res.setError(this, "0");
            }
        }
        return res;
    }

    @Override
    public void commitEnd(final boolean force) {
        if (this.endRunner != null) {
            return;
        }
        if (this.arena.realEndRunner != null) {
            debug(this.arena, "[LIVES] already ending");
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
                                    team.getColoredName()), "END");
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
                if (this.arena.realEndRunner == null) {
                    this.endRunner = new EndRunnable(this.arena, this.arena.getArenaConfig().getInt(
                            CFG.TIME_ENDCOUNTDOWN));
                }
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
            return;
        }
        if (doesRespawn) {
            final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "doesRespawn", "playerDeath:" + player.getName());
            Bukkit.getPluginManager().callEvent(gEvent);
        } else {
            final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "playerDeath:" + player.getName());
            Bukkit.getPluginManager().callEvent(gEvent);
        }
        int pos = this.getLifeMap().get(player.getName());
        debug(this.arena, player, "lives before death: " + pos);
        if (pos <= 1) {
            this.getLifeMap().remove(player.getName());
            ArenaPlayer.parsePlayer(player.getName()).setStatus(Status.LOST);
            if (this.arena.getArenaConfig().getBoolean(CFG.PLAYER_PREVENTDEATH)) {
                debug(this.arena, player, "faking player death");
                PlayerListener.finallyKillPlayer(this.arena, player, event);
            }
            // player died => commit death!
            PACheck.handleEnd(this.arena, false);
        } else {
            pos--;
            this.getLifeMap().put(player.getName(), pos);

            if (this.arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                if (this.arena.getArenaConfig().getBoolean(CFG.GENERAL_SHOWREMAININGLIVES)) {
                    this.broadcastDeathMessage(MSG.FIGHT_KILLED_BY_REMAINING, player, event, pos);
                } else {
                    this.broadcastSimpleDeathMessage(player, event);
                }
            }

            final List<ItemStack> returned;

            if (this.arena.getArenaConfig().getBoolean(CFG.PLAYER_DROPSINVENTORY)) {
                returned = InventoryManager.drop(player);
                event.getDrops().clear();
            } else {
                returned = new ArrayList<>();
                returned.addAll(event.getDrops());
            }

            PACheck.handleRespawn(this.arena, ArenaPlayer.parsePlayer(player.getName()), returned);

        }
    }

    @Override
    public void displayInfo(final CommandSender sender) {
        sender.sendMessage("lives: "
                + this.arena.getArenaConfig().getInt(CFG.GOAL_PLIVES_LIVES));
    }

    @Override
    public PACheck getLives(final PACheck res, final ArenaPlayer aPlayer) {
        if (res.getPriority() <= PRIORITY + 1000) {

            if (this.arena.isFreeForAll()) {
                res.setError(
                        this,
                        String.valueOf(this.getLifeMap().getOrDefault(aPlayer.getName(), 0))
                );
            } else {

                if (this.getLifeMap().containsKey(aPlayer.getArenaTeam().getName())) {
                    res.setError(this, String.valueOf(
                            this.getLifeMap().get(aPlayer.getName())));
                } else {

                    int sum = 0;

                    for (final ArenaPlayer player : aPlayer.getArenaTeam().getTeamMembers()) {
                        if (this.getLifeMap().containsKey(player.getName())) {
                            sum += this.getLifeMap().get(player.getName());
                        }
                    }

                    res.setError(
                            this,
                            String.valueOf(sum));
                }
            }


        }
        return res;
    }

    @Override
    public boolean hasSpawn(final String string) {
        if (this.arena.isFreeForAll()) {

            if (this.arena.getArenaConfig().getBoolean(CFG.GENERAL_CLASSSPAWN)) {
                for (final ArenaClass aClass : this.arena.getClasses()) {
                    if (string.toLowerCase().startsWith(
                            aClass.getName().toLowerCase() + "spawn")) {
                        return true;
                    }
                }
            }
            return string.toLowerCase().startsWith("spawn");
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
        }
        return false;
    }

    @Override
    public void initate(final Player player) {
        this.updateLives(player, this.arena.getArenaConfig().getInt(CFG.GOAL_PLIVES_LIVES));
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
                this.updateLives(ap.get(), this.arena.getArenaConfig().getInt(CFG.GOAL_PLIVES_LIVES));
            }
        }
    }

    @Override
    public void reset(final boolean force) {
        this.endRunner = null;
        this.getLifeMap().clear();
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
        if (this.arena.getArenaConfig().getBoolean(CFG.GOAL_FLAGS_WOOLFLAGHEAD)
                && config.get("flagColors") == null) {
            debug(this.arena, "no flagheads defined, adding white and black!");
            config.addDefault("flagColors.red", "WHITE");
            config.addDefault("flagColors.blue", "BLACK");
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
