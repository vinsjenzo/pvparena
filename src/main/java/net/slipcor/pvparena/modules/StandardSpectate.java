package net.slipcor.pvparena.modules;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.classes.PALocation;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.exceptions.GameplayException;
import net.slipcor.pvparena.loadables.ArenaModule;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * <pre>
 * Arena Module class "StandardLounge"
 * </pre>
 * <p/>
 * Enables joining to lounges instead of the battlefield
 *
 * @author slipcor
 */

public class StandardSpectate extends ArenaModule {

    public StandardSpectate() {
        super("StandardSpectate");
    }

    private static final int PRIORITY = 2;

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    @Override
    public String checkForMissingSpawns(final Set<String> list) {
        return list.contains("spectator") ? null : "spectator not set";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean handleSpectate(Player player) throws GameplayException {
        final ArenaPlayer arenaPlayer = ArenaPlayer.parsePlayer(player.getName());
        if (arenaPlayer.getArena() != null) {
            throw new GameplayException(Language.parse(MSG.ERROR_ARENA_ALREADY_PART_OF, arenaPlayer.getArena().getName()));
        }

        return true;
    }

    @Override
    public void commitSpectate(final Player player) {
        // standard join --> lounge
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        aPlayer.setLocation(new PALocation(player.getLocation()));

        aPlayer.setArena(this.arena);
        aPlayer.setStatus(Status.WATCH);

        this.arena.tpPlayerToCoordNameForJoin(aPlayer, "spectator", true);
        this.arena.msg(player, Language.parse(this.arena, MSG.NOTICE_WELCOME_SPECTATOR));

        if (aPlayer.getState() == null) {

            final Arena arena = aPlayer.getArena();

            aPlayer.createState(player);
            ArenaPlayer.backupAndClearInventory(arena, player);
            aPlayer.dump();


            if (aPlayer.getArenaTeam() != null && aPlayer.getArenaClass() == null) {
                String autoClass = arena.getArenaConfig().getDefinedString(CFG.READY_AUTOCLASS);
                if(arena.getArenaConfig().getBoolean(CFG.USES_PLAYERCLASSES) && arena.getClass(player.getName()) != null) {
                    autoClass = player.getName();
                }

                if (autoClass != null && arena.getClass(autoClass) != null) {
                    arena.chooseClass(player, null, autoClass);
                }
            }
        }
    }

    @Override
    public boolean hasSpawn(final String string) {
        return "spectator".equalsIgnoreCase(string);
    }
}