package net.slipcor.pvparena.classes;

import net.slipcor.pvparena.managers.StatisticsManager;

import java.util.HashMap;
import java.util.Map;

/**
 * <pre>PVP Arena Statistics Map class</pre>
 * <p/>
 * A Map of Statistics, bound to a Player name, being sorted by Arena inside the Statistics class
 *
 * @author slipcor
 * @version v0.10.2
 */

public class PAStatMap {
    private final Map<StatisticsManager.Type, Integer> map = new HashMap<>();

    public void decStat(final StatisticsManager.Type type) {
        this.decStat(type, 1);
    }

    public void decStat(final StatisticsManager.Type type, final int value) {
        this.map.put(type, this.getStat(type) - value);
    }

    public int getStat(final StatisticsManager.Type type) {
        return this.map.containsKey(type) ? this.map.get(type) : 0;
    }

    public void incStat(final StatisticsManager.Type type) {
        this.incStat(type, 1);
    }

    public void incStat(final StatisticsManager.Type type, final int value) {
        this.map.put(type, this.getStat(type) + value);
    }

    public void setStat(final StatisticsManager.Type type, final int value) {
        this.map.put(type, value);
    }
}
