package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.StringParser;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.runnables.TimedEndRunnable;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.HashSet;
import java.util.Set;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>Arena Goal class "Time"</pre>
 * <p/>
 * Time is ticking ^^
 *
 * @author slipcor
 */

public class GoalTime extends ArenaGoal {

    private TimedEndRunnable ter;

    public GoalTime() {
        super("Time");
    }

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    @Override
    public boolean allowsJoinInBattle() {
        return this.arena.getArenaConfig().getBoolean(CFG.PERMS_JOININBATTLE);
    }

    void commitEnd() {
        if (this.ter != null) {
            this.ter.commit();
        }
    }

    @Override
    public void displayInfo(final CommandSender sender) {
        sender.sendMessage("timer: " + StringParser.colorVar(this.arena.getArenaConfig().getInt(CFG.GOAL_TIME_END)));
    }

    @Override
    public void parseStart() {
        final int timed = this.arena.getArenaConfig().getInt(CFG.GOAL_TIME_END);
        if (timed > 0) {
            debug(this.arena, "arena timing!");
            // initiate autosave timer
            this.ter = new TimedEndRunnable(this.arena, timed, this);
        }
    }

    @Override
    public void reset(final boolean force) {
        if (this.ter != null) {
            this.ter.commit();
            this.ter = null;
        }
    }

    @Override
    public void unload(final Player player) {
        class RunLater implements Runnable {

            @Override
            public void run() {
                GoalTime.this.commitEnd();
            }

        }
        if (this.arena.getFighters().size() < 2) {
            try {
                Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), new RunLater(), 1L);
            } catch (IllegalPluginAccessException ex) {

            }
            return;
        }
        if (this.arena.isFreeForAll()) {
            return;
        }

        final Set<ArenaTeam> teams = new HashSet<>();

        for (final ArenaPlayer aPlayer : this.arena.getFighters()) {
            if (aPlayer.getStatus() == Status.FIGHT) {
                teams.add(aPlayer.getArenaTeam());
                if (teams.size() > 1) {
                    return;
                }
            }

        }
        if (teams.size() < 2) {
            try {
                Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), new RunLater(), 1L);
            } catch (IllegalPluginAccessException ex) {

            }
        }
    }
}
