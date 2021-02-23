package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.commands.PAA_Region;
import net.slipcor.pvparena.core.ColorUtils;
import net.slipcor.pvparena.core.Config;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.core.StringParser;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.managers.SpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Optional.ofNullable;
import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Goal class "PhysicalFlags"
 * </pre>
 * <p/>
 * Capture flags by breaking them, bring them home, get points, win.
 *
 * @author slipcor
 */

public class GoalPhysicalFlags extends AbstractFlagGoal implements Listener {
    private Map<String, BlockData> flagDataMap;

    public GoalPhysicalFlags() {
        super("PhysicalFlags");
    }

    @Override
    protected CFG getFlagTypeCfg() {
        return CFG.GOAL_PFLAGS_FLAGTYPE;
    }

    @Override
    protected CFG getFlagEffectCfg() {
        return CFG.GOAL_PFLAGS_FLAGEFFECT;
    }

    @Override
    protected boolean hasWoolHead() {
        return this.arena.getArenaConfig().getBoolean(CFG.GOAL_PFLAGS_WOOLFLAGHEAD);
    }

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    /**
     * hook into an interacting player
     *
     * @param player the interacting player
     * @param block  the block being clicked
     * @return true if event has been handled
     */
    @Override
    public boolean checkInteract(final Player player, final Block block) {
        if (block == null) {
            return false;
        }
        debug(this.arena, player, "checking interact");

        Material flagType = this.arena.getArenaConfig().getMaterial(CFG.GOAL_PFLAGS_FLAGTYPE);
        if (!ColorUtils.isSubType(block.getType(), flagType)) {
            debug(this.arena, player, "block, but not flag");
            return false;
        }
        debug(this.arena, player, "flag click!");

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        if (this.getFlagMap().containsValue(player.getName())) {
            debug(this.arena, player, "player " + player.getName() + " has got a flag");

            final Vector vLoc = block.getLocation().toVector();
            final String sTeam = aPlayer.getArenaTeam().getName();
            debug(this.arena, player, "block: " + vLoc);
            Vector vFlag = null;
            if (this.getTeamFlagLoc(sTeam) != null) {
                vFlag = this.getTeamFlagLoc(sTeam).toLocation().toVector();
            } else {
                debug(this.arena, player, sTeam + "flag = null");
            }

            debug(this.arena, player, "player is in the team " + sTeam);
            if (vFlag != null && vLoc.distance(vFlag) < 2) {

                debug(this.arena, player, "player is at his flag");

                if (this.getFlagMap().containsKey(sTeam) || this.getFlagMap().containsKey(TOUCHDOWN)) {
                    debug(this.arena, player, "the flag of the own team is taken!");

                    if (this.arena.getArenaConfig().getBoolean(CFG.GOAL_PFLAGS_MUSTBESAFE)
                            && !this.getFlagMap().containsKey(TOUCHDOWN)) {
                        debug(this.arena, player, "cancelling");

                        this.arena.msg(player, Language.parse(this.arena, MSG.GOAL_FLAGS_NOTSAFE));
                        return false;
                    }
                }

                String flagTeam = this.getHeldFlagTeam(player);

                debug(this.arena, player, "the flag belongs to team " + flagTeam);

                ItemStack mainHandItem = player.getInventory().getItemInMainHand();
                if (!ColorUtils.isSubType(mainHandItem.getType(), flagType)) {
                    debug(this.arena, player, "player " + player.getName() + " is not holding the flag");
                    this.arena.msg(player, Language.parse(this.arena, MSG.GOAL_PHYSICALFLAGS_HOLDFLAG));
                    return false;
                }

                player.getInventory().remove(mainHandItem);
                player.updateInventory();

                try {
                    if (TOUCHDOWN.equals(flagTeam)) {
                        this.arena.broadcast(Language.parse(this.arena,
                                MSG.GOAL_FLAGS_TOUCHHOME, this.arena.getTeam(sTeam)
                                        .colorizePlayer(player)
                                        + ChatColor.YELLOW, String
                                        .valueOf(this.getLifeMap().get(aPlayer
                                                .getArenaTeam().getName()) - 1)));
                    } else {
                        this.arena.broadcast(Language.parse(this.arena,
                                MSG.GOAL_FLAGS_BROUGHTHOME, this.arena
                                        .getTeam(sTeam).colorizePlayer(player)
                                        + ChatColor.YELLOW,
                                this.arena.getTeam(flagTeam).getColoredName()
                                        + ChatColor.YELLOW, String
                                        .valueOf(this.getLifeMap().get(flagTeam) - 1)));
                    }
                    this.getFlagMap().remove(flagTeam);
                } catch (final Exception e) {
                    Bukkit.getLogger().severe(
                            "[PVP Arena] team unknown/no lives: " + flagTeam);
                    e.printStackTrace();
                }
                if (TOUCHDOWN.equals(flagTeam)) {
                    this.releaseFlag(TOUCHDOWN);
                } else {
                    this.releaseFlag(flagTeam);
                }
                this.removeEffects(player);
                if (this.hasWoolHead()) {
                    player.getInventory().setHelmet(new ItemStack(Material.AIR, 1));
                } else {
                    if (this.getHeadGearMap().get(player.getName()) == null) {
                        player.getInventory().setHelmet(this.getHeadGearMap().get(player.getName()).clone());
                        this.getHeadGearMap().remove(player.getName());
                    }
                }

                flagTeam = TOUCHDOWN.equals(flagTeam) ? flagTeam + ':' + aPlayer.getArenaTeam().getName() : flagTeam;

                this.reduceLivesCheckEndAndCommit(this.arena, flagTeam);

                final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "trigger:" + player.getName());
                Bukkit.getPluginManager().callEvent(gEvent);

                return true;
            }
        }

