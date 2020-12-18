package net.slipcor.pvparena.classes;

public class PASpawn {
    private final PALocation location;
    private final String name;

    public PASpawn(final PALocation loc, final String string) {
        this.location = loc;
        this.name = string;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof PASpawn) {
            final PASpawn other = (PASpawn) o;
            return this.name.equals(other.name) && this.location.equals(other.location);
        }
        return false;
    }

    public PALocation getLocation() {
        return this.location;
    }

    public String getName() {
        return this.name;
    }
}
