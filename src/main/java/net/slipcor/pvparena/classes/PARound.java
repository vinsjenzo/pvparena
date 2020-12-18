package net.slipcor.pvparena.classes;

import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.loadables.ArenaGoal;

import java.util.Set;

/**
 * <pre>PVP Arena Round class</pre>
 * <p/>
 * A class to organize multiple ways of playing for an arena
 *
 * @author slipcor
 * @version v0.9.1
 */

public class PARound {
    private final Set<ArenaGoal> goals;

    public PARound(final Set<ArenaGoal> result) {
        this.goals = result;
    }

    public Set<ArenaGoal> getGoals() {
        return this.goals;
    }

    public boolean toggle(final Arena arena, final ArenaGoal goal) {
        final ArenaGoal nugoal = (ArenaGoal) goal.clone();
        nugoal.setArena(arena);

        boolean contains = false;
        ArenaGoal removeGoal = nugoal;

        for (final ArenaGoal g : this.goals) {
            if (g.getName().equals(goal.getName())) {
                contains = true;
                removeGoal = g;
                break;
            }
        }

        if (contains) {
            this.goals.remove(removeGoal);
            arena.updateRounds();
            return false;
        } else {
            this.goals.add(nugoal);
            arena.updateRounds();
            return true;
        }
    }
}
