package net.slipcor.pvparena.runnables;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.goals.GoalTime;
import org.bukkit.Bukkit;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>Arena Runnable class "TimedEnd"</pre>
 * <p/>
 * An arena timer to end the arena match after a certain amount of time
 *
 * @author slipcor
 * @version v0.9.8
 */

public class TimedEndRunnable extends ArenaRunnable {
    //private static final Debugger debug = Debugger.getInstance();
    private final GoalTime goal;

    /**
     * create a timed arena runnable
     *
     * @param arena    the arena we are running in
     * @param goalTime the ArenaGoal
     */
    public TimedEndRunnable(final Arena arena, final int seconds, final GoalTime goalTime) {
        super(MSG.TIMER_ENDING_IN.getNode(), seconds, null, arena, false);
        debug(arena, "TimedEndRunnable constructor");
        arena.endRunner = this;
        this.goal = goalTime;
    }

    @Override
    public void commit() {
        debug(this.arena, "TimedEndRunnable commiting");
        final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this.goal, "");
        Bukkit.getPluginManager().callEvent(gEvent);
        if (this.arena.isFightInProgress()) {
            PVPArena.getInstance().getAgm().timedEnd(this.arena);
        }
        this.arena.endRunner = null;
        if (this.arena.realEndRunner != null) {
            this.arena.realEndRunner.cancel();
            this.arena.realEndRunner = null;
        }
    }

    @Override
    protected void warn() {
        PVPArena.getInstance().getLogger().warning("TimedEndRunnable not scheduled yet!");
    }
}
