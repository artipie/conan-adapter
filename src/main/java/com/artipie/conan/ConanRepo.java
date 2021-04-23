package com.artipie.conan;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import io.reactivex.Completable;

public class ConanRepo {

    /**
     * Primary storage.
     */
    private final Storage storage;

    /**
     * Repository configuration.
     */
    private final ConanRepoConfig config;

    public ConanRepo(Storage storage, ConanRepoConfig config) {
        this.storage = storage;
        this.config = config;
    }

    /**
     * Updates repository incrementally.
     * @param prefix Repo prefix
     * @return Completable action
     */
    public Completable batchUpdateIncrementally(final Key prefix) {

        return null;
    }
}
