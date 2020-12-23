package net.slipcor.pvparena.runnables;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.classes.PACheck;
import net.slipcor.pvparena.core.Language.MSG;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

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

        this.arena.setRound(this.arena.getRound() + 1);

        if (this.arena.getRound() >= this.arena.getRoundCount()) {
            debug(this.arena, "rounds done!");

            this.arena.reset(false);
            if (this.arena.realEndRunner != null) {
                this.arena.realEndRunner = null;
            }
            if (this.arena.endRunner != null) {
                this.arena.endRunner.cancel();
                this.arena.endRunner = null;
            }
        } else {
            debug(this.arena, "Starting round #" + this.arena.getRound());

            if (this.arena.realEndRunner != null) {
                this.arena.realEndRunner = null;
            }
            if (this.arena.endRunner != null) {
                this.arena.endRunner.cancel();
                this.arena.endRunner = null;
            }

            Boolean check = PACheck.handleStart(this.arena, null, true);
            if (check == null || !check) {
                return;
            }

            for (final ArenaPlayer ap : this.arena.getFighters()) {
                this.arena.unKillPlayer(ap.get(), ap.get().getLastDamageCause().getCause(),
                        ap.get().getLastDamageCause().getEntity());

                final List<ItemStack> items = new ArrayList<>();

                for (final ItemStack is : ap.get().getInventory().getContents()) {
                    if (is == null) {
                        continue;
                    }
                    items.add(is.clone());
                }
                new InventoryRefillRunnable(this.arena, ap.get(), items);
            }
        }
    }

    @Override
    protected void warn() {
        PVPArena.getInstance().getLogger().warning("EndRunnable not scheduled yet!");
    }
}
