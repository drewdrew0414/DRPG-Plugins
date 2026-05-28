package com.drewdrew1.commodity;

import com.drewdrew1.api.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CommodityRepository {
    private static final String CREATE_PLAYERS = """
//            CREATE TABLE IF NOT EXISTS commodity_players (
//                player_uuid TEXT PRIMARY KEY,
//                player_name TEXT NOT NULL,
//                last_seen INTEGER NOT NULL DEFAULT 0,
                offline_processed_at INTEGER NOT NULL DEFAULT 0
            )
            """;

    private static final String CREATE_BALANCES = """
            CREATE TABLE IF NOT EXISTS commodity_balances (
                player_uuid TEXT NOT NULL,
                commodity TEXT NOT NULL,
                amount INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (player_uuid, commodity)
            )
            """;

    private final Database database;

    public CommodityRepository(Database database) {
        this.database = database;
    }

    public CompletableFuture<Void> initialize() {
        return database.execute(CREATE_PLAYERS)
                .thenCompose(ignored -> database.execute(CREATE_BALANCES));
    }

    public CompletableFuture<PlayerCommodityData> loadPlayer(
            UUID playerId,
            String playerName,
            CommoditySettings settings,
            long now,
            boolean processOffline
    ) {
        String uuid = playerId.toString();
        String safeName = safeName(playerName, playerId);
        return database.transaction(connection -> {
            PlayerTiming timing = ensurePlayer(connection, uuid, safeName, now);
            Map<String, Long> amounts = ensureBalances(connection, uuid, settings, now);
            if (processOffline) {
                applyOfflineIncrease(connection, uuid, settings, amounts, timing, now);
                updatePlayerTiming(connection, uuid, safeName, now, now);
            } else {
                updatePlayerName(connection, uuid, safeName);
            }
            return new PlayerCommodityData(playerId, safeName, amounts);
        });
    }

    public CompletableFuture<CommodityChangeResult> changeAmount(
            UUID playerId,
            String playerName,
            CommoditySettings settings,
            CommodityDefinition definition,
            CommodityOperation operation,
            long operand,
            long now
    ) {
        String uuid = playerId.toString();
        String safeName = safeName(playerName, playerId);
        return database.transaction(connection -> {
            PlayerTiming timing = ensurePlayer(connection, uuid, safeName, now);
            Map<String, Long> amounts = ensureBalances(connection, uuid, settings, now);
            applyOfflineIncrease(connection, uuid, settings, amounts, timing, now);

            long oldAmount = amounts.getOrDefault(definition.key(), definition.defaultValue());
            long rawNewAmount = switch (operation) {
                case SET -> operand;
                case ADD -> safeAdd(oldAmount, operand);
                case SUBTRACT -> safeAdd(oldAmount, -operand);
                case RESET -> definition.defaultValue();
            };
            long newAmount = definition.clamp(rawNewAmount);
            updateBalance(connection, uuid, definition.key(), newAmount, now);
            amounts.put(definition.key(), newAmount);
            updatePlayerTiming(connection, uuid, safeName, now, now);

            return new CommodityChangeResult(playerId, safeName, definition.key(), oldAmount, newAmount, amounts);
        });
    }

    public CompletableFuture<PlayerCommodityData> resetAll(
            UUID playerId,
            String playerName,
            CommoditySettings settings,
            long now
    ) {
        String uuid = playerId.toString();
        String safeName = safeName(playerName, playerId);
        return database.transaction(connection -> {
            PlayerTiming timing = ensurePlayer(connection, uuid, safeName, now);
            Map<String, Long> amounts = ensureBalances(connection, uuid, settings, now);
            applyOfflineIncrease(connection, uuid, settings, amounts, timing, now);

            for (CommodityDefinition definition : settings.definitions()) {
                updateBalance(connection, uuid, definition.key(), definition.defaultValue(), now);
                amounts.put(definition.key(), definition.defaultValue());
            }
            updatePlayerTiming(connection, uuid, safeName, now, now);
            return new PlayerCommodityData(playerId, safeName, amounts);
        });
    }

    public CompletableFuture<Void> markOffline(UUID playerId, String playerName, long now) {
        String uuid = playerId.toString();
        String safeName = safeName(playerName, playerId);
        return database.execute(
                """
                        INSERT INTO commodity_players (player_uuid, player_name, last_seen, offline_processed_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT(player_uuid) DO UPDATE SET
                            player_name = excluded.player_name,
                            last_seen = excluded.last_seen,
                            offline_processed_at = excluded.offline_processed_at
                        """,
                statement -> {
                    statement.setString(1, uuid);
                    statement.setString(2, safeName);
                    statement.setLong(3, now);
                    statement.setLong(4, now);
                }
        );
    }

    private PlayerTiming ensurePlayer(Connection connection, String uuid, String playerName, long now) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT last_seen, offline_processed_at FROM commodity_players WHERE player_uuid = ?"
        )) {
            select.setString(1, uuid);
            try (ResultSet result = select.executeQuery()) {
                if (result.next()) {
                    return new PlayerTiming(result.getLong("last_seen"), result.getLong("offline_processed_at"));
                }
            }
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO commodity_players (player_uuid, player_name, last_seen, offline_processed_at) VALUES (?, ?, ?, ?)"
        )) {
            insert.setString(1, uuid);
            insert.setString(2, playerName);
            insert.setLong(3, now);
            insert.setLong(4, now);
            insert.executeUpdate();
        }
        return new PlayerTiming(now, now);
    }

    private Map<String, Long> ensureBalances(
            Connection connection,
            String uuid,
            CommoditySettings settings,
            long now
    ) throws SQLException {
        Map<String, Long> amounts = new LinkedHashMap<>();
        for (CommodityDefinition definition : settings.definitions()) {
            insertMissingBalance(connection, uuid, definition, now);
            long amount = selectBalance(connection, uuid, definition);
            long clamped = definition.clamp(amount);
            if (amount != clamped) {
                updateBalance(connection, uuid, definition.key(), clamped, now);
            }
            amounts.put(definition.key(), clamped);
        }
        return amounts;
    }

    private void insertMissingBalance(
            Connection connection,
            String uuid,
            CommodityDefinition definition,
            long now
    ) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT OR IGNORE INTO commodity_balances (player_uuid, commodity, amount, updated_at) VALUES (?, ?, ?, ?)"
        )) {
            insert.setString(1, uuid);
            insert.setString(2, definition.key());
            insert.setLong(3, definition.defaultValue());
            insert.setLong(4, now);
            insert.executeUpdate();
        }
    }

    private long selectBalance(Connection connection, String uuid, CommodityDefinition definition) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT amount FROM commodity_balances WHERE player_uuid = ? AND commodity = ?"
        )) {
            select.setString(1, uuid);
            select.setString(2, definition.key());
            try (ResultSet result = select.executeQuery()) {
                if (result.next()) {
                    return result.getLong("amount");
                }
            }
        }
        return definition.defaultValue();
    }

    private void applyOfflineIncrease(
            Connection connection,
            String uuid,
            CommoditySettings settings,
            Map<String, Long> amounts,
            PlayerTiming timing,
            long now
    ) throws SQLException {
        long start = timing.offlineProcessedAt() > 0L ? timing.offlineProcessedAt() : timing.lastSeen();
        long elapsed = Math.max(0L, now - start);
        if (settings.maxOfflineCatchupSeconds() > 0L) {
            elapsed = Math.min(elapsed, settings.maxOfflineCatchupSeconds());
        }
        long units = elapsed / settings.offlineUnitSeconds();
        if (units <= 0L) {
            return;
        }

        for (CommodityDefinition definition : settings.definitions()) {
            if (definition.offlineIncrease() <= 0L) {
                continue;
            }
            long current = amounts.getOrDefault(definition.key(), definition.defaultValue());
            long increased = definition.clamp(safeAdd(current, safeMultiply(definition.offlineIncrease(), units)));
            if (current != increased) {
                updateBalance(connection, uuid, definition.key(), increased, now);
                amounts.put(definition.key(), increased);
            }
        }
    }

    private void updateBalance(Connection connection, String uuid, String commodity, long amount, long now) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                """
                        INSERT INTO commodity_balances (player_uuid, commodity, amount, updated_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT(player_uuid, commodity) DO UPDATE SET
                            amount = excluded.amount,
                            updated_at = excluded.updated_at
                        """
        )) {
            update.setString(1, uuid);
            update.setString(2, commodity);
            update.setLong(3, amount);
            update.setLong(4, now);
            update.executeUpdate();
        }
    }

    private void updatePlayerTiming(Connection connection, String uuid, String playerName, long lastSeen, long processedAt) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                """
                        UPDATE commodity_players
                        SET player_name = ?, last_seen = ?, offline_processed_at = ?
                        WHERE player_uuid = ?
                        """
        )) {
            update.setString(1, playerName);
            update.setLong(2, lastSeen);
            update.setLong(3, processedAt);
            update.setString(4, uuid);
            update.executeUpdate();
        }
    }

    private void updatePlayerName(Connection connection, String uuid, String playerName) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE commodity_players SET player_name = ? WHERE player_uuid = ?"
        )) {
            update.setString(1, playerName);
            update.setString(2, uuid);
            update.executeUpdate();
        }
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return right >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static long safeMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static String safeName(String playerName, UUID playerId) {
        return playerName == null || playerName.isBlank() ? playerId.toString() : playerName;
    }

    private record PlayerTiming(long lastSeen, long offlineProcessedAt) {
    }
}
