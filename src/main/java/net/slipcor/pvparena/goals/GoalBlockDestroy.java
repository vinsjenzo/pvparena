package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaClass;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PABlock;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.classes.PACheck;
import net.slipcor.pvparena.commands.CommandTree;
import net.slipcor.pvparena.commands.PAA_Region;
import net.slipcor.pvparena.core.ColorUtils;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.loadables.ArenaRegion;
import net.slipcor.pvparena.loadables.ArenaRegion.RegionType;
import net.slipcor.pvparena.managers.SpawnManager;
import net.slipcor.pvparena.managers.TeamManager;
import net.slipcor.pvparena.runnables.EndRunnable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Goal class "BlockDestroy"
 * </pre>
 * <p/>
 * Win by breaking the other team's block(s).
 *
 * @author slipcor
 */

public class GoalBlockDestroy extends ArenaGoal implements Listener {

    public GoalBlockDestroy() {
        super("BlockDestroy");
    }

    private String blockTeamName = "";

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    private static final int PRIORITY = 9;

    @Override
    public boolean allowsJoinInBattle() {
        return this.arena.getArenaConfig().getBoolean(CFG.PERMS_JOININBATTLE);
    }

    @Override
    public PACheck checkCommand(final PACheck res, final String string) {
        if (res.getPriority() > PRIORITY) {
            return res;
        }

        if ("blocktype".equalsIgnoreCase(string)) {
            res.setPriority(this, PRIORITY);
        }

        for (final ArenaTeam team : this.arena.getTeams()) {
            final String sTeam = team.getName();
            if (string.contains(sTeam + "block")) {
                res.setPriority(this, PRIORITY);
            }
        }

        return res;
    }

    @Override
    public List<String> getMain() {
        List<String> result = new ArrayList<>();
        if (this.arena != null) {
            result.add("blocktype");
            for (final ArenaTeam team : this.arena.getTeams()) {
                final String sTeam = team.getName();
                result.add(sTeam + "block");
            }
        }
        return result;
    }

    @Override
    public CommandTree<String> getSubs(final Arena arena) {
        final CommandTree<String> result = new CommandTree<>(null);
        result.define(new String[]{"{Material}"});
        return result;
    }

    @Override
    public PACheck checkEnd(final PACheck res) {

        if (res.getPriority() > PRIORITY) {
            return res;
        }

        final int count = TeamManager.countActiveTeams(this.arena);

        if (count == 1) {
            res.setPriority(this, PRIORITY); // yep. only one team left. go!
        } else if (count == 0) {
            debug(this.arena, "No teams playing!");
        }

        return res;
    }

    @Override
    public String checkForMissingSpawns(final Set<String> list) {
        final String team = this.checkForMissingTeamSpawn(list);
        if (team != null) {
            return team;
        }
        return this.checkForMissingTeamCustom(list, "block");
    }

    @Override
    public PACheck checkJoin(final CommandSender sender, final PACheck res, final String[] args) {
        if (res.getPriority() >= PRIORITY) {
            return res;
        }

        final int maxPlayers = this.arena.getArenaConfig().getInt(CFG.READY_MAXPLAYERS);
        final int maxTeamPlayers = this.arena.getArenaConfig().getInt(
                CFG.READY_MAXTEAMPLAYERS);

        if (maxPlayers > 0 && this.arena.getFighters().size() >= maxPlayers) {
            res.setError(this, Language.parse(this.arena, MSG.ERROR_JOIN_ARENA_FULL));
            return res;
        }

        if (args == null || args.length < 1) {
            return res;
        }

        if (!this.arena.isFreeForAll()) {
            final ArenaTeam team = this.arena.getTeam(args[0]);

            if (team != null && maxTeamPlayers > 0
                    && team.getTeamMembers().size() >= maxTeamPlayers) {
                res.setError(this, Language.parse(this.arena, MSG.ERROR_JOIN_TEAM_FULL));
                return res;
            }
        }

        res.setPriority(this, PRIORITY);
        return res;
    }

