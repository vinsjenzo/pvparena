package net.slipcor.pvparena.runnables;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaClass;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.core.ColorUtils;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.managers.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>Arena Runnable class "Inventory"</pre>
 * <p/>
 * An arena timer to restore a player's inventory
 *
 * @author slipcor
 * @version v0.10.2
 */

public class InventoryRefillRunnable implements Runnable {
    private final Player player;
    private final List<ItemStack> additions = new ArrayList<>();
    private final Arena arena;
    private final boolean refill;

    public InventoryRefillRunnable(final Arena arena, final Player player, final List<ItemStack> itemList) {
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        if (arena == null && aPlayer.getArena() == null) {
            this.player = null;
            this.arena = null;
            this.refill = false;
            return;
        }
        this.arena = arena == null ? aPlayer.getArena() : arena;

        boolean keepAll = this.arena.getArenaConfig().getBoolean(CFG.ITEMS_KEEPALLONRESPAWN);

        if (this.arena.getArenaConfig().getItems(CFG.ITEMS_KEEPONRESPAWN) != null) {
            final ItemStack[] items = this.arena.getArenaConfig().getItems(CFG.ITEMS_KEEPONRESPAWN);

            for (final ItemStack item : itemList) {
                if (item != null) {
                    if (keepAll) {
                        this.additions.add(item);
                        continue;
                    }
                    for (final ItemStack iItem : items) {
                        if (iItem != null) {
                            if (item.getType() != iItem.getType()) {
                                continue;
                            }

                            this.additions.add(item);
                            break;
                        }
                    }
                }
            }
        }

        this.refill = this.arena.getArenaConfig().getBoolean(CFG.PLAYER_REFILLINVENTORY);

        Bukkit.getScheduler().scheduleSyncDelayedTask(PVPArena.getInstance(), this, 3L);
        this.player = player;
    }

    @Override
    public void run() {
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(this.player.getName());
        debug(this.arena, "refilling " + this.player.getName());
        if (aPlayer.getStatus() == Status.FIGHT) {
            if (aPlayer.hasCustomClass() && !this.arena.getArenaConfig().getBoolean(CFG.PLAYER_REFILLCUSTOMINVENTORY) || !this.arena.getArenaConfig().getBoolean(CFG.PLAYER_REFILLINVENTORY)) {
                if (this.refill) {
                    final ItemStack[] items = new ItemStack[this.additions.size()];
                    int pos = 0;
                    for (final ItemStack item : this.additions) {
                        items[pos++] = item;
                    }
                    if(items.length > 0){
                        ArenaClass.equip(this.player, items);
                    } else {
                        PVPArena.getInstance().getLogger().info("Can't refill inventory, please set " + CFG.ITEMS_KEEPONRESPAWN.getNode()
                                + ", " + CFG.ITEMS_KEEPALLONRESPAWN.getNode() + " or " + CFG.PLAYER_REFILLCUSTOMINVENTORY.getNode() + " parameter");
                    }
                }
                if (this.arena.getArenaConfig().getBoolean(CFG.USES_WOOLHEAD)) {
                    final ArenaTeam aTeam = aPlayer.getArenaTeam();
                    final ChatColor chatColor = aTeam.getColor();
                    debug(this.arena, this.player, "forcing woolhead: " + aTeam.getName() + '/'
                                        + chatColor.name());
                    this.player.getInventory().setHelmet(
                            new ItemStack(ColorUtils.getWoolMaterialFromChatColor(chatColor), 1));
                    this.arena.getGoal().refillInventory(this.player);
                }
            } else if (this.refill && aPlayer.hasCustomClass()) {
                ArenaPlayer.reloadInventory(this.arena, this.player, false);

                for (final ItemStack item : this.additions) {
                    this.player.getInventory().addItem(item);
                }
            } else if (this.refill) {
                InventoryManager.clearInventory(this.player);
                ArenaPlayer.givePlayerFightItems(this.arena, this.player);

                for (final ItemStack item : this.additions) {
                    this.player.getInventory().addItem(item);
                }
            }
        } else {
            debug(this.arena, "NOT");
        }
        this.player.setFireTicks(0);
        try {
            Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), new Runnable() {
                @Override
                public void run() {
                    if (InventoryRefillRunnable.this.player.getFireTicks() > 0) {
                        InventoryRefillRunnable.this.player.setFireTicks(0);
                    }
                }
            }, 5L);
        } catch (Exception e) {
        }
    }
}
