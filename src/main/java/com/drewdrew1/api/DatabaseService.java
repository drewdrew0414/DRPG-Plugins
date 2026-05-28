package com.drewdrew1.api;

import java.nio.file.Path;
import java.util.Collection;

public interface DatabaseService extends AutoCloseable {
    Database database(String name);

    Path databaseDirectory();

    Collection<String> openDatabaseNames();

    @Override
    void close();
}
