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

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.lock.Lock;
import com.artipie.asto.lock.storage.StorageLock;
import com.google.common.base.Strings;
import io.vavr.Tuple2;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;

/**
 * Conan V2 API revisions index. Revisions index stored in revisions.txt file in json format.
 * There are 2+ index files: recipe revisions and binary revisions (per package).
 * @since 0.1
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class RevisionsIndex {

    /**
     * Manifest file stores list of package files with their hashes.
     */
    private static final String CONAN_MANIFEST = "conanmanifest.txt";

    /**
     * File with binary package information on corresponding build configuration.
     */
    private static final String CONAN_INFO = "conaninfo.txt";

    /**
     * Main files of package recipe.
     */
    private static final List<String> PKG_SRC_LIST = Collections.unmodifiableList(
        Arrays.asList(
            RevisionsIndex.CONAN_MANIFEST, "conan_export.tgz", "conanfile.py", "conan_sources.tgz"
        ));

    /**
     * Main files of package binary.
     */
    private static final List<String> PKG_BIN_LIST = Collections.unmodifiableList(
        Arrays.asList(
            RevisionsIndex.CONAN_MANIFEST, RevisionsIndex.CONAN_INFO, "conan_package.tgz"
        ));

    /**
     * Revisions json field.
     */
    private static final String REVISIONS = "revisions";

    /**
     * Revision json field.
     */
    private static final String REVISION = "revision";

    /**
     * Timestamp json field. Uses ISO 8601 format.
     */
    private static final String TIMESTAMP = "time";

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
     * Current Artipie storage instance.
     */
    private final Storage storage;

    /**
     * Package path for repository data.
     */
    private final String pkg;

    /**
     * Initializes new instance.
     * @param storage Artipie storage instance.
     * @param pkg Package path (full name).
     */
    public RevisionsIndex(final Storage storage, final String pkg) {
        this.storage = storage;
        this.pkg = pkg;
    }

    /**
     * Updates recipe index file, non recursive, doesn't affect package binaries.
     * @return CompletableFuture with recipe revisions list.
     */
    @SuppressWarnings("unchecked")
    public CompletionStage<List<Integer>> updateRecipeIndex() {
        final String path = String.join("", this.pkg);
        return this.doWithLock(
            new Key.From(path), () -> this.buildIndex(
                path, RevisionsIndex.PKG_SRC_LIST, (name, rev) -> String.join(
                    "", path, "/", rev.toString(), "/", RevisionsIndex.SRC_SUBDIR, "/", name
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
            "", this.pkg, "/", Integer.toString(reciperev),
            "/", RevisionsIndex.BIN_SUBDIR, "/", hash
        );
        return this.buildIndex(
            path, RevisionsIndex.PKG_BIN_LIST, (name, rev) -> String.join(
                "", path, "/", rev.toString(), "/", name
            )
        );
    }

    /**
     * Rebuilds specified revision index (WIP). Extracts revisions lists, check files presense,
     * then creates revision index files with valid revision numbers.
     * @param path Index file directory path (package path).
     * @param pkgfiles Package files list for verification.
     * @param generator Generates full path to one of the pkgfiles. (name, rev) -> path.
     * @return CompletableFuture with recipe revisions list.
     */
    @SuppressWarnings("PMD.UseVarargs")
    private CompletableFuture<List<Integer>> buildIndex(final String path,
        final List<String> pkgfiles, final BiFunction<String, Integer, String> generator) {
        final CompletableFuture<List<Integer>> revisions = this.storage.list(new Key.From(path))
            .thenCompose(
                pkgkeys -> {
                    final List<Tuple2<Integer, CompletableFuture<Boolean>>> revchecks =
                        RevisionsIndex.extractPkgRevisions(path, pkgkeys).stream().map(
                            rev -> new Tuple2<>(
                                rev, this.checkPkgRevValid(rev, pkgfiles, generator)
                            )
                        ).collect(Collectors.toList());
                    return new Completables.JoinTuples<>(revchecks).toTuples().thenApply(
                        checks -> checks.stream().filter(Tuple2::_2).map(Tuple2::_1)
                            .collect(Collectors.toList())
                    );
                }).toCompletableFuture();
        return revisions.thenCompose(
            revs -> {
                final JsonArrayBuilder builder = Json.createArrayBuilder();
                revs.stream().map(
                    rev -> Json.createObjectBuilder()
                        .add(RevisionsIndex.REVISION, rev.toString())
                        .add(RevisionsIndex.TIMESTAMP, Instant.now().toString())
                        .build()
                ).forEach(builder::add);
                final String revpath = String.join(
                    "", path, "/", RevisionsIndex.INDEX_FILE
                );
                return this.storage.save(
                    new Key.From(revpath), RevisionsIndex.revContent(builder.build())
                ).thenApply(nothing -> revs);
            });
    }

    /**
     * Checks that package revision contents is valid.
     * @param rev Revision number in the package.
     * @param pkgfiles Package files list for verification.
     * @param generator Generates full path to one of the pkgfiles. (name, rev) -> path.
     * @return CompletableFuture with package validity result.
     */
    private CompletableFuture<Boolean> checkPkgRevValid(final Integer rev,
        final List<String> pkgfiles, final BiFunction<String, Integer, String> generator) {
        final List<CompletableFuture<Boolean>> checks = pkgfiles.stream().map(
            name -> this.storage.exists(
                new Key.From(generator.apply(name, rev))
            )).collect(Collectors.toList());
        return new Completables.JoinList<>(checks).toList().thenApply(
            results -> results.stream().allMatch(v -> v)
        );
    }

    /**
     * Extracts next subdir in key, starting from the base path.
     * @param base Base path for key.
     * @param key Artipie storage key with full path.
     * @return Next subdir name after base, or empty string if none.
     */
    private static String getNextSubdir(final String base, final Key key) {
        final int next = key.string().indexOf('/', base.length() + 1);
        final String result;
        if (next < 0) {
            result = "";
        } else {
            result = key.string().substring(base.length() + 1, next);
        }
        return result;
    }

    /**
     * Extracts revisions list for provided storage key path and list of its keys.
     * @param base Base key path for the package.
     * @param keys Artipie storage keys for the package.
     * @return Revision number value, or -1 if none.
     */
    private static Set<Integer> extractPkgRevisions(final String base, final Collection<Key> keys) {
        return keys.stream().map(
            key -> {
                final String subdir = RevisionsIndex.getNextSubdir(base, key);
                final int result;
                if (Strings.isNullOrEmpty(subdir)) {
                    result = -1;
                } else {
                    result = Integer.parseInt(subdir);
                }
                return result;
            }).filter(rev -> rev >= 0).collect(Collectors.toSet());
    }

    /**
     * Creates revisions content object for array of revisions.
     * @param revcontent Array of revisions.
     * @return Artipie Content object with revisions data.
     */
    private static Content revContent(final JsonArray revcontent) {
        return new Content.From(Json.createObjectBuilder().add(RevisionsIndex.REVISIONS, revcontent)
            .build().toString().getBytes(StandardCharsets.UTF_8)
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
        final Supplier<CompletableFuture<T>> operation) {
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
