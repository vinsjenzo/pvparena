package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.listeners.PlayerListener;
import net.slipcor.pvparena.managers.InventoryManager;
import net.slipcor.pvparena.managers.PriorityManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Goal class "TeamLives"
 * </pre>
 * <p/>
 * The second Arena Goal. Arena Teams have lives. When every life is lost, the
 * team is teleported to the spectator spawn to watch the rest of the fight.
 *
 * @author slipcor
 */

public class GoalTeamLives extends AbstractTeamKillGoal {
    public GoalTeamLives() {
        super("TeamLives");
    }

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    @Override
    protected int getScore(ArenaTeam team) {
        return this.getLifeMap().getOrDefault(team.getName(), 0);
    }

    @Override
    protected int getTeamLivesCfg() {
        return this.arena.getArenaConfig().getInt(CFG.GOAL_TLIVES_LIVES);
    }

    @Override
    public Boolean checkPlayerDeath(Player player) {
        final ArenaTeam respawnTeam = ArenaPlayer.parsePlayer(player.getName()).getArenaTeam();

        if (this.getTeamLives(respawnTeam) != null) {
            return true;
        }
        return this.getTeamLives(respawnTeam) > 1;
    }

    @Override
    public void commitPlayerDeath(final Player respawnPlayer, final boolean doesRespawn,
                                  final PlayerDeathEvent event) {
        final PAGoalEvent gEvent;
        if (doesRespawn) {
            gEvent = new PAGoalEvent(this.arena, this, "doesRespawn", "playerDeath:" + respawnPlayer.getName());
        } else {
            gEvent = new PAGoalEvent(this.arena, this, "playerDeath:" + respawnPlayer.getName());
        }
        Bukkit.getPluginManager().callEvent(gEvent);

        final ArenaTeam respawnTeam = ArenaPlayer.parsePlayer(respawnPlayer.getName()).getArenaTeam();
        this.reduceLives(this.arena, respawnTeam);

        if (this.getTeamLives(respawnTeam) != null) {
            if (this.arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                if (this.arena.getArenaConfig().getBoolean(CFG.GENERAL_SHOWREMAININGLIVES)) {
                    this.broadcastDeathMessage(MSG.FIGHT_KILLED_BY_REMAINING_TEAM, respawnPlayer, event,
                            this.getTeamLives(respawnTeam));
                } else {
                    this.broadcastSimpleDeathMessage(respawnPlayer, event);
                }
            }

            final List<ItemStack> returned;

            if (this.arena.getArenaConfig().getBoolean(
                    CFG.PLAYER_DROPSINVENTORY)) {
                returned = InventoryManager.drop(respawnPlayer);
                event.getDrops().clear();
            } else {
                returned = new ArrayList<>(event.getDrops());
            }

            PriorityManager.handleRespawn(this.arena,
                    ArenaPlayer.parsePlayer(respawnPlayer.getName()), returned);

        } else if (this.arena.getArenaConfig().getBoolean(CFG.PLAYER_PREVENTDEATH)) {
            debug(this.arena, respawnPlayer, "faking player death");
            ArenaPlayer.parsePlayer(respawnPlayer.getName()).setStatus(Status.LOST);
            PlayerListener.finallyKillPlayer(this.arena, respawnPlayer, event);
        }
    }

    private void reduceLives(final Arena arena, final ArenaTeam team) {
        final int iLives = this.getTeamLives(team);

        if (iLives <= 1) {
            this.getLifeMap().remove(team.getName());
            for (final ArenaPlayer ap : team.getTeamMembers()) {
                if (ap.getStatus() == Status.FIGHT) {
                    ap.setStatus(Status.LOST);
                }
            }
            PriorityManager.handleEnd(arena, false);
            return;
        }

        this.getLifeMap().put(team.getName(), iLives - 1);
    }

    private Integer getTeamLives(ArenaTeam respawnTeam) {
        return this.getLifeMap().get(respawnTeam.getName());
    }
}
