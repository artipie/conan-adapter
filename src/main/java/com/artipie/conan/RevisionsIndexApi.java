/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.conan;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.lock.Lock;
import com.artipie.asto.lock.storage.StorageLock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Conan V2 API - main revisions index APIs. Revisions index stored in revisions.txt file
 * in json format.
 * There are 2+ index files: recipe revisions and binary revisions (per package).
 * @since 0.1
 */
public final class RevisionsIndexApi {

    /**
     * Package recipe (sources) subdir name.
     */
    private static final String SRC_SUBDIR = "export";

    /**
     * Package binaries subdir name.
     */
    private static final String BIN_SUBDIR = "package";

    /**
     * Revision info indexer.
     */
    private final RevisionsIndexer indexer;

    /**
     * Current Artipie storage instance.
     */
    private final Storage storage;

    /**
     * Package key for repository data.
     */
    private final Key pkgkey;

    /**
     * Initializes new instance.
     * @param storage Current Artipie storage instance.
     * @param pkgkey Package key (full name).
     */
    public RevisionsIndexApi(final Storage storage, final Key pkgkey) {
        this.storage = storage;
        this.pkgkey = pkgkey;
        this.indexer = new RevisionsIndexer(storage);
    }

    /**
     * Updates recipe index file, non recursive, doesn't affect package binaries.
     * @return CompletableFuture with recipe revisions list.
     */
    public CompletionStage<List<Integer>> updateRecipeIndex() {
        return this.doWithLock(
            this.pkgkey, () -> this.indexer.buildIndex(
                this.pkgkey, PackageList.PKG_SRC_LIST, (name, rev) -> new Key.From(
                    this.pkgkey.string(), rev.toString(), RevisionsIndexApi.SRC_SUBDIR, name
                )
            ));
    }

    /**
     * Updates binary index file.
     * @param reciperev Recipe revision number.
     * @param hash Target package binary hash.
     * @return CompletableFuture with recipe revisions list.
     */
    public CompletionStage<List<Integer>> updateBinaryIndex(final int reciperev,
        final String hash) {
        final Key key = new Key.From(
            this.pkgkey.string(), Integer.toString(reciperev),
            RevisionsIndexApi.BIN_SUBDIR, hash
        );
        return this.doWithLock(
            this.pkgkey, () -> this.indexer.buildIndex(
                key, PackageList.PKG_BIN_LIST, (name, rev) -> new Key.From(
                    key.string(), rev.toString(), name
                )
            ));
    }

    /**
     * Performs operation under lock on target with one hour expiration time.
     * @param target Lock target key.
     * @param operation Operation.
     * @param <T> Return type for operation's CompletableFuture.
     * @return Completion of operation and lock.
     */
    private <T> CompletionStage<T> doWithLock(final Key target,
        final Supplier<CompletionStage<T>> operation) {
        final Lock lock = new StorageLock(
            this.storage, target, Instant.now().plus(Duration.ofHours(1))
        );
        return lock.acquire().thenCompose(
            nothing -> operation.get().thenApply(
                result -> {
                    lock.release();
                    return result;
                }));
    }
}
