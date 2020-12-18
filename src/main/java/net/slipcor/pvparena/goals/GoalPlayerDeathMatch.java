package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.ArenaClass;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PACheck;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Debug;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.listeners.PlayerListener;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.managers.ArenaManager;
import net.slipcor.pvparena.managers.InventoryManager;
import net.slipcor.pvparena.runnables.EndRunnable;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * <pre>
 * Arena Goal class "PlayerDeathMatch"
 * </pre>
 * <p/>
 * The first Arena Goal. Players have lives. When every life is lost, the player
 * is teleported to the spectator spawn to watch the rest of the fight.
 *
 * @author slipcor
 */

public class GoalPlayerDeathMatch extends ArenaGoal {
    public GoalPlayerDeathMatch() {
        super("PlayerDeathMatch");
        this.debug = new Debug(101);
    }

    private EndRunnable endRunner;

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    private static final int PRIORITY = 3;

    @Override
    public boolean allowsJoinInBattle() {
        return this.arena.getArenaConfig().getBoolean(CFG.PERMS_JOININBATTLE);
    }

    @Override
    public PACheck checkEnd(final PACheck res) {
        if (res.getPriority() > PRIORITY) {
            return res;
        }

        final int count = this.getLifeMap().size();

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
            return null; // teams are handled somewhere else
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
        if (res.getPriority() <= PRIORITY && player.getKiller() != null
                && this.arena.hasPlayer(player.getKiller())) {
            res.setPriority(this, PRIORITY);
        }
        return res;
    }

    @Override
    public void commitEnd(final boolean force) {
        if (this.endRunner != null) {
            return;
        }
        if (this.arena.realEndRunner != null) {
            this.arena.getDebugger().i("[PDM] already ending");
            return;
        }
        final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "");
        Bukkit.getPluginManager().callEvent(gEvent);
        for (final ArenaTeam team : this.arena.getTeams()) {
            for (final ArenaPlayer ap : team.getTeamMembers()) {
                if (ap.getStatus() != Status.FIGHT) {
                    continue;
                }
                ArenaModuleManager.announce(this.arena,
                        Language.parse(this.arena, MSG.PLAYER_HAS_WON, ap.getName()),
                        "END");
                ArenaModuleManager.announce(this.arena,
                        Language.parse(this.arena, MSG.PLAYER_HAS_WON, ap.getName()),
                        "WINNER");

                this.arena.broadcast(Language.parse(this.arena, MSG.PLAYER_HAS_WON, ap.getName()));
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

        if (player.getKiller() == null
                || !this.getLifeMap().containsKey(player.getKiller().getName())
                || player.getPlayer().equals(player.getPlayer().getKiller())) {
            final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "playerKill:" + player.getName() + ':' + player.getName(), "playerDeath:" + player.getName());
            Bukkit.getPluginManager().callEvent(gEvent);

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

            PACheck.handleRespawn(this.arena, ArenaPlayer.parsePlayer(player.getName()), returned);

            if (this.arena.getArenaConfig().getBoolean(CFG.USES_SUICIDEPUNISH)) {
                for (ArenaPlayer ap : this.arena.getFighters()) {
                    if (player.equals(ap.get())) {
                        continue;
                    }
                    if (this.increaseScore(ap.get(), player)) {
                        return;
                    }
                }
            }

            return;
        }
        final Player killer = player.getKiller();
        int iLives = this.getLifeMap().get(killer.getName());
        final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "playerKill:" + killer.getName() + ':' + player.getName(), "playerDeath:" + player.getName());
        Bukkit.getPluginManager().callEvent(gEvent);

        if (this.increaseScore(killer, player)) {
            return;
        }

        if (this.arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
            if (this.arena.getArenaConfig().getBoolean(CFG.GENERAL_SHOWREMAININGLIVES)) {
                this.broadcastDeathMessage(MSG.FIGHT_KILLED_BY_REMAINING_FRAGS, player, event, iLives-1);
            } else {
                this.broadcastSimpleDeathMessage(player, event);
            }
        }

        final List<ItemStack> returned;

        if (this.arena.getArenaConfig().getBoolean(
                CFG.PLAYER_DROPSINVENTORY)) {
            returned = InventoryManager.drop(player);
            event.getDrops().clear();
        } else {
            returned = new ArrayList<>(event.getDrops());
        }