        return false;
    }

    @Override
    public void commitInteract(final Player player, final Block clickedBlock) {}

    @Override
    protected void commit(final Arena arena, final String sTeam, final boolean win) {
        super.commit(arena, sTeam, win);
        this.getFlagDataMap().clear();
    }

    @Override
    public boolean commitSetFlag(final Player player, final Block block) {

        debug(this.arena, player, "trying to set a flag");

        // command : /pa redflag1
        // location: red1flag:

        SpawnManager.setBlock(this.arena, new PABlockLocation(block.getLocation()), this.flagName);

        this.arena.msg(player, Language.parse(this.arena, MSG.GOAL_FLAGS_SET, this.flagName));

        PAA_Region.activeSelections.remove(player.getName());
        this.flagName = "";

        return true;
    }

    @Override
    public void disconnect(final ArenaPlayer aPlayer) {
        if (this.getFlagMap().isEmpty()) {
            return;
        }
        final String sTeam = this.getHeldFlagTeam(aPlayer.get());
        final ArenaTeam flagTeam = this.arena.getTeam(sTeam);

        if (flagTeam == null) {
            if (sTeam != null) {
                this.arena.broadcast(Language.parse(this.arena, MSG.GOAL_FLAGS_DROPPEDTOUCH, aPlayer
                        .getArenaTeam().getColorCodeString()
                        + aPlayer.getName()
                        + ChatColor.YELLOW));

                this.getFlagMap().remove(TOUCHDOWN);
                if (this.getHeadGearMap() != null && this.getHeadGearMap().get(aPlayer.getName()) != null) {
                    if (aPlayer.get() != null) {
                        aPlayer.get().getInventory()
                                .setHelmet(this.getHeadGearMap().get(aPlayer.getName()).clone());
                    }
                    this.getHeadGearMap().remove(aPlayer.getName());
                }

                this.releaseFlag(TOUCHDOWN);
            }
        } else {
            this.arena.broadcast(Language.parse(this.arena, MSG.GOAL_FLAGS_DROPPED, aPlayer
                    .getArenaTeam().getColorCodeString()
                    + aPlayer.getName()
                    + ChatColor.YELLOW, flagTeam.getName() + ChatColor.YELLOW));
            this.getFlagMap().remove(flagTeam.getName());
            if (this.getHeadGearMap() != null && this.getHeadGearMap().get(aPlayer.getName()) != null) {
                if (aPlayer.get() != null) {
                    aPlayer.get().getInventory()
                            .setHelmet(this.getHeadGearMap().get(aPlayer.getName()).clone());
                }
                this.getHeadGearMap().remove(aPlayer.getName());
            }

            this.releaseFlag(flagTeam.getName());
        }
    }

    @Override
    public void displayInfo(final CommandSender sender) {
        Config cfg = this.arena.getArenaConfig();
        sender.sendMessage("flageffect: " + cfg.getString(CFG.GOAL_PFLAGS_FLAGEFFECT));
        sender.sendMessage("flagtype: " + cfg.getString(CFG.GOAL_PFLAGS_FLAGTYPE));
        sender.sendMessage("lives: " + cfg.getInt(CFG.GOAL_PFLAGS_LIVES));
        sender.sendMessage(StringParser.colorVar("mustbesafe", cfg.getBoolean(CFG.GOAL_PFLAGS_MUSTBESAFE))
                + " | " + StringParser.colorVar("flaghead", this.hasWoolHead()));
    }

    private Map<String, BlockData> getFlagDataMap() {
        if (this.flagDataMap == null) {
            this.flagDataMap = new HashMap<>();
        }
        return this.flagDataMap;
    }

    @Override
    public void initiate(final Player player) {
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        final ArenaTeam team = aPlayer.getArenaTeam();
        if (!this.getLifeMap().containsKey(team.getName())) {
            this.getLifeMap().put(aPlayer.getArenaTeam().getName(), this.arena.getArenaConfig().getInt(CFG.GOAL_PFLAGS_LIVES));
        }
    }

    @Override
    public void parsePlayerDeath(final Player player,
                                 final EntityDamageEvent lastDamageCause) {

        if (this.getFlagMap().isEmpty()) {
            debug(this.arena, player, "no flags set!!");
            return;
        }
        final String sTeam = this.getHeldFlagTeam(player);
        final ArenaTeam flagTeam = this.arena.getTeam(sTeam);
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        if (flagTeam == null) {
            if (sTeam != null) {
                this.arena.broadcast(Language.parse(this.arena, MSG.GOAL_FLAGS_DROPPEDTOUCH, aPlayer
                        .getArenaTeam().getColorCodeString()
                        + aPlayer.getName()
                        + ChatColor.YELLOW));

                this.getFlagMap().remove(TOUCHDOWN);
                if (this.getHeadGearMap() != null && this.getHeadGearMap().get(aPlayer.getName()) != null) {
                    if (aPlayer.get() != null) {
                        aPlayer.get().getInventory().setHelmet(this.getHeadGearMap().get(aPlayer.getName()).clone());
                    }
                    this.getHeadGearMap().remove(aPlayer.getName());
                }

                this.releaseFlag(TOUCHDOWN);
            }
        } else {
            this.arena.broadcast(Language.parse(this.arena, MSG.GOAL_FLAGS_DROPPED, aPlayer
                            .getArenaTeam().colorizePlayer(player) + ChatColor.YELLOW,
                    flagTeam.getColoredName() + ChatColor.YELLOW));
            this.getFlagMap().remove(flagTeam.getName());
            if (this.getHeadGearMap() != null && this.getHeadGearMap().get(player.getName()) != null) {
                player.getInventory().setHelmet(this.getHeadGearMap().get(player.getName()).clone());
                this.getHeadGearMap().remove(player.getName());
            }

            this.releaseFlag(flagTeam.getName());
        }
    }

    @Override
    public void parseStart() {
        this.getLifeMap().clear();
        this.getFlagDataMap().clear();
        for (final ArenaTeam team : this.arena.getTeams()) {
            if (!team.getTeamMembers().isEmpty()) {
                debug(this.arena, "adding team " + team.getName());
                // team is active
                this.getLifeMap().put(team.getName(), this.arena.getArenaConfig().getInt(CFG.GOAL_PFLAGS_LIVES, 3));
                Block flagBlock = this.getTeamFlagLoc(team.getName()).toLocation().getBlock();
                this.getFlagDataMap().put(team.getName(), flagBlock.getBlockData().clone());
            }
        }
        ofNullable(this.getTeamFlagLoc(TOUCHDOWN)).ifPresent(paBlockLocation -> {
            Block touchdownFlagBlock = paBlockLocation.toLocation().getBlock();
            this.getFlagDataMap().put(TOUCHDOWN, touchdownFlagBlock.getBlockData().clone());
        });
    }

    @Override
    public void reset(final boolean force) {
        this.getHeadGearMap().clear();
        this.getLifeMap().clear();
        this.getFlagMap().clear();
        if(!this.getFlagDataMap().isEmpty()) {
            for (final ArenaTeam team : this.arena.getTeams()) {
                this.releaseFlag(team.getName());
            }
            this.releaseFlag(TOUCHDOWN);
        }
        this.getFlagDataMap().clear();
    }

    /**
     * reset an arena flag
     *
     * @param teamName  team whose flag needs to be reset
     */
    private void releaseFlag(final String teamName) {
        PABlockLocation paBlockLocation = this.getTeamFlagLoc(teamName);
        if (paBlockLocation == null) {
            return;
        }

        Block flagBlock = paBlockLocation.toLocation().getBlock();
        try {
            flagBlock.setBlockData(this.getFlagDataMap().get(teamName));
        } catch (Exception e) {
            PVPArena.getInstance().getLogger().warning("Impossible to reset flag data ! You may recreate arena flags.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onFlagClaim(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        Material brokenMaterial = event.getBlock().getType();
        if (!this.arena.hasPlayer(event.getPlayer()) ||
                !ColorUtils.isSubType(brokenMaterial, this.arena.getArenaConfig().getMaterial(CFG.GOAL_PFLAGS_FLAGTYPE))) {

            debug(this.arena, player, "block destroy, ignoring");
            debug(this.arena, player, String.valueOf(this.arena.hasPlayer(event.getPlayer())));
            debug(this.arena, player, event.getBlock().getType().name());
            return;
        }

        final Block block = event.getBlock();

        debug(this.arena, player, "flag destroy!");

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        if (this.getFlagMap().containsValue(player.getName())) {
            debug(this.arena, player, "already carries a flag!");
            return;
        }
        final ArenaTeam pTeam = aPlayer.getArenaTeam();
        if (pTeam == null) {
            return;
        }

        final Set<ArenaTeam> setTeam = new HashSet<>(this.arena.getTeams());

        setTeam.add(new ArenaTeam(TOUCHDOWN, "BLACK"));
        for (final ArenaTeam team : setTeam) {
            final String teamName = team.getName();
            final PABlockLocation teamFlagLoc = this.getTeamFlagLoc(teamName);

            if (teamName.equals(pTeam.getName())) {
                debug(this.arena, player, "equals!OUT! ");
                continue;
            }
            if (team.getTeamMembers().size() < 1 && !TOUCHDOWN.equals(team.getName())) {
                debug(this.arena, player, "size!OUT! ");
                continue; // dont check for inactive teams
            }
            if (this.getFlagMap().containsKey(teamName)) {
                debug(this.arena, player, "taken!OUT! ");
                continue; // already taken
            }
            debug(this.arena, player, "checking for flag of team " + teamName);
            Vector vLoc = block.getLocation().toVector();
            debug(this.arena, player, "block: " + vLoc);

            if(teamFlagLoc != null && vLoc.equals(teamFlagLoc.toLocation().toVector())) {
                debug(this.arena, player, "flag found!");

                if (TOUCHDOWN.equals(team.getName())) {

                    this.arena.broadcast(Language.parse(this.arena,
                            MSG.GOAL_FLAGS_GRABBEDTOUCH,
                            pTeam.colorizePlayer(player) + ChatColor.YELLOW));
                } else {

                    this.arena.broadcast(Language
                            .parse(this.arena, MSG.GOAL_FLAGS_GRABBED,
                                    pTeam.colorizePlayer(player)
                                            + ChatColor.YELLOW,
                                    team.getColoredName()
                                            + ChatColor.YELLOW));
                }
                try {
                    this.getHeadGearMap().put(player.getName(), player.getInventory().getHelmet().clone());
                } catch (final Exception ignored) {

                }

                if (this.hasWoolHead()) {
                    final ItemStack itemStack = new ItemStack(this.getFlagOverrideTeamMaterial(this.arena, teamName));
                    player.getInventory().setHelmet(itemStack);
                }
                this.applyEffects(player);
                this.getFlagMap().put(teamName, player.getName());
                player.getInventory().addItem(new ItemStack(block.getType()));
                block.setType(Material.AIR);
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!this.isIrrelevantInventoryClickEvent(event) && this.getFlagType().equals(event.getCurrentItem().getType())) {
            event.setCancelled(true);
        }
    }
}
