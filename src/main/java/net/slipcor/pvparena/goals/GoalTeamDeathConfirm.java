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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * Arena Goal class "TeamDeathConfirm"
 * </pre>
 * <p/>
 * Arena Teams need to achieve kills. When a player dies, they drop an item that needs to be
 * collected. First team to collect the needed amount of those items wins!
 *
 * @author slipcor
 */

public class GoalTeamDeathConfirm extends AbstractTeamKillGoal {
    public GoalTeamDeathConfirm() {
        super("TeamDeathConfirm");
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
        return this.arena.getArenaConfig().getInt(CFG.GOAL_TDC_LIVES);
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
    public void commitPlayerDeath(final Player respawnPlayer, final boolean doesRespawn,
                                  final String error, final PlayerDeathEvent event) {

        if (respawnPlayer.getKiller() == null) {
            final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "playerDeath:" + respawnPlayer.getName());
            Bukkit.getPluginManager().callEvent(gEvent);
        } else {
            final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "playerDeath:" + respawnPlayer.getName(),
                    "playerKill:" + respawnPlayer.getName() + ':' + respawnPlayer.getKiller().getName());
            Bukkit.getPluginManager().callEvent(gEvent);
        }


        final ArenaTeam respawnTeam = ArenaPlayer.parsePlayer(respawnPlayer.getName()).getArenaTeam();

        this.drop(respawnPlayer, respawnTeam);

        if (this.arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
            this.broadcastSimpleDeathMessage(respawnPlayer, event);
        }

        final List<ItemStack> returned;
        if (this.arena.getArenaConfig().getBoolean(
                CFG.PLAYER_DROPSINVENTORY)) {
            returned = InventoryManager.drop(respawnPlayer);
            event.getDrops().clear();
        } else {
            returned = new ArrayList<>(event.getDrops());
        }

        PACheck.handleRespawn(this.arena, ArenaPlayer.parsePlayer(respawnPlayer.getName()), returned);
    }

    private void drop(final Player player, final ArenaTeam team) {
        Material material = this.arena.getArenaConfig().getMaterial(CFG.GOAL_TDC_ITEM);
        ItemStack item = new ItemStack(material);

        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(team.getColoredName());
        item.setItemMeta(meta);

        player.getWorld().dropItem(player.getLocation(), item);
    }

    @Override
    public void onPlayerPickUp(final EntityPickupItemEvent event) {
        final ItemStack item = event.getItem().getItemStack();

        final Material check = this.arena.getArenaConfig().getMaterial(CFG.GOAL_TDC_ITEM);

        final ArenaPlayer player = ArenaPlayer.parsePlayer(event.getEntity().getName());

        if (item.getType().equals(check) && item.hasItemMeta()) {
            for (final ArenaTeam team : this.arena.getTeams()) {
                if (item.getItemMeta().getDisplayName().equals(team.getColoredName())) {
                    // it IS an item !!!!

                    event.setCancelled(true);
                    event.getItem().remove();

                    if (team.equals(player.getArenaTeam())) {
                        // denied a kill
                        this.arena.broadcastExcept(event.getEntity(), Language.parse(this.arena, MSG.GOAL_TEAMDEATHCONFIRM_DENIED, player.toString()));
                        this.arena.msg(event.getEntity(), Language.parse(this.arena, MSG.GOAL_TEAMDEATHCONFIRM_YOUDENIED, player.toString()));
                    } else {
                        // scored a kill
                        this.arena.broadcastExcept(event.getEntity(), Language.parse(this.arena, MSG.GOAL_TEAMDEATHCONFIRM_SCORED, player.toString()));
                        this.arena.msg(event.getEntity(), Language.parse(this.arena, MSG.GOAL_TEAMDEATHCONFIRM_YOUSCORED, player.toString()));
                        this.reduceLives(this.arena, team);
                    }
                    return;
                }
            }
        }
    }

    /**
     * @param arena the arena this is happening in
     * @param team  the killing team
     * @return true if the player should not respawn but be removed
     */
    private boolean reduceLives(final Arena arena, final ArenaTeam team) {
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
            PACheck.handleEnd(arena, false);
            return true;
        }
        arena.broadcast(Language.parse(arena, MSG.GOAL_TEAMDEATHCONFIRM_REMAINING, String.valueOf(iLives - 1), team.getColoredName()));

        this.getLifeMap().put(team.getName(), iLives - 1);
        arena.updateScoreboards();
        return false;
    }

    @Override
    public void unload(final Player player) {
        if (this.allowsJoinInBattle()) {
            this.arena.hasNotPlayed(ArenaPlayer.parsePlayer(player.getName()));
        }
    }
}
