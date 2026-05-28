package com.drewdrew1.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkillConfigCache {
    private static final String RESOURCE_DIRECTORY = "Jsons";
    private static final String RESOURCE_PREFIX = RESOURCE_DIRECTORY + "/";
    private static final Path JSON_DIRECTORY = Path.of("plugins", "RPG", "levelSystem", "jsons");

    private final JavaPlugin plugin;
    private final Gson gson = new GsonBuilder().create();
    private Map<String, SkillConfig> skillConfigsByName = Map.of();

    public SkillConfigCache(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Path jsonDirectory() {
        return JSON_DIRECTORY;
    }

    public void copyDefaultsIfMissing() throws IOException {
        Files.createDirectories(JSON_DIRECTORY);

        List<String> bundledResources = bundledJsonResources();
        if (bundledResources.isEmpty()) {
            plugin.getLogger().warning("No bundled level JSON resources found under " + RESOURCE_DIRECTORY + "/");
            return;
        }

        for (String resourcePath : bundledResources) {
            String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            Path target = JSON_DIRECTORY.resolve(fileName);
            if (Files.exists(target)) {
                continue;
            }

            try (InputStream inputStream = plugin.getResource(resourcePath)) {
                if (inputStream == null) {
                    plugin.getLogger().warning("Missing bundled JSON resource: " + resourcePath);
                    continue;
                }
                Files.copy(inputStream, target);
            }
        }
    }

    public void reload() throws IOException {
        Files.createDirectories(JSON_DIRECTORY);

        Map<String, SkillConfig> loadedConfigs = new LinkedHashMap<>();
        for (Path jsonFile : jsonFiles()) {
            if (Files.size(jsonFile) == 0) {
                plugin.getLogger().warning("Skipping empty level JSON: " + jsonFile.getFileName());
                continue;
            }

            SkillConfig config = readConfig(jsonFile);
            String skillName = config.name();
            if (skillName == null || skillName.isBlank()) {
                throw new JsonParseException("Missing skill name in " + jsonFile.getFileName());
            }

            String key = normalize(skillName);
            if (loadedConfigs.containsKey(key)) {
                throw new JsonParseException("Duplicate skill JSON name: " + skillName);
            }
            loadedConfigs.put(key, config);
        }

        skillConfigsByName = Collections.unmodifiableMap(new LinkedHashMap<>(loadedConfigs));
    }

    public Collection<SkillConfig> skillConfigs() {
        return skillConfigsByName.values();
    }

    public Optional<SkillConfig> skillConfig(String name) {
        return Optional.ofNullable(skillConfigsByName.get(normalize(name)));
    }

    public SkillConfig requireSkillConfig(String name) {
        return skillConfig(name).orElseThrow(() -> new IllegalArgumentException("Unknown skill config: " + name));
    }

    private SkillConfig readConfig(Path jsonFile) throws IOException {
        try (Reader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
            SkillConfig config = gson.fromJson(reader, SkillConfig.class);
            if (config == null) {
                throw new JsonParseException("Empty JSON object in " + jsonFile.getFileName());
            }
            return config;
        } catch (JsonParseException exception) {
            throw new JsonParseException("Failed to parse " + jsonFile.getFileName() + ": " + exception.getMessage(),
                    exception);
        }
    }

    private List<Path> jsonFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(JSON_DIRECTORY, "*.json")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    private List<String> bundledJsonResources() throws IOException {
        Set<String> resources = new LinkedHashSet<>();

        URL resourceDirectory = plugin.getClass().getClassLoader().getResource(RESOURCE_DIRECTORY);
        if (resourceDirectory != null) {
            collectResources(resourceDirectory, resources);
        }

        if (resources.isEmpty()) {
            collectCodeSourceResources(resources);
        }

        return resources.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void collectResources(URL resourceDirectory, Collection<String> resources) throws IOException {
        String protocol = resourceDirectory.getProtocol();
        if ("file".equals(protocol)) {
            try {
                collectFileSystemResources(Path.of(resourceDirectory.toURI()), resources);
            } catch (URISyntaxException exception) {
                throw new IOException("Invalid JSON resource directory URI: " + resourceDirectory, exception);
            }
            return;
        }

        if ("jar".equals(protocol)) {
            JarURLConnection connection = (JarURLConnection) resourceDirectory.openConnection();
            connection.setUseCaches(false);
            try (JarFile jarFile = connection.getJarFile()) {
                collectJarResources(jarFile, resources);
            }
        }
    }

    private void collectCodeSourceResources(Collection<String> resources) throws IOException {
        URL location = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
        if (location == null) {
            return;
        }

        Path codeSource;
        try {
            codeSource = Path.of(location.toURI());
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid plugin code source URI: " + location, exception);
        }

        if (Files.isDirectory(codeSource)) {
            Path resourceDirectory = codeSource.resolve(RESOURCE_DIRECTORY);
            if (Files.isDirectory(resourceDirectory)) {
                collectFileSystemResources(resourceDirectory, resources);
            }
            return;
        }

        if (Files.isRegularFile(codeSource) && codeSource.getFileName().toString().endsWith(".jar")) {
            try (JarFile jarFile = new JarFile(codeSource.toFile())) {
                collectJarResources(jarFile, resources);
            }
        }
    }

    private void collectFileSystemResources(Path resourceDirectory, Collection<String> resources) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(resourceDirectory, "*.json")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    resources.add(RESOURCE_PREFIX + path.getFileName());
                }
            }
        }
    }

    private void collectJarResources(JarFile jarFile, Collection<String> resources) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!entry.isDirectory() && isBundledJson(name)) {
                resources.add(name);
            }
        }
    }

    private boolean isBundledJson(String resourcePath) {
        return resourcePath.startsWith(RESOURCE_PREFIX)
                && resourcePath.indexOf('/', RESOURCE_PREFIX.length()) == -1
                && resourcePath.toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
