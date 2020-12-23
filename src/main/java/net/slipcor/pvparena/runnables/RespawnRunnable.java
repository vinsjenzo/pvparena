package net.slipcor.pvparena.runnables;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.classes.PALocation;
import net.slipcor.pvparena.classes.PASpawn;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.managers.SpawnManager;
import org.apache.commons.lang.Validate;

import java.util.HashSet;
import java.util.Set;

import static net.slipcor.pvparena.config.Debugger.debug;

public class RespawnRunnable implements Runnable {

    private final Arena arena;
    private final ArenaPlayer player;
    private final String coordName;

    public RespawnRunnable(final Arena arena, final ArenaPlayer player, final String coord) {
        Validate.notNull(arena, "Arena cannot be null!");
        debug(arena, player.get(), "RespawnRunnable constructor to spawn " + coord);
        this.arena = arena;
        this.player = player;
        this.coordName = coord;
    }

    @Override
    public void run() {
        if (this.player.get() == null || this.player.getArenaTeam() == null) {
            PVPArena.getInstance().getLogger().warning("player null!");
            return;
        }
        debug(this.arena, "respawning " + this.player.getName() + " to " + this.coordName);

        final PALocation loc = SpawnManager.getSpawnByExactName(this.arena, this.coordName);

        if (loc == null) {
            final Set<PASpawn> spawns = new HashSet<>();
            if (this.arena.getArenaConfig().getBoolean(CFG.GENERAL_CLASSSPAWN)) {
                final String arenaClass = this.player.getArenaClass().getName();
                spawns.addAll(SpawnManager.getPASpawnsStartingWith(this.arena, this.player.getArenaTeam().getName() + arenaClass + "spawn"));
            } else if (this.arena.isFreeForAll()) {
                if ("free".equals(this.player.getArenaTeam().getName())) {
                    spawns.addAll(SpawnManager.getPASpawnsStartingWith(this.arena, "spawn"));
                } else {
                    spawns.addAll(SpawnManager.getPASpawnsStartingWith(this.arena, this.player.getArenaTeam().getName()));
                }
            } else {
                spawns.addAll(SpawnManager.getPASpawnsStartingWith(this.arena, this.player.getArenaTeam().getName() + "spawn"));
            }

            int pos = spawns.size();

            for (final PASpawn spawn : spawns) {
                if (--pos < 0) {
                    this.arena.tpPlayerToCoordName(this.player, spawn.getName());
                    break;
                }
            }
        } else {
            this.arena.tpPlayerToCoordName(this.player, this.coordName);
        }
    }

}
