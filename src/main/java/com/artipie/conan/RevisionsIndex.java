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
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.lock.Lock;
import com.artipie.asto.lock.storage.StorageLock;
import hu.akarnokd.rxjava2.interop.FlowableInterop;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import io.vavr.Tuple2;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.stream.JsonParser;

/**
 * Conan V2 API revisions index. Revisions index stored in revisions.txt file in json format.
 * There are 2+ index files: recipe revisions and binary revisions (per package).
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (999 lines)
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
    private static final String[] PKG_SRC_LIST = {
        RevisionsIndex.CONAN_MANIFEST, "conan_export.tgz", "conanfile.py", "conan_sources.tgz",
    };

    /**
     * Main files of package binary.
     */
    private static final String[] PKG_BIN_LIST = {
        RevisionsIndex.CONAN_MANIFEST, RevisionsIndex.CONAN_INFO, "conan_package.tgz",
    };

    /**
     * Revisions json field.
     */
    private static final String REVISIONS = "revisions";

    /**
     * Revision json field.
     */
    private static final String REVISION = "revision";

    /**
     * Timestamp json field.
     */
    private static final String TIMESTAMP = "timestamp";

    /**
     * Revisions index file name.
     */
    private static final String INDEX_FILE = "revisions.txt";

    /**
     * Package binaries subdir name.
     */
    private static final String PKG_SUBDIR = "package";

    /**
     * Current Artipie storage instance.
     */
    private final Storage storage;

    /**
     * Storage path prefix for the repository data.
     */
    private final Key prefix;

    /**
     * Package path for repository data.
     */
    private final String pkg;

    /**
     * Initializes new instance.
     * @param storage Artipie storage instance.
     * @param prefix Storage prefix, path to the repository data.
     * @param pkg Package path (full name).
     */
    public RevisionsIndex(final Storage storage, final Key prefix, final String pkg) {
        this.storage = storage;
        this.prefix = prefix;
        this.pkg = pkg;
    }

    /**
     * Add new revision to the recipe index.
     * @param revision Revision number.
     * @return CompletionStage for this operation.
     */
    public CompletionStage<Void> addRecipeRevision(final Integer revision) {
        final String path = this.getRecipeRevpath();
        return this.addToRevdata(revision, path);
    }

    /**
     * Returns list of revisions for the recipe.
     * @return CompletionStage with the list.
     */
    public CompletionStage<List<Integer>> getRecipeRevisions() {
        final String path = this.getRecipeRevpath();
        return this.loadRevisionData(new Key.From(path))
            .thenCompose(
                array -> {
                    final List<Integer> revs = array.stream().map(
                        value -> Integer.parseInt(
                            RevisionsIndex.getJsonStr(value, RevisionsIndex.REVISION)
                        )).collect(Collectors.toList());
                    return CompletableFuture.completedFuture(revs);
                });
    }

    /**
     * Removes specified revison from index file of package recipe.
     * @param revision Revision number.
     * @return CompletionStage with boolean == true if recipe & revision were found.
     */
    public CompletionStage<Boolean> removeRecipeRevision(final Integer revision) {
        final String path = this.getRecipeRevpath();
        return this.removeRevision(revision, path);
    }

    /**
     * Updates recipe index file, non recursive, doesn't affect package binaries.
     * @return CompletableFuture with recipe revisions list.
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<String>> updateRecipeIndex() {
        final String path = String.join("", this.prefix.string(), this.pkg);
        return this.doWithLock(
            new Key.From(path), () -> this.buildIndex(path, RevisionsIndex.PKG_SRC_LIST)
        );
    }

    /**
     * Creates path to the recipe revisions file. Should be private later.
     * @return Recipe revisions index file in the storage, as String.
     */
    public String getRecipeRevpath() {
        return String.join(
            "", this.prefix.string(), this.pkg, "/",
            RevisionsIndex.INDEX_FILE
        );
    }

    /**
     * Returns last (max) recipe revision value.
     * @return CompletableFuture with recipe revision as Integer.
     */
    public CompletableFuture<Integer> getLastRecipeRevision() {
        final String path = this.getRecipeRevpath();
        return this.getLastRev(path);
    }

    /**
     * Loads revisions data array from index file. Should be private later.
     * @param revpath Path to revisions index.
     * @return CompletableFuture with revisions data as JsonArray.
     */
    public CompletableFuture<JsonArray> loadRevisionData(final Key revpath) {
        return this.storage.exists(revpath).thenCompose(
            exist -> {
                final CompletableFuture<JsonArray> revs;
                if (exist) {
                    revs = this.storage.value(revpath).thenCompose(
                        content -> new PublisherAs(content).asciiString().thenCompose(
                            string -> {
                                final JsonParser parser = Json.createParser(
                                    new StringReader(string)
                                );
                                parser.next();
                                final JsonArray revisions = parser.getObject()
                                    .getJsonArray(RevisionsIndex.REVISIONS);
                                return CompletableFuture.completedFuture(revisions);
                            }));
                } else {
                    revs = CompletableFuture.completedFuture(Json.createArrayBuilder().build());
                }
                return revs;
            }
        );
    }

    /**
     * Returns last (max) revision number for binary revision index.
     * @param reciperev Recipe revision number.
     * @param hash Target package binary hash.
     * @return CompletableFuture with recipe revision as Integer.
     */
    public CompletableFuture<Integer> getLastBinaryRevision(final String reciperev,
        final String hash) {
        final String path = this.getBinaryRevpath(reciperev, hash);
        return this.getLastRev(path);
    }

    /**
     * Creates path to the binary revisions file. Should be private later.
     * @param reciperev Recipe revision number.
     * @param hash Target package binary hash.
     * @return Binary revisions index file in the storage, as String.
     */
    public String getBinaryRevpath(final String reciperev, final String hash) {
        return String.join(
            "", this.prefix.string(), this.pkg, "/", reciperev, "/",
            RevisionsIndex.PKG_SUBDIR, "/", hash, "/", RevisionsIndex.INDEX_FILE
        );
    }

    /**
     * Updates binary index file.(WIP).
     * @param revision Recipe revision number.
     * @param hash Target package binary hash.
     * @return CompletableFuture with recipe revisions list.
     */
    public CompletableFuture<List<String>> updateBinaryIndex(final String revision,
        final String hash) {
        final String path = String.join(
            "", this.prefix.string(), this.pkg, "/", revision,
            "/", RevisionsIndex.PKG_SUBDIR, "/", hash
        );
        return this.buildIndex(path, RevisionsIndex.PKG_BIN_LIST);
    }

    /**
     * Updates binary index file. Fully recursive (WIP).
     * Does updateRecipeIndex(), then for each revision & for each pkg binary updateBinaryIndex().
     * @return CompletionStage to handle operation completion.
     */
    public CompletionStage<Void> fullIndexUpdate() {
        final String path = String.join("", this.prefix.string(), this.pkg);
        return this.doWithLock(
            new Key.From(path), () -> {
                final Flowable<List<String>> flowable = SingleInterop.fromFuture(
                    this.buildIndex(path, RevisionsIndex.PKG_SRC_LIST)
                ).flatMapPublisher(Flowable::fromIterable).flatMap(
                    rev -> {
                        final String packages = String.join(
                            "", path, "/", rev, "/", RevisionsIndex.PKG_SUBDIR
                        );
                        return SingleInterop.fromFuture(
                            this.storage.list(new Key.From(packages))
                                .thenCompose(
                                    files -> CompletableFuture.completedFuture(
                                        files.stream().map(file -> getNextSubdir(path, file))
                                            .filter(s -> s.length() > 0).collect(Collectors.toSet())
                                    )
                                )
                            )
                            .flatMapPublisher(Flowable::fromIterable);
                    })
                    .flatMap(
                        pkgpath -> FlowableInterop.fromFuture(
                            this.buildIndex(pkgpath, RevisionsIndex.PKG_BIN_LIST)
                        )
                    )
                    .parallel().runOn(Schedulers.io())
                    .sequential().observeOn(Schedulers.io());
                return fromFlowable(flowable).thenCompose(
                    unused ->
                        CompletableFuture.completedFuture(null)
                );
            }
        );
    }

    /**
     * Removes specified revison from index file of package binary.
     * @param reciperev Recipe revision number.
     * @param hash Target package binary hash.
     * @param revision Revision number of the binary.
     * @return CompletionStage with boolean == true if recipe & revision were found.
     */
    public CompletionStage<Boolean> removeBinaryRevision(final String reciperev, final String hash,
        final Integer revision) {
        final String path = this.getBinaryRevpath(reciperev, hash);
        return this.removeRevision(revision, path);
    }

    /**
     * Returns binary packages list (of hashes) for given recipe revision.
     * @param revision Revision number of the recipe.
     * @return CompletionStage with the list of package binaries (hashes) as strings.
     */
    public CompletionStage<List<String>> getPackageList(final Integer revision) {
        final String path = String.join(
            "", this.prefix.string(), this.pkg, "/", revision.toString(), "/",
            RevisionsIndex.PKG_SUBDIR
        );
        return this.storage.list(new Key.From(path)).thenCompose(
            keys -> CompletableFuture.completedFuture(
                new ArrayList<>(
                    keys.stream().map(key -> RevisionsIndex.getNextSubdir(path, key))
                        .filter(s -> s.length() > 0).collect(Collectors.toSet())
                )
            )
        );
    }

    /**
     * Add binary revision to the index.
     * @param reciperev Recipe revision number.
     * @param hash Package binary hash.
     * @param revision Package binary revision.
     * @return CompletionStage to handle operation completion.
     */
    public CompletionStage<Void> addBinaryRevision(final String reciperev, final String hash,
        final Integer revision) {
        final String path = this.getBinaryRevpath(reciperev, hash);
        return this.addToRevdata(revision, path);
    }

    /**
     * Rebuilds specified revision index (WIP).
     * @param path Index file path.
     * @param pkgfiles Package files list for verification.
     * @return CompletableFuture with recipe revisions list.
     */
    @SuppressWarnings("PMD.UseVarargs")
    private CompletableFuture<List<String>> buildIndex(final String path,
        final String[] pkgfiles) {
        final CompletableFuture<List<String>> list = this.storage.list(new Key.From(path))
            .thenCompose(
                keys -> {
                    final List<CompletableFuture<Tuple2<String, Boolean>>> revs =
                        keys.stream().map(key -> RevisionsIndex.getNextSubdir(path, key))
                            .filter(s -> s.length() > 0)
                            .collect(Collectors.toSet()).stream().map(
                                rev -> {
                                    final List<CompletableFuture<Boolean>> checks =
                                        Arrays.stream(pkgfiles).map(
                                            name -> {
                                                final String fullpath = String.join(
                                                    "", path, "/", rev, "/", name
                                                );
                                                return this.storage.exists(new Key.From(fullpath));
                                            }).collect(Collectors.toList());
                                    @SuppressWarnings("rawtypes") final CompletableFuture[] arr =
                                        checks.toArray(
                                        new CompletableFuture[0]
                                    );
                                    return CompletableFuture.allOf(arr).thenApply(
                                        nothing -> new Tuple2<>(
                                            rev, checks.stream().allMatch(CompletableFuture::join)
                                        ));
                                }).collect(Collectors.toList());
                    @SuppressWarnings("rawtypes") final CompletableFuture[] arr =
                        revs.toArray(new CompletableFuture[0]);
                    return CompletableFuture.allOf(arr).thenApply(
                        nothing -> revs.stream().filter(tuple -> tuple.join()._2())
                            .map(tuple -> tuple.join()._1()).collect(Collectors.toList())
                    );
                }).toCompletableFuture();
        return list.thenCompose(
            revs -> {
                final JsonArrayBuilder builder = Json.createArrayBuilder();
                revs.stream().map(
                    rev -> Json.createObjectBuilder()
                        .add(RevisionsIndex.REVISION, rev).add(RevisionsIndex.TIMESTAMP, "")
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
     * Returns last (max) index file revision value.
     * @param path Path to revisions index.
     * @return CompletableFuture with index file revision as Integer.
     */
    private CompletableFuture<Integer> getLastRev(final String path) {
        return this.loadRevisionData(new Key.From(path)).thenCompose(
            array -> {
                final Optional<JsonValue> max = array.stream().max(
                    (val1, val2) -> {
                        final String revx = val1.asJsonObject().getString(RevisionsIndex.REVISION);
                        final String revy = val2.asJsonObject().getString(RevisionsIndex.REVISION);
                        return Integer.parseInt(revx) - Integer.parseInt(revy);
                    });
                final Integer revision;
                if (max.isPresent()) {
                    revision = Integer.parseInt(
                        RevisionsIndex.getJsonStr(max.get(), RevisionsIndex.REVISION)
                    );
                } else {
                    revision = null;
                }
                return CompletableFuture.completedFuture(revision);
            });
    }

    /**
     * Creates java's CompletableFuture from reactivex's Flowable.
     * @param flowable Flowable object to wrap.
     * @param <T> The type of the items for Flowable/CompletableFuture.
     * @return CompletableFuture instance, which wraps flowable and provides its results.
     */
    private static <T> CompletableFuture<List<T>> fromFlowable(final Flowable<T> flowable) {
        final CompletableFuture<List<T>> future = new CompletableFuture<>();
        flowable.doOnError(future::completeExceptionally).toList()
            .toObservable().forEach(future::complete);
        return future;
    }

    /**
     * Extracts string from json object field.
     * @param object Json object.
     * @param key Object key to extract.
     * @return Json object field value as String.
     */
    private static String getJsonStr(final JsonValue object, final String key) {
        return object.asJsonObject().get(key).toString().replaceAll("\"", "");
    }

    /**
     * Removes specified revison from index file.
     * @param revision Revision number.
     * @param path Path to the index file.
     * @return CompletionStage with boolean == true if recipe & revision were found.
     */
    private CompletableFuture<Boolean> removeRevision(final Integer revision, final String path) {
        final Key key = new Key.From(path);
        return this.doWithLock(
            key, () -> this.storage.exists(key).thenCompose(
                exist -> {
                    final CompletableFuture<Boolean> revs;
                    if (exist) {
                        revs = this.storage.value(key).thenCompose(
                            content -> new PublisherAs(content).asciiString().thenCompose(
                                string -> this.removeRevData(string, revision, key)
                            )
                        );
                    } else {
                        revs = CompletableFuture.completedFuture(false);
                    }
                    return revs;
                })
        );
    }

    /**
     * Removes specified revison from index data.
     * @param content Index file data, as json string.
     * @param revision Revision number.
     * @param target Target file name for save.
     * @return CompletionStage with boolean == true if revision was found.
     */
    private CompletableFuture<Boolean> removeRevData(final String content, final Integer revision,
        final Key target) {
        final CompletableFuture<Boolean> result;
        final JsonParser parser = Json.createParser(new StringReader(content));
        parser.next();
        final JsonArray revisions = parser.getObject().getJsonArray(RevisionsIndex.REVISIONS);
        final int index = RevisionsIndex.jsonIndexOf(revisions, RevisionsIndex.REVISION, revision);
        final JsonArrayBuilder updated = Json.createArrayBuilder(revisions);
        if (index >= 0) {
            updated.remove(index);
            result = this.storage.save(target, RevisionsIndex.revContent(updated.build()))
                .thenCompose(nothing -> CompletableFuture.completedFuture(true));
        } else {
            result = CompletableFuture.completedFuture(false);
        }
        return result;
    }

    /**
     * Returns index of json element with key == targetValue.
     * @param array Json array to search.
     * @param key Array element key to search.
     * @param value Target value for key to search.
     * @return Index if json array, or -1 if not found.
     */
    private static int jsonIndexOf(final JsonArray array, final String key, final Object value) {
        int index = -1;
        for (int idx = 0; idx < array.size(); ++idx) {
            if (RevisionsIndex.getJsonStr(array.get(idx), key).equals(value.toString())) {
                index = idx;
                break;
            }
        }
        return index;
    }

    /**
     * Extracts next subdir in key, starting from the base path.
     * @param base Base path for key.
     * @param key Artipie storage key with full path.
     * @return Next subdir name after base, or empty string if none.
     */
    private static String getNextSubdir(final String base, final Key key) {
        final int next = key.string().indexOf(
            '/', base.length() + 1
        );
        final String result;
        if (next < 0) {
            result = "";
        } else {
            result = key.string().substring(base.length() + 1, next);
        }
        return result;
    }

    /**
     * Add new revision to the specified index file.
     * @param revision New revision number.
     * @param path Path to the revisions index file.
     * @return CompletionStage for this operation.
     */
    private CompletableFuture<Void> addToRevdata(final Integer revision, final String path) {
        return this.doWithLock(
            new Key.From(path), () -> {
                final Key key = new Key.From(path);
                return this.loadRevisionData(key).thenCompose(
                    array -> {
                        final int index = RevisionsIndex.jsonIndexOf(
                            array, RevisionsIndex.REVISION, revision
                        );
                        final JsonArrayBuilder updated = Json.createArrayBuilder(array);
                        if (index >= 0) {
                            updated.remove(index);
                        }
                        updated.add(RevisionsIndex.newRevision(revision));
                        return this.storage.save(key, RevisionsIndex.revContent(updated.build()));
                    });
            });
    }

    /**
     * Creates new revision object for json index file.
     * @param revision New revision number.
     * @return JsonObject with revision info.
     */
    private static JsonObject newRevision(final Integer revision) {
        return Json.createObjectBuilder().add(RevisionsIndex.REVISION, revision.toString())
            .add(RevisionsIndex.TIMESTAMP, Instant.now().toString()).build();
    }

    /**
     * Creates revisions content object for array of revisons.
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
            nothing -> operation.get().thenCompose(
                result -> {
                    lock.release();
                    return CompletableFuture.completedFuture(result);
                })).toCompletableFuture();
    }
}
