package com.systemdesign.ratelimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 1 (FR7): key -> tier mapping loaded from a static YAML file, hot-reloaded
 * via a filesystem watch on a virtual thread. Phase 2 replaces this with Redis.
 */
@Component
public class ApiKeyRegistry {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyRegistry.class);

    private final Path configPath;
    private volatile Map<String, Tier> keyToTier = Map.of();

    public ApiKeyRegistry(@Value("${ratelimiter.api-keys-config-path}") String configPath) {
        this.configPath = Path.of(configPath);
        reload();
        startWatching();
    }

    public Optional<Tier> tierFor(String apiKey) {
        return Optional.ofNullable(keyToTier.get(apiKey));
    }

    private void reload() {
        try (InputStream in = Files.newInputStream(configPath)) {
            Yaml yaml = new Yaml();
            Map<String, String> raw = yaml.load(in);
            Map<String, Tier> parsed = new HashMap<>();
            if (raw != null) {
                raw.forEach((key, tierName) -> parsed.put(key, Tier.valueOf(tierName.trim().toUpperCase())));
            }
            this.keyToTier = Map.copyOf(parsed);
            log.info("Loaded {} API key mappings from {}", parsed.size(), configPath);
        } catch (IOException e) {
            log.error("Failed to load API key config from {}", configPath, e);
        }
    }

    private void startWatching() {
        Thread.ofVirtual().name("api-key-config-watcher").start(() -> {
            Path dir = configPath.toAbsolutePath().getParent();
            try (WatchService watchService = dir.getFileSystem().newWatchService()) {
                dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
                while (true) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = dir.resolve((Path) event.context());
                        if (changed.getFileName().equals(configPath.getFileName())) {
                            log.info("Detected change in {}, reloading", configPath);
                            reload();
                        }
                    }
                    if (!key.reset()) {
                        log.warn("Watch key no longer valid for {}, stopping watcher", dir);
                        break;
                    }
                }
            } catch (IOException | InterruptedException e) {
                log.error("API key config watcher stopped unexpectedly", e);
            }
        });
    }
}