    @Override
    public PACheck checkSetBlock(final PACheck res, final Player player, final Block block) {

        if (res.getPriority() > PRIORITY
                || !PAA_Region.activeSelections.containsKey(player.getName())) {
            return res;
        }
        if (block == null
                || !block
                .getType()
                .name()
                .equals(this.arena.getArenaConfig().getString(
                        CFG.GOAL_BLOCKDESTROY_BLOCKTYPE))) {
            return res;
        }

        if (!PVPArena.hasAdminPerms(player)
                && !PVPArena.hasCreatePerms(player, this.arena)) {
            return res;
        }
        res.setPriority(this, PRIORITY); // success :)

        return res;
    }

    private void commit(final Arena arena, final String sTeam) {
        debug(arena, "[BD] checking end: " + sTeam);
        debug(arena, "win: " + false);

        for (final ArenaTeam team : arena.getTeams()) {
            if (!team.getName().equals(sTeam)) {
                /*
				team is sTeam and win
				team is not sTeam and not win
				*/
                continue;
            }
            for (final ArenaPlayer ap : team.getTeamMembers()) {
                if (ap.getStatus() == Status.FIGHT || ap.getStatus() == Status.DEAD) {
                    ap.addLosses();
					/*
					arena.removePlayer(ap.get(), CFG.TP_LOSE.toString(),
							true, false);*/

                    ap.setStatus(Status.LOST);

                    //ap.setTelePass(false);
                }
            }
        }
		/*
		if (!win && getLifeMap().size() > 1) {
			return; // if not a win trigger AND more than one team left. out!
		}
		
		for (ArenaTeam team : arena.getTeams()) {
			for (ArenaPlayer ap : team.getTeamMembers()) {
				if (!ap.getStatus().equals(Status.FIGHT)) {
					continue;
				}
				winteam = team.getName();
				break;
			}
		}

		if (arena.getTeam(winteam) != null) {

			ArenaModuleManager
					.announce(
							arena,
							Language.parse(arena, MSG.TEAM_HAS_WON,
									arena.getTeam(winteam).getColor()
											+ winteam + ChatColor.YELLOW),
							"WINNER");
			arena.broadcast(Language.parse(arena, MSG.TEAM_HAS_WON,
					arena.getTeam(winteam).getColor() + winteam
							+ ChatColor.YELLOW));
		}

		getLifeMap().clear();
		new EndRunnable(arena, arena.getArenaConfig().getInt(
				CFG.TIME_ENDCOUNTDOWN));
				*/
        PACheck.handleEnd(arena, false);
    }

