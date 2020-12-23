package net.slipcor.pvparena.runnables;

import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.core.Config.CFG;
import org.bukkit.Bukkit;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>Arena Runnable class "SpawnCamp"</pre>
 * <p/>
 * An arena timer to punish spawn campers
 *
 * @author slipcor
 * @version v0.9.8
 */

public class SpawnCampRunnable implements Runnable {
    private final Arena arena;
    //	private final static Debugger debug = Debugger.getInstance();
    private int iID;

    /**
     * create a spawn camp runnable
     *
     * @param arena the arena we are running in
     */
    public SpawnCampRunnable(final Arena arena) {
        this.iID = 0;
        this.arena = arena;
        debug(arena, "SpawnCampRunnable constructor");
    }

    /**
     * the run method, commit arena end
     */
    @Override
    public void run() {
        debug(this.arena, "SpawnCampRunnable commiting");
        if (this.arena.isFightInProgress() && this.arena.getArenaConfig().getBoolean(CFG.PROTECT_PUNISH)) {
            this.arena.spawnCampPunish();
        } else {
            // deactivate the auto saving task
            Bukkit.getServer().getScheduler().cancelTask(this.iID);
            this.arena.spawnCampRunnerID = -1;
        }
    }

    public void setId(final int runID) {
        this.iID = runID;
    }
}
