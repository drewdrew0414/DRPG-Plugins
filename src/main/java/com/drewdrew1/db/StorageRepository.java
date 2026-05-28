package com.drewdrew1.db;

import com.drewdrew1.api.Database;
import com.drewdrew1.api.Migration;
import com.drewdrew1.config.StorageGuildConfig;
import com.drewdrew1.model.Guild;
import com.drewdrew1.model.GuildInvite;
import com.drewdrew1.model.GuildMember;
import com.drewdrew1.model.GuildRole;
import com.drewdrew1.model.StorageRecord;
import com.drewdrew1.storage.ItemStackCodec;
import com.drewdrew1.storage.StorageId;
import com.drewdrew1.storage.StorageType;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;

public final class StorageRepository {
    private final Database database;
    private final StorageGuildConfig config;

    public StorageRepository(Database database, StorageGuildConfig config) {
        this.database = database;
        this.config = config;
    }

    public void initialize() {
        database.migrate(List.of(new Migration(1, "initial storage and guild schema", this::createSchema))).join();
    }

    public CompletableFuture<StorageRecord> getOrCreatePersonalStorage(UUID playerUuid) {
        String storageKey = StorageId.personal(playerUuid).databaseKey();
        return database.transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT OR IGNORE INTO sg_storages(storage_key, type, owner_uuid, guild_id, unlocked_slots, updated_at)
                    VALUES(?, ?, ?, NULL, ?, ?)
                    """)) {
                insert.setString(1, storageKey);
                insert.setString(2, StorageType.PERSONAL.name());
                insert.setString(3, playerUuid.toString());
                insert.setInt(4, config.defaultStorageSlots());
                insert.setLong(5, System.currentTimeMillis());
                insert.executeUpdate();
            }
            return selectStorage(connection, storageKey).orElseThrow(() -> new SQLException("Personal storage was not created."));
        });
    }

    public CompletableFuture<StorageRecord> getOrCreateGuildStorage(long guildId) {
        String storageKey = StorageId.guild(guildId).databaseKey();
        return database.transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT OR IGNORE INTO sg_storages(storage_key, type, owner_uuid, guild_id, unlocked_slots, updated_at)
                    VALUES(?, ?, NULL, ?, ?, ?)
                    """)) {
                insert.setString(1, storageKey);
                insert.setString(2, StorageType.GUILD.name());
                insert.setLong(3, guildId);
                insert.setInt(4, config.defaultStorageSlots());
                insert.setLong(5, System.currentTimeMillis());
                insert.executeUpdate();
            }
            return selectStorage(connection, storageKey).orElseThrow(() -> new SQLException("Guild storage was not created."));
        });
    }

    public CompletableFuture<StorageRecord> expandStorage(String storageKey, int amount, int maxSlots) {
        return database.transaction(connection -> {
            StorageRecord storage = selectStorage(connection, storageKey)
                    .orElseThrow(() -> new SQLException("Storage not found."));
            int next = storage.unlockedSlots() + amount;
            if (next > maxSlots) {
                throw new SQLException("Storage is already at the maximum configured page limit.");
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE sg_storages SET unlocked_slots = ?, updated_at = ? WHERE storage_key = ?
                    """)) {
                update.setInt(1, next);
                update.setLong(2, System.currentTimeMillis());
                update.setString(3, storageKey);
                update.executeUpdate();
            }
            return selectStorage(connection, storageKey).orElseThrow(() -> new SQLException("Storage not found after expansion."));
        });
    }

    public CompletableFuture<Map<Integer, ItemStack>> loadItems(String storageKey) {
        return database.query("""
                SELECT slot_index, item_data FROM sg_storage_items WHERE storage_key = ?
                """, statement -> statement.setString(1, storageKey), resultSet -> {
            try {
                return Map.entry(resultSet.getInt("slot_index"), ItemStackCodec.decode(resultSet.getString("item_data")));
            } catch (IOException | ClassNotFoundException exception) {
                throw new SQLException("Failed to decode stored item.", exception);
            }
        }).thenApply(rows -> {
            Map<Integer, ItemStack> items = new LinkedHashMap<>();
            for (Map.Entry<Integer, ItemStack> row : rows) {
                items.put(row.getKey(), row.getValue());
            }
            return items;
        });
    }

    public CompletableFuture<Void> saveItems(String storageKey, Map<Integer, ItemStack> items) {
        return database.transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("""
                    DELETE FROM sg_storage_items WHERE storage_key = ?
                    """)) {
                delete.setString(1, storageKey);
                delete.executeUpdate();
            }

            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO sg_storage_items(storage_key, slot_index, item_data, updated_at)
                    VALUES(?, ?, ?, ?)
                    """)) {
                long now = System.currentTimeMillis();
                for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                    ItemStack item = entry.getValue();
                    if (item == null || item.getType().isAir()) {
                        continue;
                    }
                    insert.setString(1, storageKey);
                    insert.setInt(2, entry.getKey());
                    try {
                        insert.setString(3, ItemStackCodec.encode(item));
                    } catch (IOException exception) {
                        throw new SQLException("Failed to encode item.", exception);
                    }
                    insert.setLong(4, now);
                    insert.addBatch();
                }
                insert.executeBatch();
            }

            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE sg_storages SET updated_at = ? WHERE storage_key = ?
                    """)) {
                update.setLong(1, System.currentTimeMillis());
                update.setString(2, storageKey);
                update.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Guild> createGuild(UUID ownerUuid, String ownerName, String guildName) {
        return database.transaction(connection -> {
            long now = System.currentTimeMillis();
            long guildId;
            try (PreparedStatement insertGuild = connection.prepareStatement("""
                    INSERT INTO sg_guilds(name, owner_uuid, owner_name, created_at)
                    VALUES(?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                insertGuild.setString(1, guildName);
                insertGuild.setString(2, ownerUuid.toString());
                insertGuild.setString(3, ownerName);
                insertGuild.setLong(4, now);
                insertGuild.executeUpdate();

                try (ResultSet keys = insertGuild.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Guild id was not generated.");
                    }
                    guildId = keys.getLong(1);
                }
            }

            insertMember(connection, guildId, ownerUuid, ownerName, GuildRole.OWNER, now);
            insertStorage(connection, StorageId.guild(guildId).databaseKey(), StorageType.GUILD, null, guildId, config.defaultStorageSlots(), now);

            return new Guild(guildId, guildName, ownerUuid, ownerName, now);
        });
    }

    public CompletableFuture<Void> deleteGuild(long guildId) {
        return database.transaction(connection -> {
            try (PreparedStatement deleteItems = connection.prepareStatement("""
                    DELETE FROM sg_storage_items WHERE storage_key = ?
                    """)) {
                deleteItems.setString(1, StorageId.guild(guildId).databaseKey());
                deleteItems.executeUpdate();
            }
            try (PreparedStatement deleteStorage = connection.prepareStatement("""
                    DELETE FROM sg_storages WHERE storage_key = ?
                    """)) {
                deleteStorage.setString(1, StorageId.guild(guildId).databaseKey());
                deleteStorage.executeUpdate();
            }
            try (PreparedStatement deleteInvites = connection.prepareStatement("""
                    DELETE FROM sg_guild_invites WHERE guild_id = ?
                    """)) {
                deleteInvites.setLong(1, guildId);
                deleteInvites.executeUpdate();
            }
            try (PreparedStatement deleteMembers = connection.prepareStatement("""
                    DELETE FROM sg_guild_members WHERE guild_id = ?
                    """)) {
                deleteMembers.setLong(1, guildId);
                deleteMembers.executeUpdate();
            }
            try (PreparedStatement deleteGuild = connection.prepareStatement("""
                    DELETE FROM sg_guilds WHERE id = ?
                    """)) {
                deleteGuild.setLong(1, guildId);
                deleteGuild.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Optional<Guild>> findGuildByName(String name) {
        return database.queryOne("""
                SELECT id, name, owner_uuid, owner_name, created_at FROM sg_guilds WHERE name = ? COLLATE NOCASE
                """, statement -> statement.setString(1, name), this::mapGuild);
    }

    public CompletableFuture<Optional<Guild>> findGuildById(long guildId) {
        return database.queryOne("""
                SELECT id, name, owner_uuid, owner_name, created_at FROM sg_guilds WHERE id = ?
                """, statement -> statement.setLong(1, guildId), this::mapGuild);
    }

    public CompletableFuture<List<Guild>> guildsForMember(UUID playerUuid) {
        return database.query("""
                SELECT g.id, g.name, g.owner_uuid, g.owner_name, g.created_at
                FROM sg_guilds g
                JOIN sg_guild_members m ON m.guild_id = g.id
                WHERE m.player_uuid = ?
                ORDER BY g.name COLLATE NOCASE
                """, statement -> statement.setString(1, playerUuid.toString()), this::mapGuild);
    }

    public CompletableFuture<List<GuildMember>> members(long guildId) {
        return database.query("""
                SELECT guild_id, player_uuid, player_name, role, joined_at
                FROM sg_guild_members
                WHERE guild_id = ?
                ORDER BY role, player_name COLLATE NOCASE
                """, statement -> statement.setLong(1, guildId), this::mapMember);
    }

    public CompletableFuture<Optional<GuildMember>> findMember(long guildId, UUID playerUuid) {
        return database.queryOne("""
                SELECT guild_id, player_uuid, player_name, role, joined_at
                FROM sg_guild_members
                WHERE guild_id = ? AND player_uuid = ?
                """, statement -> {
            statement.setLong(1, guildId);
            statement.setString(2, playerUuid.toString());
        }, this::mapMember);
    }

    public CompletableFuture<Optional<GuildMember>> findMemberByName(long guildId, String playerName) {
        return database.queryOne("""
                SELECT guild_id, player_uuid, player_name, role, joined_at
                FROM sg_guild_members
                WHERE guild_id = ? AND player_name = ? COLLATE NOCASE
                """, statement -> {
            statement.setLong(1, guildId);
            statement.setString(2, playerName);
        }, this::mapMember);
    }

    public CompletableFuture<Void> createInvite(long guildId, UUID inviterUuid, UUID targetUuid, String targetName, long expiresAt) {
        return database.transaction(connection -> {
            if (selectMember(connection, guildId, targetUuid).isPresent()) {
                throw new SQLException("Target is already a guild member.");
            }
            long now = System.currentTimeMillis();
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO sg_guild_invites(guild_id, inviter_uuid, target_uuid, target_name, created_at, expires_at)
                    VALUES(?, ?, ?, ?, ?, ?)
                    ON CONFLICT(guild_id, target_uuid) DO UPDATE SET
                        inviter_uuid = excluded.inviter_uuid,
                        target_name = excluded.target_name,
                        created_at = excluded.created_at,
                        expires_at = excluded.expires_at
                    """)) {
                insert.setLong(1, guildId);
                insert.setString(2, inviterUuid.toString());
                insert.setString(3, targetUuid.toString());
                insert.setString(4, targetName);
                insert.setLong(5, now);
                insert.setLong(6, expiresAt);
                insert.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Guild> acceptInvite(UUID targetUuid, String targetName, String guildName) {
        return database.transaction(connection -> {
            Guild guild = selectGuildByName(connection, guildName)
                    .orElseThrow(() -> new SQLException("Guild not found."));
            GuildInvite invite = selectInvite(connection, guild.id(), targetUuid)
                    .orElseThrow(() -> new SQLException("Invite not found."));
            long now = System.currentTimeMillis();
            if (invite.expired(now)) {
                deleteInvite(connection, guild.id(), targetUuid);
                throw new SQLException("Invite expired.");
            }
            if (selectMember(connection, guild.id(), targetUuid).isEmpty()) {
                insertMember(connection, guild.id(), targetUuid, targetName, GuildRole.MEMBER, now);
            }
            deleteInvite(connection, guild.id(), targetUuid);
            return guild;
        });
    }

    public CompletableFuture<Void> denyInvite(UUID targetUuid, String guildName) {
        return database.transaction(connection -> {
            Optional<Guild> guild = selectGuildByName(connection, guildName);
            if (guild.isPresent()) {
                deleteInvite(connection, guild.get().id(), targetUuid);
            }
            return null;
        });
    }

    public CompletableFuture<List<Guild>> invitesFor(UUID targetUuid) {
        return database.query("""
                SELECT g.id, g.name, g.owner_uuid, g.owner_name, g.created_at
                FROM sg_guild_invites i
                JOIN sg_guilds g ON g.id = i.guild_id
                WHERE i.target_uuid = ? AND i.expires_at > ?
                ORDER BY i.created_at DESC
                """, statement -> {
            statement.setString(1, targetUuid.toString());
            statement.setLong(2, System.currentTimeMillis());
        }, this::mapGuild);
    }

    public CompletableFuture<Void> removeMember(long guildId, UUID targetUuid) {
        return database.transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("""
                    DELETE FROM sg_guild_members WHERE guild_id = ? AND player_uuid = ?
                    """)) {
                delete.setLong(1, guildId);
                delete.setString(2, targetUuid.toString());
                delete.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> updateRole(long guildId, UUID targetUuid, GuildRole role) {
        return database.transaction(connection -> {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE sg_guild_members SET role = ? WHERE guild_id = ? AND player_uuid = ?
                    """)) {
                update.setString(1, role.name());
                update.setLong(2, guildId);
                update.setString(3, targetUuid.toString());
                update.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> cleanupExpiredInvites() {
        return database.update("""
                DELETE FROM sg_guild_invites WHERE expires_at <= ?
                """, statement -> statement.setLong(1, System.currentTimeMillis())).thenApply(ignored -> null);
    }

    private Void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sg_storages(
                        storage_key TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        owner_uuid TEXT,
                        guild_id INTEGER,
                        unlocked_slots INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sg_storage_items(
                        storage_key TEXT NOT NULL,
                        slot_index INTEGER NOT NULL,
                        item_data TEXT NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(storage_key, slot_index),
                        FOREIGN KEY(storage_key) REFERENCES sg_storages(storage_key) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sg_guilds(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                        owner_uuid TEXT NOT NULL,
                        owner_name TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sg_guild_members(
                        guild_id INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        role TEXT NOT NULL,
                        joined_at INTEGER NOT NULL,
                        PRIMARY KEY(guild_id, player_uuid),
                        FOREIGN KEY(guild_id) REFERENCES sg_guilds(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_sg_guild_members_player
                    ON sg_guild_members(player_uuid)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sg_guild_invites(
                        guild_id INTEGER NOT NULL,
                        inviter_uuid TEXT NOT NULL,
                        target_uuid TEXT NOT NULL,
                        target_name TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL,
                        PRIMARY KEY(guild_id, target_uuid),
                        FOREIGN KEY(guild_id) REFERENCES sg_guilds(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_sg_guild_invites_target
                    ON sg_guild_invites(target_uuid, expires_at)
                    """);
        }
        return null;
    }

    private void insertStorage(Connection connection, String storageKey, StorageType type, UUID ownerUuid, Long guildId, int slots, long now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO sg_storages(storage_key, type, owner_uuid, guild_id, unlocked_slots, updated_at)
                VALUES(?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, storageKey);
            insert.setString(2, type.name());
            insert.setString(3, ownerUuid == null ? null : ownerUuid.toString());
            if (guildId == null) {
                insert.setNull(4, java.sql.Types.INTEGER);
            } else {
                insert.setLong(4, guildId);
            }
            insert.setInt(5, slots);
            insert.setLong(6, now);
            insert.executeUpdate();
        }
    }

    private Optional<StorageRecord> selectStorage(Connection connection, String storageKey) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT storage_key, type, owner_uuid, guild_id, unlocked_slots FROM sg_storages WHERE storage_key = ?
                """)) {
            select.setString(1, storageKey);
            try (ResultSet resultSet = select.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapStorage(resultSet));
            }
        }
    }

    private void insertMember(Connection connection, long guildId, UUID playerUuid, String playerName, GuildRole role, long now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO sg_guild_members(guild_id, player_uuid, player_name, role, joined_at)
                VALUES(?, ?, ?, ?, ?)
                """)) {
            insert.setLong(1, guildId);
            insert.setString(2, playerUuid.toString());
            insert.setString(3, playerName);
            insert.setString(4, role.name());
            insert.setLong(5, now);
            insert.executeUpdate();
        }
    }

    private Optional<Guild> selectGuildByName(Connection connection, String name) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, name, owner_uuid, owner_name, created_at FROM sg_guilds WHERE name = ? COLLATE NOCASE
                """)) {
            select.setString(1, name);
            try (ResultSet resultSet = select.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapGuild(resultSet));
            }
        }
    }

    private Optional<GuildMember> selectMember(Connection connection, long guildId, UUID playerUuid) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT guild_id, player_uuid, player_name, role, joined_at
                FROM sg_guild_members WHERE guild_id = ? AND player_uuid = ?
                """)) {
            select.setLong(1, guildId);
            select.setString(2, playerUuid.toString());
            try (ResultSet resultSet = select.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapMember(resultSet));
            }
        }
    }

    private Optional<GuildInvite> selectInvite(Connection connection, long guildId, UUID targetUuid) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT guild_id, inviter_uuid, target_uuid, target_name, created_at, expires_at
                FROM sg_guild_invites WHERE guild_id = ? AND target_uuid = ?
                """)) {
            select.setLong(1, guildId);
            select.setString(2, targetUuid.toString());
            try (ResultSet resultSet = select.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapInvite(resultSet));
            }
        }
    }

    private void deleteInvite(Connection connection, long guildId, UUID targetUuid) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("""
                DELETE FROM sg_guild_invites WHERE guild_id = ? AND target_uuid = ?
                """)) {
            delete.setLong(1, guildId);
            delete.setString(2, targetUuid.toString());
            delete.executeUpdate();
        }
    }

    private StorageRecord mapStorage(ResultSet resultSet) throws SQLException {
        String owner = resultSet.getString("owner_uuid");
        long guildId = resultSet.getLong("guild_id");
        boolean guildIdNull = resultSet.wasNull();
        return new StorageRecord(
                resultSet.getString("storage_key"),
                StorageType.valueOf(resultSet.getString("type")),
                owner == null ? null : UUID.fromString(owner),
                guildIdNull ? null : guildId,
                resultSet.getInt("unlocked_slots")
        );
    }

    private Guild mapGuild(ResultSet resultSet) throws SQLException {
        return new Guild(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                UUID.fromString(resultSet.getString("owner_uuid")),
                resultSet.getString("owner_name"),
                resultSet.getLong("created_at")
        );
    }

    private GuildMember mapMember(ResultSet resultSet) throws SQLException {
        return new GuildMember(
                resultSet.getLong("guild_id"),
                UUID.fromString(resultSet.getString("player_uuid")),
                resultSet.getString("player_name"),
                GuildRole.valueOf(resultSet.getString("role")),
                resultSet.getLong("joined_at")
        );
    }

    private GuildInvite mapInvite(ResultSet resultSet) throws SQLException {
        return new GuildInvite(
                resultSet.getLong("guild_id"),
                UUID.fromString(resultSet.getString("inviter_uuid")),
                UUID.fromString(resultSet.getString("target_uuid")),
                resultSet.getString("target_name"),
                resultSet.getLong("created_at"),
                resultSet.getLong("expires_at")
        );
    }
}
