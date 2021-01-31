package net.slipcor.pvparena.runnables;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.core.Language.MSG;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>Arena Runnable class "End"</pre>
 * <p/>
 * An arena timer counting down to the end of an arena
 *
 * @author slipcor
 * @version v0.9.8
 */

public class EndRunnable extends ArenaRunnable {
//	private final static Debugger debug = Debugger.getInstance();

    /**
     * create a timed arena runnable
     *
     * @param arena   the arena we are running in
     * @param seconds the seconds it will run
     */
    public EndRunnable(final Arena arena, final int seconds) {
        super(MSG.TIMER_RESETTING_IN.getNode(), seconds, null, arena, false);
        debug(arena, "EndRunnable constructor");
        if (arena.endRunner != null) {
            arena.endRunner.cancel();
            arena.endRunner = null;
        }
        if (arena.realEndRunner != null) {
            arena.realEndRunner.cancel();
        }
        arena.realEndRunner = this;
    }

    @Override
    protected void commit() {
        debug(this.arena, "EndRunnable commiting");

        this.arena.reset(false);
        if (this.arena.realEndRunner != null) {
            this.arena.realEndRunner = null;
        }
        if (this.arena.endRunner != null) {
            this.arena.endRunner.cancel();
            this.arena.endRunner = null;
        }
    }

    @Override
    protected void warn() {
        PVPArena.getInstance().getLogger().warning("EndRunnable not scheduled yet!");
    }
}
