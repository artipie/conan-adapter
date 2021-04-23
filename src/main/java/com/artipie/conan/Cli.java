package com.artipie.conan;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.fs.FileStorage;

import java.nio.file.Path;

public final class Cli {
    public static void main(final String... args) {
        Path path = Path.of(".");
        Storage storage = new FileStorage(path);
        ConanRepo repo = new ConanRepo(storage, new ConanRepoConfig.Simple());
        repo.batchUpdateIncrementally(Key.ROOT);
        System.out.println(path.toAbsolutePath());
    }
}
