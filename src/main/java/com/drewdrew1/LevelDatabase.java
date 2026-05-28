package com.drewdrew1;

import com.drewdrew1.api.Database;
import com.drewdrew1.api.Migration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LevelDatabase {
    private final Database database;

    public LevelDatabase(Database database) {
        this.database = database;
    }

    public CompletableFuture<Void> migrate() {
        return database.migrate(List.of(
                Migration.sql(1, "create player levels table", """
                        CREATE TABLE IF NOT EXISTS player_levels (
                            uuid TEXT NOT NULL,
                            skill TEXT NOT NULL,
                            level INTEGER NOT NULL DEFAULT 0,
                            exp REAL NOT NULL DEFAULT 0,
                            updated_at INTEGER NOT NULL DEFAULT (CAST(strftime('%s','now') AS INTEGER)),
                            PRIMARY KEY (uuid, skill)
                        );
                        """),
                Migration.sql(2, "create claimed rewards table", """
                        CREATE TABLE IF NOT EXISTS player_level_rewards (
                            uuid TEXT NOT NULL,
                            skill TEXT NOT NULL,
                            reward_level INTEGER NOT NULL,
                            claimed_at INTEGER NOT NULL DEFAULT (CAST(strftime('%s','now') AS INTEGER)),
                            PRIMARY KEY (uuid, skill, reward_level)
                        );
                        """),
                Migration.sql(3, "index player levels by skill", """
                        CREATE INDEX IF NOT EXISTS idx_player_levels_skill_level
                        ON player_levels(skill, level);
                        """)
        ));
    }

    public CompletableFuture<Void> saveLevel(UUID uuid, String skill, int level, double exp) {
        return database.update(
                """
                        INSERT INTO player_levels(uuid, skill, level, exp, updated_at)
                        VALUES(?, ?, ?, ?, CAST(strftime('%s','now') AS INTEGER))
                        ON CONFLICT(uuid, skill) DO UPDATE SET
                            level = excluded.level,
                            exp = excluded.exp,
                            updated_at = excluded.updated_at
                        """,
                statement -> {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, skill);
                    statement.setInt(3, level);
                    statement.setDouble(4, exp);
                }
        ).thenApply(updatedRows -> null);
    }

    public CompletableFuture<Optional<LevelData>> loadLevel(UUID uuid, String skill) {
        return database.queryOne(
                """
                        SELECT uuid, skill, level, exp
                        FROM player_levels
                        WHERE uuid = ? AND skill = ?
                        """,
                statement -> {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, skill);
                },
                resultSet -> new LevelData(
                        UUID.fromString(resultSet.getString("uuid")),
                        resultSet.getString("skill"),
                        resultSet.getInt("level"),
                        resultSet.getDouble("exp")
                )
        );
    }

    public CompletableFuture<Void> markRewardClaimed(UUID uuid, String skill, int rewardLevel) {
        return claimRewardIfAbsent(uuid, skill, rewardLevel).thenApply(claimed -> null);
    }

    public CompletableFuture<Boolean> claimRewardIfAbsent(UUID uuid, String skill, int rewardLevel) {
        return database.update(
                """
                        INSERT OR IGNORE INTO player_level_rewards(uuid, skill, reward_level, claimed_at)
                        VALUES(?, ?, ?, CAST(strftime('%s','now') AS INTEGER))
                        """,
                statement -> {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, skill);
                    statement.setInt(3, rewardLevel);
                }
        ).thenApply(updatedRows -> updatedRows > 0);
    }

    public CompletableFuture<Boolean> hasRewardClaimed(UUID uuid, String skill, int rewardLevel) {
        return database.queryOne(
                """
                        SELECT 1
                        FROM player_level_rewards
                        WHERE uuid = ? AND skill = ? AND reward_level = ?
                        """,
                statement -> {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, skill);
                    statement.setInt(3, rewardLevel);
                },
                resultSet -> true
        ).thenApply(Optional::isPresent);
    }
}
