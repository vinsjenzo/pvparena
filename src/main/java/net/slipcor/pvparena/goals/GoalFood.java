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
import net.slipcor.pvparena.commands.PAA_Region;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.exceptions.GameplayException;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.managers.InventoryManager;
import net.slipcor.pvparena.managers.PriorityManager;
import net.slipcor.pvparena.managers.SpawnManager;
import net.slipcor.pvparena.managers.TeamManager;
import net.slipcor.pvparena.runnables.EndRunnable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Goal class "Food"
 * </pre>
 * <p/>
 * Players are equipped with raw food, the goal is to bring back cooked food
 * to their base. The first team having gathered enough wins!
 *
 * @author slipcor
 */

public class GoalFood extends ArenaGoal implements Listener {
    public GoalFood() {
        super("Food");
    }

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    private static final int PRIORITY = 12;

    private Map<ArenaTeam, Material> foodtypes;
    private Map<Block, ArenaTeam> chestMap;
    private static final Map<Material, Material> cookmap = new HashMap<>();

    static {
        cookmap.put(Material.BEEF, Material.COOKED_BEEF);
        cookmap.put(Material.CHICKEN, Material.COOKED_CHICKEN);
        cookmap.put(Material.COD, Material.COOKED_COD);
        cookmap.put(Material.MUTTON, Material.COOKED_MUTTON);
        cookmap.put(Material.PORKCHOP, Material.COOKED_PORKCHOP);
        cookmap.put(Material.POTATO, Material.BAKED_POTATO);
        cookmap.put(Material.SALMON, Material.COOKED_SALMON);
    }

    @Override
    public boolean allowsJoinInBattle() {
        return this.arena.getArenaConfig().getBoolean(CFG.PERMS_JOININBATTLE);
    }

    @Override
    public boolean checkCommand(final String string) {
        return this.arena.getTeams().stream()
                .map(ArenaTeam::getName)
                .anyMatch(sTeam -> string.contains(sTeam + "foodchest") || string.contains(sTeam + "foodfurnace"));
    }

    @Override
    public List<String> getMain() {
        final List<String> result = new ArrayList<>();
        if (this.arena != null) {
            for (final ArenaTeam team : this.arena.getTeams()) {
                final String sTeam = team.getName();
                result.add(sTeam + "foodchest");
                result.add(sTeam + "foodfurnace");
            }
        }
        return result;
    }

    @Override
    public boolean checkEnd() throws GameplayException {
        final int count = TeamManager.countActiveTeams(this.arena);

        if (count == 1) {
            return true; // yep. only one team left. go!
        } else if (count == 0) {
            throw new GameplayException(MSG.ERROR_TEAMNOTFOUND);
        }

        return false;
    }

