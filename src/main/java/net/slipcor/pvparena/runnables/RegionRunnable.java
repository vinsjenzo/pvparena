package net.slipcor.pvparena.runnables;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.loadables.ArenaRegion;
import net.slipcor.pvparena.loadables.ArenaRegion.RegionType;
import org.bukkit.scheduler.BukkitRunnable;

import static java.util.Arrays.asList;

/**
 * <pre>
 * Arena Runnable class "Region"
 * </pre>
 * <p/>
 * An arena timer to commit region specific checks
 *
 * @author slipcor
 * @version v0.9.9
 */

public class RegionRunnable extends BukkitRunnable {
    private final ArenaRegion region;
//	private final static Debug DEBUG = new Debug(49);
//	private int iID;

    /**
     * create a region runnable
     *
     * @param paRegion the region we are running in
     */
    public RegionRunnable(final ArenaRegion paRegion) {
        this.region = paRegion;
        this.region.getArena().getDebugger().i("RegionRunnable constructor: " + paRegion.getRegionName());
    }

    /**
     * the run method, commit arena end
     */
    @Override
    public void run() {
        /*
		 * J - is a join region
		 * I - is a fight in progress?
		 * T - should a region tick be run?
		 * ---------------------------
		 * J I - T
		 * 0 0 - 0 : no join region, no game, no tick
		 * 0 1 - 1 : no join region, game, tick for other region type
		 * 1 0 - 1 : join region! no game! tick so ppl can join!
		 * 1 1 - 1 : join region! game! tick so ppl can join!
		 * /
		if (
			!region.getType().equals(RegionType.JOIN) &&
			!region.getArena().isFightInProgress() &&
			!region.getType().equals(RegionType.WATCH)) {
			Bukkit.getScheduler().cancelTask(iID);
		} else {
			region.tick();
		}*/


        if (this.region.getType() == RegionType.JOIN) {
            // join region
            if (this.region.getArena().isFightInProgress()) {
                if (PVPArena.getInstance().getAgm().allowsJoinInBattle(this.region.getArena())) {
                    // ingame: only tick if allowed
                    this.region.getArena().getDebugger().i("tick 1: " + this.region.getRegionName());
                    this.region.tick();
                } else {
                    this.region.getArena().getDebugger().i("notick 1: " + this.region.getRegionName());
                    // otherwise: no tick! No cancelling for join regions!
                    // Bukkit.getScheduler().cancelTask(iID);
                }
            } else {
                // not running. JOIN!

                this.region.getArena().getDebugger().i("tick 2: " + this.region.getRegionName());
                this.region.tick();
            }
        } else if (asList(RegionType.WATCH, RegionType.LOUNGE).contains(this.region.getType())) {
            // always tick for WATCH & LOUNGE regions!
            this.region.getArena().getDebugger().i("tick 3: " + this.region.getRegionName());
            this.region.tick();
        } else if (this.region.getArena().isFightInProgress()) {
            // if ingame, always tick for other kinds of things!
            //region.getArena().getDebugger().i("tick 4: " + region.getRegionName());
            this.region.tick();
        } else {
            // not ingame; ignore!
            this.region.getArena().getDebugger().i("notick 5: " + this.region.getRegionName());
        }

    }
}
