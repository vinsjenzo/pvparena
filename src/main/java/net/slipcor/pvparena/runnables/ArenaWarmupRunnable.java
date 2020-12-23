package net.slipcor.pvparena.runnables;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.commands.PAG_Join;
import net.slipcor.pvparena.commands.PAG_Spectate;
import net.slipcor.pvparena.core.Language.MSG;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>Arena Runnable class "Warmup"</pre>
 * <p/>
 * An arena timer to count down a warming up player
 *
 * @author slipcor
 * @version v0.10.2
 */

public class ArenaWarmupRunnable extends ArenaRunnable {
    private final ArenaPlayer player;
    private final String teamName;
    private final boolean spectator;
//	private final static Debugger debug = Debugger.getInstance();

    private final Arena wArena;

    /**
     * create a timed arena runnable
     *
     * @param player the player to reset
     */
    public ArenaWarmupRunnable(final Arena arena, final ArenaPlayer player, final String team, final boolean spectator, final int seconds) {
        super(MSG.TIMER_WARMINGUP.getNode(), seconds, player.get(), null, false);
        debug(arena, player.get(), "ArenaWarmupRunnable constructor");
        this.player = player;
        this.teamName = team;
        this.spectator = spectator;
        this.wArena = arena;
    }

    @Override
    protected void commit() {
        debug(this.wArena, this.player.get(), "ArenaWarmupRunnable commiting");
        this.player.setStatus(Status.WARM);
        if (this.spectator) {
            this.wArena.hasNotPlayed(this.player);
            (new PAG_Spectate()).commit(this.wArena, this.player.get(), null);
        } else if (this.teamName == null) {
            this.wArena.hasNotPlayed(this.player);
            (new PAG_Join()).commit(this.wArena, this.player.get(), null);
        } else {
            this.wArena.hasNotPlayed(this.player);
            final String[] args = new String[1];
            args[0] = this.teamName;
            (new PAG_Join()).commit(this.wArena, this.player.get(), args);
        }
    }

    @Override
    protected void warn() {
        PVPArena.getInstance().getLogger().warning("ArenaWarmupRunnable not scheduled yet!");
    }
}