    @Override
    public String checkForMissingSpawns(final Set<String> list) {
        final String error = this.checkForMissingTeamSpawn(list);
        if (error != null) {
            return error;
        }
        return this.checkForMissingTeamCustom(list, "foodchest");
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
                res.setError(this, Language.parse(this.arena, MSG.ERROR_JOIN_TEAM_FULL, team.getName()));
                return res;
            }
        }

        res.setPriority(this, PRIORITY);
        return res;
    }

    @Override
    public Boolean checkPlayerDeath(Player player) {
        return true;
    }

    @Override
    public boolean checkSetBlock(final Player player, final Block block) {

        if (!PAA_Region.activeSelections.containsKey(player.getName())) {
            return false;
        }

        if (this.flagName == null || block == null
                || block.getType() != Material.CHEST && block.getType() != Material.FURNACE) {
            return false;
        }

        return PVPArena.hasAdminPerms(player) || PVPArena.hasCreatePerms(player, this.arena);
    }

    private String flagName;

    @Override
    public void commitCommand(final CommandSender sender, final String[] args) {
        if (args[0].contains("foodchest")) {
            for (final ArenaTeam team : this.arena.getTeams()) {
                final String sTeam = team.getName();
                if (args[0].contains(sTeam + "foodchest")) {
                    this.flagName = args[0];
                    PAA_Region.activeSelections.put(sender.getName(), this.arena);

                    this.arena.msg(sender,
                            Language.parse(this.arena, MSG.GOAL_FOOD_TOSET, this.flagName));
                }
            }
        } else if (args[0].contains("foodfurnace")) {
            for (final ArenaTeam team : this.arena.getTeams()) {
                final String sTeam = team.getName();
                if (args[0].contains(sTeam + "foodfurnace")) {
                    this.flagName = args[0];
                    PAA_Region.activeSelections.put(sender.getName(), this.arena);

                    this.arena.msg(sender,
                            Language.parse(this.arena, MSG.GOAL_FOODFURNACE_TOSET, this.flagName));
                }
            }
        }
    }

    @Override
    public void commitEnd(final boolean force) {
        if (this.arena.realEndRunner != null) {
            debug(this.arena, "[FOOD] already ending");
            return;
        }
        debug(this.arena, "[FOOD]");

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
    public void commitPlayerDeath(final Player respawnPlayer, final boolean doesRespawn,
                                  final PlayerDeathEvent event) {

        if (this.arena.getArenaConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
            this.broadcastSimpleDeathMessage(respawnPlayer, event);
        }

        final List<ItemStack> returned;

        if (this.arena.getArenaConfig().getBoolean(
                CFG.PLAYER_DROPSINVENTORY)) {
            returned = InventoryManager.drop(respawnPlayer);
            event.getDrops().clear();
        } else {
            returned = new ArrayList<>(event.getDrops());
        }

        PriorityManager.handleRespawn(this.arena, ArenaPlayer.parsePlayer(respawnPlayer.getName()), returned);

    }

    @Override
    public boolean commitSetFlag(final Player player, final Block block) {

        debug(this.arena, player, "trying to set a foodchest/furnace");

        // command : /pa redflag1
        // location: red1flag:

        SpawnManager.setBlock(this.arena, new PABlockLocation(block.getLocation()),
                this.flagName);


        if (this.flagName.contains("furnace")) {
            if (block.getType() != Material.FURNACE) {
                return false;
            }
            this.arena.msg(player, Language.parse(this.arena, MSG.GOAL_FOODFURNACE_SET, this.flagName));

        } else {
            if (block.getType() != Material.CHEST) {
                return false;
            }
            this.arena.msg(player, Language.parse(this.arena, MSG.GOAL_FOOD_SET, this.flagName));

        }

        PAA_Region.activeSelections.remove(player.getName());
        this.flagName = "";

        return true;
    }

    @Override
    public void configParse(final YamlConfiguration config) {
        Bukkit.getPluginManager().registerEvents(this, PVPArena.getInstance());
    }

    @Override
    public void displayInfo(final CommandSender sender) {
        sender.sendMessage("items needed: "
                + this.arena.getArenaConfig().getInt(CFG.GOAL_FOOD_FMAXITEMS));
        sender.sendMessage("items per player: "
                + this.arena.getArenaConfig().getInt(CFG.GOAL_FOOD_FPLAYERITEMS));
        sender.sendMessage("items per team: "
                + this.arena.getArenaConfig().getInt(CFG.GOAL_FOOD_FTEAMITEMS));
    }

    private Map<ArenaTeam, Material> getFoodMap() {
        if (this.foodtypes == null) {
            this.foodtypes = new HashMap<>();
        }
        return this.foodtypes;
    }

    @Override
    public int getLives(ArenaPlayer aPlayer) {
        return this.getLifeMap().getOrDefault(aPlayer.getArenaTeam().getName(), 0);
    }

    @Override
    public boolean hasSpawn(final String string) {
        for (final String teamName : this.arena.getTeamNames()) {
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
    public void initiate(final Player player) {
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        if (this.getLifeMap().get(aPlayer.getArenaTeam().getName()) == null) {
            this.getLifeMap().put(aPlayer.getArenaTeam().getName(), this.arena.getArenaConfig()
                    .getInt(CFG.GOAL_FOOD_FMAXITEMS));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceClick(final PlayerInteractEvent event) {
        if (!event.hasBlock() || event.getClickedBlock().getType() != Material.FURNACE) {
            return;
        }

        final ArenaPlayer player = ArenaPlayer.parsePlayer(event.getPlayer().getName());

        if (player.getArena() == null || !player.getArena().isFightInProgress()) {
            return;
        }

        final Set<PABlock> spawns = SpawnManager.getPABlocksContaining(this.arena, "foodfurnace");

        if (spawns.size() < 1) {
            return;
        }

        final String teamName = player.getArenaTeam().getName();

        final Set<PABlockLocation> validSpawns = new HashSet<>();

        for (final PABlock block : spawns) {
            final String spawnName = block.getName();
            if (spawnName.startsWith(teamName + "foodfurnace")) {
                validSpawns.add(block.getLocation());
            }
        }

        if (validSpawns.size() < 1) {
            return;
        }

        if (!validSpawns.contains(new PABlockLocation(event.getClickedBlock().getLocation()))) {
            this.arena.msg(player.get(), Language.parse(this.arena, MSG.GOAL_FOOD_NOTYOURFOOD));
            event.setCancelled(true);
        }

    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemTransfer(final InventoryMoveItemEvent event) {

        if (this.arena == null || !this.arena.isFightInProgress()) {
            return;
        }

        final InventoryType type = event.getDestination().getType();

        if (type != InventoryType.CHEST) {
            return;
        }

        if (this.chestMap == null || !this.chestMap.containsKey(((Chest) event.getDestination()
                .getHolder()).getBlock())) {
            return;
        }

        final ItemStack stack = event.getItem();

        final ArenaTeam team = this.chestMap.get(((Chest) event.getDestination()
                .getHolder()).getBlock());

        if (team == null || stack == null || stack.getType() != cookmap.get(this.getFoodMap().get(team))) {
            return;
        }

        ArenaPlayer noone = null;

        for (final ArenaPlayer player : team.getTeamMembers()) {
            noone = player;
            break;
        }

        if (noone == null) {
            return;
        }

        // INTO container
        final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "score:" +
                noone.getName() + ':' + team.getName() + ':' + stack.getAmount());
        Bukkit.getPluginManager().callEvent(gEvent);
        this.reduceLives(this.arena, team, stack.getAmount());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {

        if (this.arena == null || !this.arena.isFightInProgress()) {
            return;
        }

        final InventoryType type = event.getInventory().getType();

        if (type != InventoryType.CHEST) {
            return;
        }

        if (this.chestMap == null || !this.chestMap.containsKey(((Chest) event.getInventory()
                .getHolder()).getBlock())) {
            return;
        }

        if (!event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        final ItemStack stack = event.getCurrentItem();

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(event.getWhoClicked().getName());

        final ArenaTeam team = aPlayer.getArenaTeam();

        if (team == null || stack == null || stack.getType() != cookmap.get(this.getFoodMap().get(team))) {
            return;
        }

        final SlotType sType = event.getSlotType();

        if (sType == SlotType.CONTAINER) {
            // OUT of container
            final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "score:" +
                    aPlayer.getName() + ':' + team.getName() + ":-" + stack.getAmount());
            Bukkit.getPluginManager().callEvent(gEvent);
            this.reduceLives(this.arena, team, -stack.getAmount());
        } else {
            // INTO container
            final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "score:" +
                    aPlayer.getName() + ':' + team.getName() + ':' + stack.getAmount());
            Bukkit.getPluginManager().callEvent(gEvent);
            this.reduceLives(this.arena, team, stack.getAmount());
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void parseStart() {

        final int pAmount = this.arena.getArenaConfig().getInt(CFG.GOAL_FOOD_FPLAYERITEMS);
        final int tAmount = this.arena.getArenaConfig().getInt(CFG.GOAL_FOOD_FTEAMITEMS);

        this.chestMap = new HashMap<>();

        for (final ArenaTeam team : this.arena.getTeams()) {
            int pos = new Random().nextInt(cookmap.size());
            for (final Material mat : cookmap.keySet()) {
                if (pos <= 0) {
                    this.getFoodMap().put(team, mat);
                    break;
                }
                pos--;
            }
            int totalAmount = pAmount;
            totalAmount += tAmount / team.getTeamMembers().size();

            if (totalAmount < 1) {
                totalAmount = 1;
            }
            for (final ArenaPlayer player : team.getTeamMembers()) {

                player.get().getInventory().addItem(new ItemStack(this.getFoodMap().get(team), totalAmount));
                player.get().updateInventory();
            }
            this.chestMap.put(SpawnManager.getBlockByExactName(this.arena, team.getName() + "foodchest").toLocation().getBlock(), team);
            this.getLifeMap().put(team.getName(),
                    this.arena.getArenaConfig().getInt(CFG.GOAL_FOOD_FMAXITEMS));
        }
    }

    private void reduceLives(final Arena arena, final ArenaTeam team, final int amount) {
        final int iLives = this.getLifeMap().get(team.getName());

        if (iLives <= amount && amount > 0) {
            for (final ArenaTeam otherTeam : arena.getTeams()) {
                if (otherTeam.equals(team)) {
                    continue;
                }
                this.getLifeMap().remove(otherTeam.getName());
                for (final ArenaPlayer ap : otherTeam.getTeamMembers()) {
                    if (ap.getStatus() == Status.FIGHT) {
                        ap.setStatus(Status.LOST);/*
                        arena.removePlayer(ap.get(), CFG.TP_LOSE.toString(),
								true, false);*/
                    }
                }
            }
            PriorityManager.handleEnd(arena, false);
            return;
        }

        this.getLifeMap().put(team.getName(), iLives - amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void refillInventory(final Player player) {
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        final ArenaTeam team = aPlayer.getArenaTeam();
        if (team == null) {
            return;
        }

        player.getInventory().addItem(new ItemStack(this.getFoodMap().get(team), this.arena.getArenaConfig().getInt(CFG.GOAL_FOOD_FPLAYERITEMS)));
        player.updateInventory();
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

    @Override
    public Map<String, Double> timedEnd(final Map<String, Double> scores) {

        for (final ArenaTeam team : this.arena.getTeams()) {
            double score = this.arena.getArenaConfig().getInt(CFG.GOAL_FOOD_FMAXITEMS)
                    - (this.getLifeMap().containsKey(team.getName()) ? this.getLifeMap().get(team
                    .getName()) : 0);
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
        if (this.allowsJoinInBattle()) {
            this.arena.hasNotPlayed(ArenaPlayer.parsePlayer(player.getName()));
        }
    }
}
