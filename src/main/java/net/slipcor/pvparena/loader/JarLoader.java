package net.slipcor.pvparena.loader;

import net.slipcor.pvparena.PVPArena;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * Utility class used to load PVPArena optional Jar files
 * like modules, goals or region shapes
 * @param <T> Expected class to load
 */
public class JarLoader<T> {

    private static final String MODULE_FILE = "module.yml";
    private static final String MAIN_CLASS_KEY = "main-class";
    private static final String NAME_KEY = "name";
    private static final String JAR_EXT = ".jar";
    private static final Plugin plugin = PVPArena.getInstance();

    private final File dir;
    private final Class<T> expectedClass;

    private ClassLoader loader;

    private List<File> jarFiles;

    public JarLoader(final File dir, Class<T> expectedClass) {
        this.dir = dir;
        this.expectedClass = expectedClass;
        this.init();
    }

    /**
     * Load all loadable .jar files present in directory (passed in constructor)
     * @return Set of Loadables
     */
    public final Set<Loadable<? extends T>> loadClasses() {
        final Logger logger = plugin.getLogger();
        Set<Loadable<? extends T>> loadableList = new HashSet<>();

        this.jarFiles.forEach(file -> {
            try {
                final JarFile jarFile = new JarFile(file);

                if (jarFile.getEntry(MODULE_FILE) != null) {
                    final JarEntry element = jarFile.getJarEntry(MODULE_FILE);
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(jarFile.getInputStream(element)));
                    YamlConfiguration pathYml = YamlConfiguration.loadConfiguration(reader);
                    String moduleName = pathYml.getString(NAME_KEY);
                    String mainClass = pathYml.getString(MAIN_CLASS_KEY);

                    if (moduleName != null && mainClass != null) {
                        final Class<?> clazz = Class.forName(mainClass, true, this.loader);
                        final Class<? extends T> loadableClass = clazz.asSubclass(this.expectedClass);
                        loadableList.add(new Loadable<>(moduleName, false, loadableClass));
                        debug("[Loader] %s class loaded", moduleName);
                    } else {
                        throw new ClassNotFoundException();
                    }
                }

                jarFile.close();

            } catch (final ClassCastException e) {
                logger.warning(String.format("The JAR file %s is in the wrong directory", file.getPath()));
                logger.warning(String.format("The JAR file %s failed to load", file.getName()));
                e.printStackTrace();

            } catch (final ClassNotFoundException e) {
                logger.warning(String.format("Invalid %s", MODULE_FILE));
                logger.warning(String.format("The JAR file %s failed to load", file.getName()));
                e.printStackTrace();

            } catch (final Exception e) {
                logger.warning("Unknown cause");
                logger.warning(String.format("The JAR file %s failed to load", file.getName()));
                e.printStackTrace();

            }
        });

        return loadableList;
    }

    /**
     * Reload all loadable .jar files present in directory (passed in constructor)
     * @return Set of Loadables
     */
    public Set<Loadable<? extends T>> reloadClasses() {
        this.jarFiles.clear();
        this.init();
        return this.loadClasses();
    }

    private void init() {
        List<File> fileList = ofNullable(this.dir.listFiles()).map(Arrays::asList).orElse(new ArrayList<>());
        this.jarFiles = fileList.stream().filter(f -> f.getName().endsWith(JAR_EXT)).collect(Collectors.toList());

        final List<URL> urls = new ArrayList<>();

        this.jarFiles.forEach(file -> {
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        });

        this.loader = URLClassLoader.newInstance(urls.toArray(new URL[0]), plugin.getClass().getClassLoader());
    }
}