package net.slipcor.pvparena.modules;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PALocation;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.exceptions.GameplayException;
import net.slipcor.pvparena.loadables.ArenaModule;
import net.slipcor.pvparena.managers.ArenaManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Set;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Module class "StandardLounge"
 * </pre>
 * <p/>
 * Enables joining to lounges instead of the battlefield
 *
 * @author slipcor
 */

public class StandardLounge extends ArenaModule {

    private static final int PRIORITY = 2;

    public StandardLounge() {
        super("StandardLounge");
    }

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public String checkForMissingSpawns(final Set<String> list) {
        // not random! we need teams * 2 (lounge + spawn) + exit + spectator
        debug("parsing not random");
        final Iterator<String> iter = list.iterator();
        int lounges = 0;
        while (iter.hasNext()) {
            final String spawnName = iter.next();
            debug("parsing '{}'", spawnName);
            if (this.arena.isFreeForAll()) {
                if ("lounge".equals(spawnName)) {
                    lounges++;
                }
            } else {
                if (spawnName.endsWith("lounge") && !"lounge".equals(spawnName)) {
                    lounges++;
                }
            }

        }

        int neededCount = this.arena.getTeams().size();

        for (final ArenaTeam team : this.arena.getTeams()) {
            if ("infected".equals(team.getName()) ||
                    "tank".equalsIgnoreCase(team.getName())) {
                neededCount--;
            }
        }
        if (lounges == neededCount) {
            return null;
        }

        return lounges + "/" + this.arena.getTeams().size() + "x lounge";
    }

    @Override
    public boolean handleJoin(Player player) throws GameplayException {
        if (this.arena.isLocked() && !player.hasPermission("pvparena.admin")
                && !(player.hasPermission("pvparena.create") && this.arena.getOwner().equals(player.getName()))) {
            throw new GameplayException(Language.parse(this.arena, MSG.ERROR_DISABLED));
        }

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        if (aPlayer.getArena() != null) {
            debug(aPlayer.getArena(), player, this.getName());
            throw new GameplayException(Language.parse(this.arena,
                    MSG.ERROR_ARENA_ALREADY_PART_OF, ArenaManager.getIndirectArenaName(aPlayer.getArena())));
        }

        if (aPlayer.getArenaClass() == null) {
            String autoClass = this.arena.getArenaConfig().getDefinedString(CFG.READY_AUTOCLASS);
            if(this.arena.getArenaConfig().getBoolean(CFG.USES_PLAYERCLASSES) && this.arena.getClass(player.getName()) != null) {
                autoClass = player.getName();
            }
            if (autoClass != null && this.arena.getClass(autoClass) == null) {
                throw new GameplayException(Language.parse(this.arena, MSG.ERROR_CLASS_NOT_FOUND, "autoClass"));
            }
        }

        return true;
    }

    @Override
    public boolean hasSpawn(final String spawnName) {
        if (this.arena.isFreeForAll()) {
            return spawnName.startsWith("lounge");
        }
        for (final ArenaTeam team : this.arena.getTeams()) {
            if (spawnName.startsWith(team.getName() + "lounge")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void commitJoin(final Player sender, final ArenaTeam team) {
        // standard join --> lounge
        final ArenaPlayer player = ArenaPlayer.parsePlayer(sender.getName());
        player.setLocation(new PALocation(player.get().getLocation()));

        // ArenaPlayer.prepareInventory(arena, ap.get());
        player.setArena(this.arena);
        team.add(player);

        if (this.arena.isFreeForAll()) {
            this.arena.tpPlayerToCoordNameForJoin(player, "lounge", true);
        } else {
            this.arena.tpPlayerToCoordNameForJoin(player, team.getName() + "lounge", true);
        }

        player.setStatus(Status.LOUNGE);
        this.arena.msg(sender, Language.parse(this.arena, CFG.MSG_LOUNGE));
        if (this.arena.isFreeForAll()) {
            this.arena.msg(sender,
                    Language.parse(this.arena, CFG.MSG_YOUJOINED,
                    Integer.toString(team.getTeamMembers().size()),
                    Integer.toString(this.arena.getArenaConfig().getInt(CFG.READY_MAXPLAYERS))
            ));
            this.arena.broadcastExcept(
                    sender,
                    Language.parse(this.arena, CFG.MSG_PLAYERJOINED,
                            sender.getName(),
                            Integer.toString(team.getTeamMembers().size()),
                            Integer.toString(this.arena.getArenaConfig().getInt(CFG.READY_MAXPLAYERS))
                    ));
        } else {

            this.arena.msg(sender,
                    Language.parse(this.arena, CFG.MSG_YOUJOINEDTEAM,
                            team.getColoredName() + ChatColor.COLOR_CHAR + 'r',
                            Integer.toString(team.getTeamMembers().size()),
                            Integer.toString(this.arena.getArenaConfig().getInt(CFG.READY_MAXPLAYERS))
            ));

            this.arena.broadcastExcept(
                    sender,
                    Language.parse(this.arena, CFG.MSG_PLAYERJOINEDTEAM,
                            sender.getName(),
                            team.getColoredName() + ChatColor.COLOR_CHAR + 'r',
                            Integer.toString(team.getTeamMembers().size()),
                            Integer.toString(this.arena.getArenaConfig().getInt(CFG.READY_MAXPLAYERS))
            ));
        }

        if (player.getState() == null) {

            final Arena arena = player.getArena();

            player.createState(player.get());
            ArenaPlayer.backupAndClearInventory(arena, player.get());
            player.dump();


            if (player.getArenaTeam() != null && player.getArenaClass() == null) {
                final String autoClass = arena.getArenaConfig().getDefinedString(CFG.READY_AUTOCLASS);
                if (autoClass != null && arena.getClass(autoClass) != null) {
                    arena.chooseClass(player.get(), null, autoClass);
                }
            }
        } else {
            PVPArena.getInstance().getLogger().warning("Player has a state while joining: " + player.getName());
        }
    }

    @Override
    public void parseJoin(final CommandSender player, final ArenaTeam team) {
        if (this.arena.startRunner != null) {
            this.arena.countDown();
        }
    }
}