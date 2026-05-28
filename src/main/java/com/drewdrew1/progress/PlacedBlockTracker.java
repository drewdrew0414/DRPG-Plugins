package com.drewdrew1.progress;

import com.drewdrew1.LevelDatabase;
import com.drewdrew1.Main;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.block.Block;

public final class PlacedBlockTracker {
    private final Main plugin;
    private final LevelDatabase levelDatabase;
    private final Set<BlockPosition> placedBlocks = ConcurrentHashMap.newKeySet();
    private CompletableFuture<Void> writeChain = CompletableFuture.completedFuture(null);

    public PlacedBlockTracker(Main plugin, LevelDatabase levelDatabase) {
        this.plugin = plugin;
        this.levelDatabase = levelDatabase;
    }

    public CompletableFuture<Void> load() {
        return levelDatabase.loadPlacedBlocks().thenAccept(blocks -> {
            placedBlocks.clear();
            for (LevelDatabase.PlacedBlock block : blocks) {
                placedBlocks.add(new BlockPosition(block.world(), block.x(), block.y(), block.z()));
            }
        });
    }

    public void markPlaced(Block block) {
        BlockPosition position = BlockPosition.from(block);
        placedBlocks.add(position);
        enqueueWrite(() -> levelDatabase.savePlacedBlock(position.world(), position.x(), position.y(), position.z()));
    }

    public boolean consumeIfPlaced(Block block) {
        BlockPosition position = BlockPosition.from(block);
        boolean removed = placedBlocks.remove(position);
        if (removed) {
            enqueueWrite(() -> levelDatabase.deletePlacedBlock(position.world(), position.x(), position.y(), position.z()));
        }
        return removed;
    }

    public CompletableFuture<Void> flush() {
        synchronized (this) {
            return writeChain;
        }
    }

    private void enqueueWrite(Supplier<CompletableFuture<Void>> write) {
        synchronized (this) {
            writeChain = writeChain
                    .exceptionally(throwable -> null)
                    .thenCompose(unused -> write.get())
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(Level.WARNING, "Failed to save placed block state.", throwable);
                        return null;
                    });
        }
    }

    private record BlockPosition(UUID world, int x, int y, int z) {
        private static BlockPosition from(Block block) {
            return new BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
