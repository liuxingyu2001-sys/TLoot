package com.tloot.storage;

import com.tloot.data.Treasure;

import java.util.Collection;
import java.util.Map;

public interface StorageBackend {

    void init();

    Map<String, Treasure> loadAll();

    void save(Treasure treasure);

    void delete(String id);

    void saveAll(Collection<Treasure> treasures);

    void close();
}
