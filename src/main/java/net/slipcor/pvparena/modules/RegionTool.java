package net.slipcor.pvparena.modules;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.loadables.ArenaModule;
import net.slipcor.pvparena.loadables.ArenaRegion;
import net.slipcor.pvparena.managers.ArenaManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>Arena Module class "RegionTool"</pre>
 * <p/>
 * Enables region debug via WAND item
 *
 * @author slipcor
 */

public class RegionTool extends ArenaModule {
    public RegionTool() {
        super("RegionTool");
    }

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public boolean onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getPlayer().getEquipment().getItemInMainHand() == null
                || event.getPlayer().getEquipment().getItemInMainHand().getType() == Material.AIR) {
            return false;
        }

        if (event.getPlayer().getEquipment().getItemInMainHand().getType() == Material.AIR) {
            return false;
        }

        if (!PVPArena.hasAdminPerms(event.getPlayer())) {
            return false;
        }

        if (event.getHand().equals(EquipmentSlot.OFF_HAND)) {
            debug(event.getPlayer(), "exiting: offhand");
            return false;
        }

        for (final Arena arena : ArenaManager.getArenas()) {
            if (arena.getArenaConfig().getString(CFG.GENERAL_WAND) != null) {
                debug(arena, event.getPlayer(), "reading wand");
                final Material mMat = Material.getMaterial(arena.getArenaConfig().getString(CFG.GENERAL_WAND));
                debug(arena, event.getPlayer(), "mMat now is " + mMat.name());

                if (event.getPlayer().getEquipment().getItemInMainHand().getType() == mMat) {
                    PABlockLocation loc = new PABlockLocation(event.getPlayer().getLocation());
                    if (event.getClickedBlock() != null) {
                        loc = new PABlockLocation(event.getClickedBlock().getLocation());
                    }
                    for (final ArenaRegion region : arena.getRegions()) {
                        if (region.getShape().contains(loc)) {
                            arena.msg(event.getPlayer(), ChatColor.COLOR_CHAR + "fArena " + ChatColor.COLOR_CHAR + 'b'
                                    + arena.getName() + ChatColor.COLOR_CHAR + "f: region " + ChatColor.COLOR_CHAR + 'b'
                                    + region.getRegionName());
                        }
                    }
                }
            }
        }
        return false;
    }
}
