package net.slipcor.pvparena.arena;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.runnables.TimedEndRunnable;
import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>Arena Timer</pre>
 * <p/>
 * Time is ticking ^^
 */
public class ArenaTimer {

    protected Arena arena;
    private TimedEndRunnable ter;

    public ArenaTimer(Arena arena) {
        this.arena = arena;
    }

    public void start() {
        final int timed = this.arena.getArenaConfig().getInt(CFG.GENERAL_TIMER);
        if (timed > 0) {
            debug(this.arena, "arena timing!");
            // initiate arena timer
            this.ter = new TimedEndRunnable(this.arena, timed);
        }
    }

    public void stop() {
        if (this.ter == null) {
           debug(this.arena, "timer already stopped");
            return;
        }

        class EndTimerRunner implements Runnable {
            @Override
            public void run() {
                ArenaTimer.this.ter.commit();
            }
        }

        debug(this.arena, "Stopping arena end timer");
        try {
            Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), new EndTimerRunner(), 1L);
        } catch (IllegalPluginAccessException ex) {
            PVPArena.getInstance().getLogger().warning(ex.getMessage());
        }
    }
}
