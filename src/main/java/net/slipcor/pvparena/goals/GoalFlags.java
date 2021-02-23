package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PABlockLocation;
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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Goal class "Flags"
 * </pre>
 * <p/>
 * Well, should be clear. Capture flags, bring them home, get points, win.
 *
 * @author slipcor
 */

public class GoalFlags extends AbstractFlagGoal implements Listener {
    public GoalFlags() {
        super("Flags");
    }

    @Override
    protected CFG getFlagTypeCfg() {
        return CFG.GOAL_FLAGS_FLAGTYPE;
    }

    @Override
    protected CFG getFlagEffectCfg() {
        return CFG.GOAL_FLAGS_FLAGEFFECT;
    }

    @Override
    protected boolean hasWoolHead() {
        return this.arena.getArenaConfig().getBoolean(CFG.GOAL_FLAGS_WOOLFLAGHEAD);
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
        if (this.arena.realEndRunner != null) {
            debug(this.arena, "[CTF] already ending!!");
            return false;
        }

        Material flagType = this.getFlagType();
        if (!ColorUtils.isSubType(block.getType(), flagType)) {
            debug(this.arena, player, "block, but not flag");
            return false;
        }
        debug(this.arena, player, "flag click!");

        Vector vLoc;
        Vector vFlag = null;
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        if (this.getFlagMap().containsValue(player.getName())) {
            debug(this.arena, player, "player " + player.getName() + " has got a flag");
            vLoc = block.getLocation().toVector();
            final String sTeam = aPlayer.getArenaTeam().getName();
            debug(this.arena, player, "block: " + vLoc);
            if (!SpawnManager.getBlocksStartingWith(this.arena, sTeam + "flag").isEmpty()) {
                vFlag = SpawnManager
                        .getBlockNearest(
                                SpawnManager.getBlocksStartingWith(this.arena, sTeam + "flag"),
                                new PABlockLocation(player.getLocation()))
                        .toLocation().toVector();
            } else {
                debug(this.arena, player, sTeam + "flag = null");
            }

            debug(this.arena, player, "player is in the team " + sTeam);
            if (vFlag != null && vLoc.distance(vFlag) < 2) {

                debug(this.arena, player, "player is at his flag");

                if (this.getFlagMap().containsKey(sTeam) || this.getFlagMap().containsKey(TOUCHDOWN)) {
                    debug(this.arena, player, "the flag of the own team is taken!");

                    if (this.arena.getArenaConfig().getBoolean(
                            CFG.GOAL_FLAGS_MUSTBESAFE)
                            && !this.getFlagMap().containsKey(TOUCHDOWN)) {
                        debug(this.arena, player, "cancelling");

                        this.arena.msg(player,
                                Language.parse(this.arena, MSG.GOAL_FLAGS_NOTSAFE));
                        return false;
                    }
                }

                String flagTeam = this.getHeldFlagTeam(player);

                debug(this.arena, player, "the flag belongs to team " + flagTeam);

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
                    this.releaseFlag(ChatColor.BLACK, this.getTeamFlagLoc(TOUCHDOWN));
                } else {
                    this.releaseFlag(this.arena.getTeam(flagTeam).getColor(), this.getTeamFlagLoc(flagTeam));
                }
                this.removeEffects(player);
                if (this.hasWoolHead()) {
                    if (this.getHeadGearMap().get(player.getName()) == null) {
                        player.getInventory().setHelmet(
                                new ItemStack(Material.AIR, 1));
                    } else {
                        player.getInventory().setHelmet(
                                this.getHeadGearMap().get(player.getName()).clone());
                        this.getHeadGearMap().remove(player.getName());
                    }
                }

                flagTeam = TOUCHDOWN.equals(flagTeam) ? flagTeam + ':' + aPlayer
                        .getArenaTeam().getName() : flagTeam;

                this.reduceLivesCheckEndAndCommit(this.arena, flagTeam); // TODO move to
                // "commit" ?

                final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "trigger:" + aPlayer.getName());
                Bukkit.getPluginManager().callEvent(gEvent);
                return true;
            }
        } else {
            final ArenaTeam pTeam = aPlayer.getArenaTeam();
            if (pTeam == null) {
                return false;
            }

            final Set<ArenaTeam> setTeam = new HashSet<>(this.arena.getTeams());

            setTeam.add(new ArenaTeam(TOUCHDOWN, "BLACK"));
            for (final ArenaTeam team : setTeam) {
                final String aTeam = team.getName();

                if (aTeam.equals(pTeam.getName())) {
                    debug(this.arena, player, "equals!OUT! ");
                    continue;
                }

                if (team.getTeamMembers().size() < 1 && !TOUCHDOWN.equals(team.getName())) {
                    debug(this.arena, player, "size!OUT! ");
                    continue; // dont check for inactive teams
                }

                if (this.getFlagMap() != null && this.getFlagMap().containsKey(aTeam)) {
                    debug(this.arena, player, "taken!OUT! ");
                    continue; // already taken
                }

                debug(this.arena, player, "checking for flag of team " + aTeam);
                vLoc = block.getLocation().toVector();
                debug(this.arena, player, "block: " + vLoc);

                if (!SpawnManager.getBlocksStartingWith(this.arena, aTeam + "flag").isEmpty()) {
                    vFlag = SpawnManager
                            .getBlockNearest(
                                    SpawnManager.getBlocksStartingWith(this.arena, aTeam
                                            + "flag"),
                                    new PABlockLocation(player.getLocation()))
                            .toLocation().toVector();
                }

                if (vFlag != null && vLoc.distance(vFlag) < 2) {
                    debug(this.arena, player, "flag found!");
                    debug(this.arena, player, "vFlag: " + vFlag);

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
                        final ItemStack itemStack = new ItemStack(this.getFlagOverrideTeamMaterial(this.arena, aTeam));
                        player.getInventory().setHelmet(itemStack);
                    }
                    this.applyEffects(player);

                    this.takeFlag(new PABlockLocation(vFlag.toLocation(block.getWorld())));
                    this.getFlagMap().put(aTeam, player.getName());

                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void disconnect(final ArenaPlayer aPlayer) {
        if (this.getFlagMap() == null) {
            return;
        }
        final String sTeam = this.getHeldFlagTeam(aPlayer.get());
        final ArenaTeam flagTeam = this.arena.getTeam(sTeam);

        if (flagTeam == null) {
            if (sTeam == null) {
                return;
            }
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

            this.releaseFlag(ChatColor.BLACK, this.getTeamFlagLoc(TOUCHDOWN));

            return;
        }
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

        this.releaseFlag(flagTeam.getColor(), this.getTeamFlagLoc(flagTeam.getName()));
    }

    @Override
    public void displayInfo(final CommandSender sender) {
        Config cfg = this.arena.getArenaConfig();
        sender.sendMessage("flageffect: " + cfg.getString(CFG.GOAL_FLAGS_FLAGEFFECT));
        sender.sendMessage("flagtype: " + cfg.getString(CFG.GOAL_FLAGS_FLAGTYPE));
        sender.sendMessage("lives: " + cfg.getInt(CFG.GOAL_FLAGS_LIVES));
        sender.sendMessage(StringParser.colorVar("mustbesafe", cfg.getBoolean(CFG.GOAL_FLAGS_MUSTBESAFE))
                + " | " + StringParser.colorVar("flaghead", this.hasWoolHead())
                + " | " + StringParser.colorVar("alterOnCatch", cfg.getBoolean(CFG.GOAL_FLAGS_ALTERONCATCH)));
    }

    @Override
    public void initiate(final Player player) {
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        final ArenaTeam team = aPlayer.getArenaTeam();
        if (!this.getLifeMap().containsKey(team.getName())) {
            this.getLifeMap().put(aPlayer.getArenaTeam().getName(), this.arena.getArenaConfig()
                    .getInt(CFG.GOAL_FLAGS_LIVES));

            this.releaseFlag(team.getColor(), this.getTeamFlagLoc(team.getName()));
            this.releaseFlag(ChatColor.BLACK, this.getTeamFlagLoc(TOUCHDOWN));
        }
    }

    @Override
    public void parsePlayerDeath(final Player player,
                                 final EntityDamageEvent lastDamageCause) {

        if (this.getFlagMap() == null) {
            debug(this.arena, player, "no flags set!!");
            return;
        }
        final String sTeam = this.getHeldFlagTeam(player);
        final ArenaTeam flagTeam = this.arena.getTeam(sTeam);
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        if (flagTeam == null) {
            if (sTeam == null) {
                return;
            }
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

            this.releaseFlag(ChatColor.BLACK, this.getTeamFlagLoc(TOUCHDOWN));
            return;
        }
        this.arena.broadcast(Language.parse(this.arena, MSG.GOAL_FLAGS_DROPPED, aPlayer
                        .getArenaTeam().colorizePlayer(player) + ChatColor.YELLOW,
                flagTeam.getColoredName() + ChatColor.YELLOW));
        this.getFlagMap().remove(flagTeam.getName());
        if (this.getHeadGearMap() != null && this.getHeadGearMap().get(player.getName()) != null) {
            player.getInventory().setHelmet(this.getHeadGearMap().get(player.getName()).clone());
            this.getHeadGearMap().remove(player.getName());
        }

        this.releaseFlag(flagTeam.getColor(), this.getTeamFlagLoc(flagTeam.getName()));
    }

    @Override
    public void parseStart() {
        this.getLifeMap().clear();
        for (final ArenaTeam team : this.arena.getTeams()) {
            if (!team.getTeamMembers().isEmpty()) {
                debug(this.arena, "adding team " + team.getName());
                // team is active
                this.getLifeMap().put(team.getName(),
                        this.arena.getArenaConfig().getInt(CFG.GOAL_FLAGS_LIVES, 3));
            }
            this.releaseFlag(team.getColor(), this.getTeamFlagLoc(team.getName()));
        }
        this.releaseFlag(ChatColor.BLACK, this.getTeamFlagLoc(TOUCHDOWN));
    }

    @Override
    public void reset(final boolean force) {
        this.getFlagMap().clear();
        this.getHeadGearMap().clear();
        this.getLifeMap().clear();
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (this.isIrrelevantInventoryClickEvent(event)) {
            return;
        }

        if (this.hasWoolHead() && event.getSlotType() == InventoryType.SlotType.ARMOR &&
                this.getFlagType().equals(event.getCurrentItem().getType())) {
            event.setCancelled(true);
        }
    }

    /**
     * reset an arena flag
     *
     * @param flagColor       the teamcolor to reset
     * @param paBlockLocation the location to take/reset
     */
    private void releaseFlag(final ChatColor flagColor, final PABlockLocation paBlockLocation) {
        if (paBlockLocation == null) {
            return;
        }

        if(this.arena.getArenaConfig().getBoolean(CFG.GOAL_FLAGS_ALTERONCATCH)) {
            Block flagBlock = paBlockLocation.toLocation().getBlock();

            if (ColorUtils.isColorableMaterial(flagBlock.getType())) {
                ColorUtils.setNewFlagColor(flagBlock, flagColor);
            } else {
                flagBlock.setType(this.getFlagType());
            }
        }
    }

    /**
     * take an arena flag
     *
     * @param paBlockLocation the location to take/reset
     */
    private void takeFlag(final PABlockLocation paBlockLocation) {
        if (paBlockLocation == null) {
            return;
        }

        if(this.arena.getArenaConfig().getBoolean(CFG.GOAL_FLAGS_ALTERONCATCH)) {
            Block flagBlock = paBlockLocation.toLocation().getBlock();

            if (ColorUtils.isColorableMaterial(flagBlock.getType())) {
                ColorUtils.setNewFlagColor(flagBlock, ChatColor.WHITE);
            } else {
                flagBlock.setType(Material.BEDROCK);
            }
        }
    }
}
