package net.slipcor.pvparena.loader;

import java.lang.reflect.Constructor;
import java.util.Objects;

/**
 * Class wrapper that allows to instantiate external classes easily
 * @param <T> Class to instantiate
 */
public class Loadable<T> {

    private final String name;
    private final boolean internal;
    private final Class<T> loadableClass;

    public Loadable(String name, boolean internal, Class<T> loadableClass) {
        this.name = name;
        this.internal = internal;
        this.loadableClass = loadableClass;
    }

    public String getName() {
        return this.name;
    }

    public boolean isInternal() {
        return this.internal;
    }

    public String getVersion() {
        return this.loadableClass.getPackage().getImplementationVersion();
    }

    public T getNewInstance() throws ReflectiveOperationException {
        Constructor<T> constructor = this.loadableClass.getConstructor();
        return constructor.newInstance();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        Loadable<?> loadable = (Loadable<?>) o;
        return this.name.equals(loadable.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name);
    }

    @Override
    public String toString() {
        return this.name;
    }
}