        PACheck.handleRespawn(this.arena, ArenaPlayer.parsePlayer(player.getName()), returned);
    }

    private boolean increaseScore(Player killer, Player killed) {
        int iLives = this.getLifeMap().get(killer.getName());
        this.arena.getDebugger().i("kills to go: " + iLives, killer);
        if (iLives <= 1) {
            // player has won!
            final Set<ArenaPlayer> plrs = new HashSet<>();
            for (final ArenaPlayer ap : this.arena.getFighters()) {
                if (ap.getName().equals(killer.getName())) {
                    continue;
                }
                plrs.add(ap);
            }
            for (final ArenaPlayer ap : plrs) {
                this.getLifeMap().remove(ap.getName());

                ap.setStatus(Status.LOST);
                ap.addLosses();

                if (this.arena.getArenaConfig().getBoolean(CFG.PLAYER_PREVENTDEATH)) {
                    this.arena.getDebugger().i("faking player death", ap.get());
                    PlayerListener.finallyKillPlayer(this.arena, ap.get(), killed.getLastDamageCause());
                }

                if (ArenaManager.checkAndCommit(this.arena, false)) {
                    this.arena.unKillPlayer(killed, killed
                            .getLastDamageCause().getCause(), killer);
                    return true;
                }
            }

            if (this.arena.getArenaConfig().getBoolean(CFG.PLAYER_PREVENTDEATH)) {
                this.arena.getDebugger().i("faking player death", killed);
                PlayerListener.finallyKillPlayer(this.arena, killed, killed.getLastDamageCause());
            }

            PACheck.handleEnd(this.arena, false);
            return true;
        }
        iLives--;
        this.getLifeMap().put(killer.getName(), iLives);
        return false;
    }

    @Override
    public void displayInfo(final CommandSender sender) {
        sender.sendMessage("lives: "
                + this.arena.getArenaConfig().getInt(CFG.GOAL_PDM_LIVES));
    }

    @Override
    public PACheck getLives(final PACheck res, final ArenaPlayer aPlayer) {
        if (res.getPriority() <= PRIORITY + 1000) {
            res.setError(
                    this,
                    String.valueOf(this.arena.getArenaConfig()
                            .getInt(CFG.GOAL_PDM_LIVES) - (this.getLifeMap()
                            .containsKey(aPlayer.getName()) ? this.getLifeMap().get(aPlayer
                            .getName()) : 0)));
        }
        return res;
    }

    @Override
    public boolean hasSpawn(final String string) {

        if (this.arena.getArenaConfig().getBoolean(CFG.GENERAL_CLASSSPAWN)) {
            for (final ArenaClass aClass : this.arena.getClasses()) {
                if (string.toLowerCase().startsWith(
                        aClass.getName().toLowerCase() + "spawn")) {
                    return true;
                }
            }
        }
        return this.arena.isFreeForAll() && string.toLowerCase()
                .startsWith("spawn");
    }

    @Override
    public void initate(final Player player) {
        this.updateLives(player, this.arena.getArenaConfig().getInt(CFG.GOAL_PDM_LIVES));
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public void lateJoin(final Player player) {
        this.initate(player);
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
                this.updateLives(ap.get(), this.arena.getArenaConfig().getInt(CFG.GOAL_PDM_LIVES));
            }
        }
    }

    @Override
    public void reset(final boolean force) {
        this.endRunner = null;
        this.getLifeMap().clear();
    }

    @Override
    public Map<String, Double> timedEnd(final Map<String, Double> scores) {

        for (final ArenaPlayer ap : this.arena.getFighters()) {
            double score = this.arena.getArenaConfig().getInt(CFG.GOAL_PDM_LIVES)
                    - (this.getLifeMap().containsKey(ap.getName()) ? this.getLifeMap()
                    .get(ap.getName()) : 0);
            if (scores.containsKey(ap.getName())) {
                scores.put(ap.getName(), scores.get(ap.getName()) + score);
            } else {
                scores.put(ap.getName(), score);
            }
        }

        return scores;
    }

    @Override
    public void unload(final Player player) {
        this.getLifeMap().remove(player.getName());
        if (this.allowsJoinInBattle()) {
            this.arena.hasNotPlayed(ArenaPlayer.parsePlayer(player.getName()));
        }
    }
}