    @Override
    public void commitCommand(final CommandSender sender, final String[] args) {
        if ("blocktype".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                this.arena.msg(
                        sender,
                        Language.parse(this.arena, MSG.ERROR_INVALID_ARGUMENT_COUNT,
                                String.valueOf(args.length), "2"));
                return;
            }

            final Material mat = Material.getMaterial(args[1].toUpperCase());

            if (mat == null) {
                this.arena.msg(sender,
                        Language.parse(this.arena, MSG.ERROR_MAT_NOT_FOUND, args[1]));
                return;
            }

            this.arena.getArenaConfig().set(CFG.GOAL_BLOCKDESTROY_BLOCKTYPE,
                    mat.name());
            this.arena.getArenaConfig().save();
            this.arena.msg(sender, Language.parse(this.arena, MSG.GOAL_BLOCKDESTROY_TYPESET,
                    CFG.GOAL_BLOCKDESTROY_BLOCKTYPE.toString()));

        } else if (args[0].contains("block")) {
            for (final ArenaTeam team : this.arena.getTeams()) {
                final String sTeam = team.getName();
                if (args[0].contains(sTeam + "block")) {
                    this.blockTeamName = args[0];
                    PAA_Region.activeSelections.put(sender.getName(), this.arena);

                    this.arena.msg(sender, Language.parse(this.arena,
                            MSG.GOAL_BLOCKDESTROY_TOSET, this.blockTeamName));
                }
            }
        }
    }

    @Override
    public void commitEnd(final boolean force) {
        if (this.arena.realEndRunner != null) {
            debug(this.arena, "[BD] already ending");
            return;
        }
        debug(this.arena, "[BD]");

        final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "");
        Bukkit.getPluginManager().callEvent(gEvent);
        ArenaTeam aTeam = null;

        for (final ArenaTeam team : this.arena.getTeams()) {
            for (final ArenaPlayer ap : team.getTeamMembers()) {
                if (ap.getStatus() == Status.FIGHT) {
                    aTeam = team;
                    break;
                }
            }
        }

        if (aTeam != null && !force) {
            ArenaModuleManager.announce(
                    this.arena,
                    Language.parse(this.arena, MSG.TEAM_HAS_WON, aTeam.getColor()
                            + aTeam.getName() + ChatColor.YELLOW), "END");

            ArenaModuleManager.announce(
                    this.arena,
                    Language.parse(this.arena, MSG.TEAM_HAS_WON, aTeam.getColor()
                            + aTeam.getName() + ChatColor.YELLOW), "WINNER");
            this.arena.broadcast(Language.parse(this.arena, MSG.TEAM_HAS_WON, aTeam.getColor()
                    + aTeam.getName() + ChatColor.YELLOW));
        }

        if (ArenaModuleManager.commitEnd(this.arena, aTeam)) {
            return;
        }
        new EndRunnable(this.arena, this.arena.getArenaConfig().getInt(
                CFG.TIME_ENDCOUNTDOWN));
    }

    @Override
    public boolean commitSetFlag(final Player player, final Block block) {

        debug(this.arena, player, "trying to set a block");

        // command : /pa redblock1
        // location: red1block:

        SpawnManager.setBlock(this.arena, new PABlockLocation(block.getLocation()),
                this.blockTeamName);

        this.arena.msg(player,
                Language.parse(this.arena, MSG.GOAL_BLOCKDESTROY_SET, this.blockTeamName));

        PAA_Region.activeSelections.remove(player.getName());
        this.blockTeamName = "";

        return true;
    }

    @Override
    public void commitStart() {

    }

    @Override
    public void configParse(final YamlConfiguration config) {
        Bukkit.getPluginManager().registerEvents(this, PVPArena.getInstance());
    }

    @Override
    public PACheck getLives(final PACheck res, final ArenaPlayer aPlayer) {
        if (res.getPriority() <= PRIORITY + 1000) {
            res.setError(
                    this,
                    String.valueOf(this.getLifeMap().getOrDefault(aPlayer.getArenaTeam().getName(), 0))
            );
        }
        return res;
    }

    @Override
    public void displayInfo(final CommandSender sender) {
        sender.sendMessage("block type: " +
                this.arena.getArenaConfig().getString(CFG.GOAL_BLOCKDESTROY_BLOCKTYPE));
        sender.sendMessage("lives: " +
                this.arena.getArenaConfig().getInt(CFG.GOAL_BLOCKDESTROY_LIVES));
    }

    @Override
    public boolean hasSpawn(final String string) {
        for (final String teamName : this.arena.getTeamNames()) {
            if (string.toLowerCase().equals(teamName.toLowerCase() + "block")) {
                return true;
            }
            if (string.toLowerCase().startsWith(
                    teamName.toLowerCase() + "spawn")) {
                return true;
            }

            if (this.arena.getArenaConfig().getBoolean(CFG.GENERAL_CLASSSPAWN)) {
                for (final ArenaClass aClass : this.arena.getClasses()) {
                    if (string.toLowerCase().startsWith(teamName.toLowerCase() +
                            aClass.getName().toLowerCase() + "spawn")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void initate(final Player player) {
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        final ArenaTeam team = aPlayer.getArenaTeam();
        if (!this.getLifeMap().containsKey(team.getName())) {
            this.getLifeMap().put(aPlayer.getArenaTeam().getName(), this.arena.getArenaConfig()
                    .getInt(CFG.GOAL_BLOCKDESTROY_LIVES));

            final Set<PABlockLocation> blocks = SpawnManager.getBlocksContaining(this.arena, "block");

            for (final PABlockLocation block : blocks) {
                this.takeBlock(team.getColor(), block);
            }
        }
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public void parseStart() {
        this.getLifeMap().clear();
        for (final ArenaTeam team : this.arena.getTeams()) {
            if (!team.getTeamMembers().isEmpty()) {
                debug(this.arena, "adding team " + team.getName());
                // team is active
                this.getLifeMap().put(
                        team.getName(),
                        this.arena.getArenaConfig().getInt(
                                CFG.GOAL_BLOCKDESTROY_LIVES, 1));
            }
            final Set<PABlockLocation> blocks = SpawnManager.getBlocksContaining(this.arena, "block");

            for (final PABlockLocation block : blocks) {
                this.takeBlock(team.getColor(), block);
            }
        }
    }

    private void reduceLivesCheckEndAndCommit(final Arena arena, final String team) {

        debug(arena, "reducing lives of team " + team);
        if (!this.getLifeMap().containsKey(team)) {
            return;
        }
        final int count = this.getLifeMap().get(team) - 1;
        if (count > 0) {
            this.getLifeMap().put(team, count);
        } else {
            this.getLifeMap().remove(team);
            this.commit(arena, team);
        }
    }

    @Override
    public void reset(final boolean force) {
        this.getLifeMap().clear();
    }

    @Override
    public void setDefaults(final YamlConfiguration config) {
        if (this.arena.isFreeForAll()) {
            return;
        }

        if (config.get("teams.free") != null) {
            config.set("teams", null);
        }
        if (config.get("teams") == null) {
            debug(this.arena, "no teams defined, adding custom red and blue!");
            config.addDefault("teams.red", ChatColor.RED.name());
            config.addDefault("teams.blue", ChatColor.BLUE.name());
        }
    }

    /**
     * take/reset an arena block
     *
     * @param blockColor      the teamcolor to reset
     * @param paBlockLocation the location to take/reset
     */
    void takeBlock(final ChatColor blockColor, final PABlockLocation paBlockLocation) {
        if (paBlockLocation == null) {
            return;
        }
        Material blockDestroyType = Material.valueOf(this.arena.getArenaConfig().getString(CFG.GOAL_BLOCKDESTROY_BLOCKTYPE));
        if (ColorUtils.isColorableMaterial(blockDestroyType)) {
            paBlockLocation.toLocation()
                    .getBlock()
                    .setType(ColorUtils.getColoredMaterialFromChatColor(blockColor, blockDestroyType));
        } else {
            paBlockLocation.toLocation()
                    .getBlock()
                    .setType(
                            Material.valueOf(
                                    this.arena.getArenaConfig().getString(
                                            CFG.GOAL_BLOCKDESTROY_BLOCKTYPE)));
        }
    }

    @Override
    public Map<String, Double> timedEnd(final Map<String, Double> scores) {

        for (final ArenaTeam team : this.arena.getTeams()) {
            double score = this.getLifeMap().containsKey(team.getName()) ? this.getLifeMap()
                    .get(team.getName()) : 0;
            if (scores.containsKey(team.getName())) {
                scores.put(team.getName(), scores.get(team.getName()) + score);
            } else {
                scores.put(team.getName(), score);
            }
        }

        return scores;
    }

    @Override
    public void unload(final Player player) {
        this.disconnect(ArenaPlayer.parsePlayer(player.getName()));
        if (this.allowsJoinInBattle()) {
            this.arena.hasNotPlayed(ArenaPlayer.parsePlayer(player.getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        final Material blockToBreak = this.arena.getArenaConfig().getMaterial(CFG.GOAL_BLOCKDESTROY_BLOCKTYPE);
        final Material brokenBlock = event.getBlock().getType();
        if (!this.arena.hasPlayer(event.getPlayer()) || !ColorUtils.isSubType(brokenBlock, blockToBreak)) {
            debug(this.arena, player, "block destroy, ignoring");
            debug(this.arena, player, String.valueOf(this.arena.hasPlayer(event.getPlayer())));
            debug(this.arena, player, event.getBlock().getType().name());
            return;
        }

        if (!this.arena.isFightInProgress()) {
            event.setCancelled(true);
            return;
        }

        final Block block = event.getBlock();

        debug(this.arena, player, "block destroy!");

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        final ArenaTeam pTeam = aPlayer.getArenaTeam();
        if (pTeam == null) {
            return;
        }

        Vector vBlock = null;
        for (final ArenaTeam team : this.arena.getTeams()) {
            final String blockTeam = team.getName();

            if (team.getTeamMembers().size() < 1
                    && !"touchdown".equals(team.getName())) {
                debug(this.arena, player, "size!OUT! ");
                continue; // dont check for inactive teams
            }

            debug(this.arena, player, "checking for block of team " + blockTeam);
            Vector vLoc = block.getLocation().toVector();
            debug(this.arena, player, "block: " + vLoc);
            if (!SpawnManager.getBlocksStartingWith(this.arena, blockTeam + "block").isEmpty()) {
                vBlock = SpawnManager
                        .getBlockNearest(
                                SpawnManager.getBlocksStartingWith(this.arena, blockTeam
                                        + "block"),
                                new PABlockLocation(player.getLocation()))
                        .toLocation().toVector();
            }
            if (vBlock != null && vLoc.distance(vBlock) < 2) {

                // ///////

                if (blockTeam.equals(pTeam.getName())) {
                    debug(this.arena, player, "is own team! cancel and OUT! ");
                    event.setCancelled(true);
                    break;
                }
                PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "trigger:" + player.getName());
                Bukkit.getPluginManager().callEvent(gEvent);
                final String sTeam = pTeam.getName();

                try {
                    this.arena.broadcast(Language.parse(this.arena, MSG.GOAL_BLOCKDESTROY_SCORE,
                            this.arena.getTeam(sTeam).colorizePlayer(player)
                                    + ChatColor.YELLOW, this.arena
                                    .getTeam(blockTeam).getColoredName()
                                    + ChatColor.YELLOW, String
                                    .valueOf(this.getLifeMap().get(blockTeam) - 1)));
                } catch (final Exception e) {
                    Bukkit.getLogger().severe(
                            "[PVP Arena] team unknown/no lives: " + blockTeam);
                    e.printStackTrace();
                }


                gEvent = new PAGoalEvent(this.arena, this,
                        "score:" + player.getName() + ':' + aPlayer.getArenaTeam().getName() + ":1");
                Bukkit.getPluginManager().callEvent(gEvent);
                class RunLater implements Runnable {
                    final ChatColor localColor;
                    final PABlockLocation localLoc;

                    RunLater(final ChatColor color, final PABlockLocation loc) {
                        this.localColor = color;
                        this.localLoc = loc;
                    }

                    @Override
                    public void run() {
                        GoalBlockDestroy.this.takeBlock(this.localColor, this.localLoc);
                    }
                }

                if (this.getLifeMap().containsKey(blockTeam)
                        && this.getLifeMap().get(blockTeam) > SpawnManager.getBlocksStartingWith(this.arena, blockTeam + "block").size()) {

                    Bukkit.getScheduler().runTaskLater(
                            PVPArena.getInstance(),
                            new RunLater(
                                    this.arena.getTeam(blockTeam).getColor(),
                                    new PABlockLocation(event.getBlock().getLocation())), 5L);
                }
                this.reduceLivesCheckEndAndCommit(this.arena, blockTeam);

                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent event) {
        if (this.arena == null) {
            return;
        }

        boolean contains = false;

        for (final ArenaRegion region : this.arena.getRegionsByType(RegionType.BATTLE)) {
            if (region.getShape().contains(new PABlockLocation(event.getLocation()))) {
                contains = true;
                break;
            }
        }

        if (!contains) {
            return;
        }

        final Set<PABlock> blocks = SpawnManager.getPABlocksContaining(this.arena, "block");

        //final Set<PABlockLocation>

        for (final Block b : event.blockList()) {
            final PABlockLocation loc = new PABlockLocation(b.getLocation());
            for (final PABlock pb : blocks) {
                if (pb.getLocation().getDistanceSquared(loc) < 1) {
                    final String blockTeam = pb.getName().split("block")[0];

                    try {
                        this.arena.broadcast(Language.parse(this.arena, MSG.GOAL_BLOCKDESTROY_SCORE,
                                Language.parse(this.arena, MSG.DEATHCAUSE_BLOCK_EXPLOSION)
                                        + ChatColor.YELLOW, this.arena
                                        .getTeam(blockTeam).getColoredName()
                                        + ChatColor.YELLOW, String
                                        .valueOf(this.getLifeMap().get(blockTeam) - 1)));
                    } catch (final Exception e) {
                        Bukkit.getLogger().severe(
                                "[PVP Arena] team unknown/no lives: " + blockTeam);
                        e.printStackTrace();
                    }
                    this.takeBlock(this.arena.getTeam(blockTeam).getColor(), pb.getLocation());

                    reduceLivesCheckEndAndCommit(this.arena, blockTeam);
                    break;
                }
            }
        }
    }
}
