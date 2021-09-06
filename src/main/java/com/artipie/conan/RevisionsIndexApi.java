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
import java.util.concurrent.CompletableFuture;
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
     * Revisions index file name.
     */
    private static final String INDEX_FILE = "revisions.txt";

    /**
     * Package recipe (sources) subdir name.
     */
    private static final String SRC_SUBDIR = "export";

    /**
     * Package binaries subdir name.
     */
    private static final String BIN_SUBDIR = "package";

    /**
     * RevisionsIndex core logic.
     */
    private final RevisionsIndexCore core;

    /**
     * Revision info indexer.
     */
    private final RevisionsIndexer indexer;

    /**
     * Revision info indexer.
     */
    private final FullIndexer fullindexer;

    /**
     * Current Artipie storage instance.
     */
    private final Storage storage;

    /**
     * Package path for repository data.
     */
    private final String pkg;

    /**
     * Initializes new instance.
     * @param storage Current Artipie storage instance.
     * @param pkg Package path (full name).
     */
    public RevisionsIndexApi(final Storage storage, final String pkg) {
        this.storage = storage;
        this.pkg = pkg;
        this.core = new RevisionsIndexCore(storage);
        this.indexer = new RevisionsIndexer(storage);
        this.fullindexer = new FullIndexer(storage, this.indexer);
    }

    /**
     * Updates recipe index file, non recursive, doesn't affect package binaries.
     * @return CompletableFuture with recipe revisions list.
     */
    public CompletionStage<List<Integer>> updateRecipeIndex() {
        return this.doWithLock(
            new Key.From(this.pkg), () -> this.indexer.buildIndex(
                this.pkg, PkgList.PKG_SRC_LIST, (name, rev) -> String.join(
                    "/", this.pkg, rev.toString(), RevisionsIndexApi.SRC_SUBDIR, name
                )
            )
        );
    }

    /**
     * Updates binary index file.(WIP).
     * @param reciperev Recipe revision number.
     * @param hash Target package binary hash.
     * @return CompletableFuture with recipe revisions list.
     */
    public CompletionStage<List<Integer>> updateBinaryIndex(final int reciperev,
        final String hash) {
        final String path = String.join(
            "/", this.pkg, Integer.toString(reciperev), RevisionsIndexApi.BIN_SUBDIR, hash
        );
        return this.indexer.buildIndex(
            path, PkgList.PKG_BIN_LIST, (name, rev) -> String.join(
                "/", path, rev.toString(), name
            )
        );
    }

    /**
     * Add new revision to the recipe index.
     * @param revision Revision number.
     * @return CompletionStage for this operation.
     */
    public CompletionStage<Void> addRecipeRevision(final int revision) {
        final String path = this.getRecipeRevpath();
        return this.doWithLock(
            new Key.From(path), () -> this.core.addToRevdata(revision, path)
        );
    }

    /**
     * Returns list of revisions for the recipe.
     * @return CompletionStage with the list.
     */
    public CompletionStage<List<Integer>> getRecipeRevisions() {
        final String path = this.getRecipeRevpath();
        return this.core.getRevisions(path);
    }

    /**
     * Returns list of revisions for the package binary.
     * @param reciperev Recipe revision number.
     * @param hash Target package binary hash.
     * @return CompletionStage with the list.
     */
    public CompletionStage<List<Integer>> getBinaryRevisions(final int reciperev,
        final String hash) {
        final String path = this.getBinaryRevpath(reciperev, hash);
        return this.core.getRevisions(path);
    }

    /**
     * Removes specified revision from index file of package recipe.
     * @param revision Revision number.
     * @return CompletionStage with boolean == true if recipe & revision were found.
     */
    public CompletionStage<Boolean> removeRecipeRevision(final int revision) {
        final String path = this.getRecipeRevpath();
        return this.doWithLock(
            new Key.From(path), () -> this.core.removeRevision(revision, path)
        );
    }

    /**
     * Creates path to the recipe revisions file. Should be private later.
     * @return Recipe revisions index file in the storage, as String.
     */
    public String getRecipeRevpath() {
        return String.join(
            "", this.pkg, "/",
            RevisionsIndexApi.INDEX_FILE
        );
    }

    /**
     * Returns last (max) recipe revision value.
     * @return CompletableFuture with recipe revision as Integer.
     */
    public CompletableFuture<Integer> getLastRecipeRevision() {
        final String path = this.getRecipeRevpath();
        return this.core.getLastRev(path);
    }

    /**
     * Returns last (max) revision number for binary revision index.
     * @param reciperev Recipe revision number.
     * @param hash Target package binary hash.
     * @return CompletableFuture with recipe revision as Integer.
     */
    public CompletableFuture<Integer> getLastBinaryRevision(final int reciperev,
        final String hash) {
        final String path = this.getBinaryRevpath(reciperev, hash);
        return this.core.getLastRev(path);
    }

    /**
     * Creates path to the binary revisions file. Should be private later.
     * @param reciperev Recipe revision number.
     * @param hash Target package binary hash.
     * @return Binary revisions index file in the storage, as String.
     */
    public String getBinaryRevpath(final int reciperev, final String hash) {
        return String.join(
            "", this.pkg, "/", Integer.toString(reciperev), "/",
            RevisionsIndexApi.BIN_SUBDIR, "/", hash, "/", RevisionsIndexApi.INDEX_FILE
        );
    }

    /**
     * Updates binary index file. Fully recursive.
     * Does updateRecipeIndex(), then for each revision & for each pkg binary updateBinaryIndex().
     * @return CompletionStage to handle operation completion.
     */
    public CompletionStage<Void> fullIndexUpdate() {
        final String path = String.join("", this.pkg);
        return this.doWithLock(
            new Key.From(path), () -> this.fullindexer.fullIndexUpdate(path)
        );
    }

    /**
     * Removes specified revision from index file of package binary.
     * @param reciperev Recipe revision number.
     * @param hash Target package binary hash.
     * @param revision Revision number of the binary.
     * @return CompletionStage with boolean == true if recipe & revision were found.
     */
    public CompletionStage<Boolean> removeBinaryRevision(final int reciperev, final String hash,
        final int revision) {
        final String path = this.getBinaryRevpath(reciperev, hash);
        return this.doWithLock(
            new Key.From(path), () -> this.core.removeRevision(revision, path)
        );
    }

    /**
     * Returns binary packages list (of hashes) for given recipe revision.
     * @param reciperev Revision number of the recipe.
     * @return CompletionStage with the list of package binaries (hashes) as strings.
     */
    public CompletionStage<List<String>> getPackageList(final int reciperev) {
        final String path = String.join(
            "", this.pkg, "/", Integer.toString(reciperev), "/",
            RevisionsIndexApi.BIN_SUBDIR
        );
        return new PkgList(this.storage).getPackageList(path);
    }

    /**
     * Add binary revision to the index.
     * @param reciperev Recipe revision number.
     * @param hash Package binary hash.
     * @param revision Package binary revision.
     * @return CompletionStage to handle operation completion.
     */
    public CompletionStage<Void> addBinaryRevision(final int reciperev, final String hash,
        final int revision) {
        final String path = this.getBinaryRevpath(reciperev, hash);
        return this.doWithLock(
            new Key.From(path), () -> this.core.addToRevdata(revision, path)
        );
    }

    /**
     * Performs operation under lock on target with one hour expiration time.
     * @param target Lock target key.
     * @param operation Operation.
     * @param <T> Return type for operation's CompletableFuture.
     * @return Completion of operation and lock.
     */
    private <T> CompletableFuture<T> doWithLock(final Key target,
        final Supplier<CompletionStage<T>> operation) {
        final Lock lock = new StorageLock(
            this.storage, target, Instant.now().plus(Duration.ofHours(1))
        );
        return lock.acquire().thenCompose(
            nothing -> operation.get().thenApply(
                result -> {
                    lock.release();
                    return result;
                })).toCompletableFuture();
    }
}
