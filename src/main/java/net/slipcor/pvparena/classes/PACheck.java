package net.slipcor.pvparena.classes;

import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModule;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * CLass used to compare priorities between loadables
 */

public class PACheck {
    private int priority;
    private String error;
    private String modName;

    /**
     * @return the error message
     */
    public String getError() {
        return this.error;
    }

    /**
     * @return the module name returning the current result
     */
    public String getModName() {
        return this.modName;
    }

    /**
     * @return the PACR priority
     */
    public int getPriority() {
        return this.priority;
    }

    /**
     * @return true if there was an error
     */
    public boolean hasError() {
        return this.error != null;
    }

    /**
     * set the error message
     *
     * @param error the error message
     */
    public void setError(ArenaGoal loadable, String error) {
        this.modName = loadable.getName();
        try {
            Integer.parseInt(error);
        } catch (Exception e) {
            debug("{} is setting error to: {}", this.modName, error);
        }
        this.error = error;
        this.priority += 1000;
    }

    /**
     * set the priority
     *
     * @param priority the priority
     */
    public void setPriority(ArenaGoal loadable, int priority) {
        this.modName = loadable.getName();
        debug("{} is setting priority to: {}", this.modName, priority);
        this.priority = priority;
    }

    public void setError(ArenaModule module, String error) {
        this.modName = module.getName();
        try {
            Integer.parseInt(error);
        } catch (Exception e) {
            debug("{} is setting error to: {}", this.modName, error);
        }
        this.error = error;
        this.priority += 1000;
    }

    /**
     * set the priority
     *
     * @param priority the priority
     */
    public void setPriority(ArenaModule module, int priority) {
        this.modName = module.getName();
        debug("{} is setting priority to: {}", this.modName, priority);
        this.priority = priority;
    }
}