package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PACheck;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.managers.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * Arena Goal class "TeamDeathMatch"
 * </pre>
 * <p/>
 * The second Arena Goal. Arena Teams have lives. When every life is lost, the
 * team is teleported to the spectator spawn to watch the rest of the fight.
 *
 * @author slipcor
 */

public class GoalTeamDeathMatch extends AbstractTeamKillGoal {
    public GoalTeamDeathMatch() {
        super("TeamDeathMatch");
    }

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    @Override
    public boolean allowsJoinInBattle() {
        return this.arena.getArenaConfig().getBoolean(CFG.PERMS_JOININBATTLE);
    }

    @Override
    protected double getScore(ArenaTeam team) {
        return this.getTeamLivesCfg() - (this.getLifeMap().getOrDefault(team.getName(), 0));
    }

    @Override
    protected int getTeamLivesCfg() {
        return this.arena.getArenaConfig().getInt(CFG.GOAL_TDM_LIVES);
    }

    @Override
    public PACheck checkPlayerDeath(final PACheck res, final Player player) {
        if (res.getPriority() <= PRIORITY) {
            res.setPriority(this, PRIORITY);
        }
        return res;
    }

    @Override
    public void commitPlayerDeath(final Player respawnPlayer, final boolean doesRespawn,
                                  final String error, final PlayerDeathEvent event) {

        Player killer = respawnPlayer.getKiller();

        if (killer == null || respawnPlayer.equals(respawnPlayer.getKiller())) {
            if (!this.arena.getArenaConfig().getBoolean(CFG.GOAL_TDM_SUICIDESCORE)) {
                this.broadcastSimpleDeathMessage(respawnPlayer, event);
                this.respawnPlayer(respawnPlayer, event);
                final PAGoalEvent gEvent;
                if (doesRespawn) {
                    gEvent = new PAGoalEvent(this.arena, this, "doesRespawn", "playerDeath:" + respawnPlayer.getName());
                } else {
                    gEvent = new PAGoalEvent(this.arena, this, "playerDeath:" + respawnPlayer.getName());
                }
                Bukkit.getPluginManager().callEvent(gEvent);
                return;
            }
            killer = respawnPlayer;
        }

        final ArenaTeam respawnTeam = ArenaPlayer.parsePlayer(respawnPlayer.getName()).getArenaTeam();
        final ArenaTeam killerTeam = ArenaPlayer.parsePlayer(killer.getName()).getArenaTeam();

        if (killerTeam.equals(respawnTeam)) { // suicide
            for (ArenaTeam newKillerTeam : this.arena.getTeams()) {
                if (!newKillerTeam.equals(respawnTeam) && this.reduceLives(this.arena, newKillerTeam, respawnPlayer, event)) {
                    this.makePlayerLose(respawnPlayer, event);
                    return;
                }
            }
        } else if (this.reduceLives(this.arena, killerTeam, respawnPlayer, event)) {
            this.makePlayerLose(respawnPlayer, event);
            return;
        }

        final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, String.format("playerDeath:%s", respawnPlayer.getName()),
                String.format("playerKill:%s:%s", respawnPlayer.getName(), killer.getName()));
        Bukkit.getPluginManager().callEvent(gEvent);

        if (this.getLifeMap().get(killerTeam.getName()) != null) {
            if (this.arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                if (killerTeam.equals(respawnTeam) || !this.arena.getArenaConfig().getBoolean(CFG.GENERAL_SHOWREMAININGLIVES)) {
                    this.broadcastSimpleDeathMessage(respawnPlayer, event);
                } else {
                    this.broadcastDeathMessage(MSG.FIGHT_KILLED_BY_REMAINING_TEAM_FRAGS, respawnPlayer, event, this.getLifeMap().get(killerTeam.getName()));
                }
            }
            this.respawnPlayer(respawnPlayer, event);
        }

    }

    private void respawnPlayer(Player player, PlayerDeathEvent event) {
        final List<ItemStack> returned;

        if (this.arena.getArenaConfig().getBoolean(CFG.PLAYER_DROPSINVENTORY)) {
            returned = InventoryManager.drop(player);
            event.getDrops().clear();
        } else {
            returned = new ArrayList<>(event.getDrops());
        }

        PACheck.handleRespawn(this.arena, ArenaPlayer.parsePlayer(player.getName()), returned);
    }

    private void makePlayerLose(Player respawnPlayer, PlayerDeathEvent event) {
        if (this.arena.getArenaConfig().getBoolean(CFG.PLAYER_PREVENTDEATH)) {
            this.respawnPlayer(respawnPlayer, event);
            ArenaPlayer.parsePlayer(respawnPlayer.getName()).setStatus(Status.LOST);
        }
    }

    /**
     * @param arena the arena this is happening in
     * @param team  the killing team
     * @return true if the player should not respawn but be removed
     */
    private boolean reduceLives(final Arena arena, final ArenaTeam team, final Player respawnPlayer, final EntityDeathEvent event) {
        final int iLives = this.getLifeMap().get(team.getName());

        if (iLives <= 1) {
            for (final ArenaTeam otherTeam : arena.getTeams()) {
                if (otherTeam.equals(team)) {
                    continue;
                }
                this.getLifeMap().remove(otherTeam.getName());
                for (final ArenaPlayer ap : otherTeam.getTeamMembers()) {
                    if (ap.getStatus() == Status.FIGHT) {
                        ap.setStatus(Status.LOST);
                    }
                }
            }
            arena.broadcast(Language.parse(arena,
                    MSG.FIGHT_KILLED_BY,
                    team.colorizePlayer(respawnPlayer)
                            + ChatColor.YELLOW, arena.parseDeathCause(
                            respawnPlayer, event.getEntity()
                                    .getLastDamageCause().getCause(), event
                                    .getEntity().getKiller())));
            PACheck.handleEnd(arena, false);
            return true;
        }

        this.getLifeMap().put(team.getName(), iLives - 1);
        return false;
    }

    @Override
    public void unload(final Player player) {
        if (this.allowsJoinInBattle()) {
            this.arena.hasNotPlayed(ArenaPlayer.parsePlayer(player.getName()));
        }
    }
}
