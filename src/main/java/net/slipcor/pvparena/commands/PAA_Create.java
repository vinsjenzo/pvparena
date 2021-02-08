package net.slipcor.pvparena.commands;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.core.Help;
import net.slipcor.pvparena.core.Help.HELP;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaGoalManager;
import net.slipcor.pvparena.managers.ArenaManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <pre>PVP Arena CREATE Command class</pre>
 * <p/>
 * A command to create an arena
 *
 * @author slipcor
 * @version v0.10.0
 */

public class PAA_Create extends AbstractGlobalCommand {

    public PAA_Create() {
        super(new String[]{"pvparena.create", "pvparena.cmds.create"});
    }

    @Override
    public void commit(final CommandSender sender, final String[] args) {
        if (!this.hasPerms(sender)) {
            return;
        }

        if (!(sender instanceof Player)) {
            Arena.pmsg(sender, Language.parse(MSG.ERROR_ONLY_PLAYERS));
            return;
        }

        if (!argCountValid(sender, args, new Integer[]{1, 2})) {
            return;
        }

        // usage: /pa create [arenaname] {legacy_arenatype}

        Arena arena = ArenaManager.getArenaByName(args[0]);

        if (arena != null) {
            Arena.pmsg(sender, Language.parse(arena, MSG.ERROR_ARENA_EXISTS, arena.getName()));
            return;
        }

        arena = new Arena(args[0]);

        if (!sender.hasPermission("pvparena.admin")) {
            // no admin perms => create perms => set owner
            arena.setOwner(sender.getName());
        }

        ArenaGoalManager goalManager = PVPArena.getInstance().getAgm();
        if (args.length > 1) {
            if (goalManager.hasLoadable(args[1])) {
                ArenaGoal goal = goalManager.getNewInstance(args[1]);
                arena.setGoal(goal, true);
            } else {
                arena.msg(sender, Language.parse(MSG.ERROR_GOAL_NOTFOUND, args[1], String.join(",", goalManager.getAllGoalNames())));
                return;
            }
        } else {
            ArenaGoal goal = goalManager.getNewInstance("TeamLives");
            arena.setGoal(goal, true);
        }

        if (ArenaManager.loadArena(arena)) {
            Arena.pmsg(sender, Language.parse(arena, MSG.ARENA_CREATE_DONE, arena.getName()));
            arena = ArenaManager.getArenaByName(arena.getName());
            final PAA_ToggleMod cmd = new PAA_ToggleMod();
            cmd.commit(arena, sender, new String[]{"standardspectate"});
            cmd.commit(arena, sender, new String[]{"standardlounge"});
            cmd.commit(arena, sender, new String[]{"battlefieldjoin"});
        }
    }

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public void displayHelp(final CommandSender sender) {
        Arena.pmsg(sender, Help.parse(HELP.CREATE));
    }

    @Override
    public List<String> getMain() {
        return Arrays.asList("create", "new");
    }

    @Override
    public List<String> getShort() {
        return Collections.singletonList("!c");
    }

    @Override
    public CommandTree<String> getSubs(final Arena nothing) {
        final CommandTree<String> result = new CommandTree<>(null);
        result.define(new String[]{"{String}", "teams"});
        result.define(new String[]{"{String}", "teamdm"});
        result.define(new String[]{"{String}", "dm"});
        result.define(new String[]{"{String}", "free"});
        result.define(new String[]{"{String}", "spleef"});
        result.define(new String[]{"{String}", "ctf"});
        result.define(new String[]{"{String}", "ctp"});
        result.define(new String[]{"{String}", "tank"});
        result.define(new String[]{"{String}", "sabotage"});
        result.define(new String[]{"{String}", "infect"});
        result.define(new String[]{"{String}", "liberation"});
        result.define(new String[]{"{String}", "food"});
        return result;
    }
}
