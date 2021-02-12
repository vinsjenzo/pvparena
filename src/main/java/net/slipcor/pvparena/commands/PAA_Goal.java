package net.slipcor.pvparena.commands;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.core.Help;
import net.slipcor.pvparena.core.Help.HELP;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.core.StringParser;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaGoalManager;
import net.slipcor.pvparena.loader.Loadable;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * <pre>PVP Arena GOAL Command class</pre>
 * <p/>
 * A command to manage arena goals
 *
 * @author slipcor
 * @version v0.10.0
 */

public class PAA_Goal extends AbstractArenaCommand {

    public PAA_Goal() {
        super(new String[]{"pvparena.cmds.goal"});
    }

    @Override
    public void commit(final Arena arena, final CommandSender sender, final String[] args) {
        if (!this.hasPerms(sender, arena)) {
            return;
        }

        if (!argCountValid(sender, arena, args, new Integer[]{1, 2})) {
            return;
        }

        ArenaGoalManager goalManager = PVPArena.getInstance().getAgm();
        final Loadable<?> loadableGoal = goalManager.getLoadableByName(args[0].toLowerCase());

        if (loadableGoal == null) {
            arena.msg(sender, Language.parse(arena, MSG.ERROR_GOAL_NOTFOUND, args[0], StringParser.joinSet(goalManager.getAllGoalNames(), " ")));
            arena.msg(sender, Language.parse(arena, MSG.GOAL_INSTALLING));
            return;
        }

        if (args.length < 2) {
            // toggle
            ArenaGoal goal = goalManager.getNewInstance(loadableGoal.getName());
            arena.setGoal(goal, true);
            arena.msg(sender, Language.parse(arena, MSG.GOAL_SET, args[0]));
            return;
        }

        // usage: /pa {arenaname} loadableGoal [loadableGoal] {value}

        arena.msg(sender, Language.parse(arena, MSG.ERROR_INVALID_VALUE, args[1]));
        arena.msg(sender, Language.parse(arena, MSG.ERROR_POSITIVES, StringParser.joinSet(StringParser.positive, " | ")));
        arena.msg(sender, Language.parse(arena, MSG.ERROR_NEGATIVES, StringParser.joinSet(StringParser.negative, " | ")));
    }

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public void displayHelp(final CommandSender sender) {
        Arena.pmsg(sender, Help.parse(HELP.GOAL));
    }

    @Override
    public List<String> getMain() {
        return Collections.singletonList("goal");
    }

    @Override
    public List<String> getShort() {
        return Collections.singletonList("!g");
    }

    @Override
    public CommandTree<String> getSubs(final Arena arena) {
        final CommandTree<String> result = new CommandTree<>(null);
        if (arena == null) {
            return result;
        }

        PVPArena.getInstance().getAgm().getAllGoalNames().forEach(goalName -> {
            result.define(new String[]{goalName});
        });
        return result;
    }
}
