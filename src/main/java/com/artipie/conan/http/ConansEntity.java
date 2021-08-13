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
package com.artipie.conan.http;

import com.artipie.asto.ArtipieIOException;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.http.rq.RequestLineFrom;
import io.vavr.Tuple2;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import org.ini4j.Wini;

/**
 * Conan /v1/conans/* REST APIs.
 * Conan recognizes two types of packages: package binary and package recipe (sources).
 * Package recipe ("source code") could be built to multiple package binaries with different
 * configuration (conaninfo.txt).
 * Artipie-conan storage structure for now corresponds to standard conan_server.
 * @since 0.1
 */
public final class ConansEntity {

    /**
     * Protocol type for download URIs.
     */
    private static final String PROTOCOL = "http://";

    /**
     * Subdir for package recipe (sources).
     */
    private static final String PKG_SRC_DIR = "/0/export/";

    /**
     * Subdir for package binaries.
     */
    private static final String PKG_BIN_DIR = "/0/package/";

    /**
     * Revision subdir name, for v1 Conan protocol its fixed. v2 Conan protocol is still WIP.
     */
    private static final String PKG_REV_DIR = "/0/";

    /**
     * Path part of the request URI.
     */
    private static final String URI_PATH = "path";

    /**
     * Hash (of the package binary) part of the request URI.
     */
    private static final String URI_HASH = "hash";

    /**
     * URL formatting string.
     */
    private static final String URL_FMT = "%1$s%2$s/%3$s";

    /**
     * File with binary package information on corresponding build configuration.
     */
    private static final String CONAN_INFO = "conaninfo.txt";

    /**
     * Manifest file stores list of package files with their hashes.
     */
    private static final String CONAN_MANIFEST = "conanmanifest.txt";

    /**
     * Main files of package binary.
     */
    private static final String[] PKG_BIN_LIST = new String[]{
        ConansEntity.CONAN_MANIFEST, ConansEntity.CONAN_INFO, "conan_package.tgz",
    };

    /**
     * Main files of package recipe.
     */
    private static final String[] PKG_SRC_LIST = new String[]{
        ConansEntity.CONAN_MANIFEST, "conan_export.tgz", "conanfile.py", "conan_sources.tgz",
    };

    /**
     * Only subclasses are instantiated.
     */
    private ConansEntity() {
    }

    /**
     * Conan /download_url REST APIs.
     *
     * @since 0.1
     */
    public static final class DownloadBin extends BaseConanSlice {

        /**
         * Ctor.
         *
         * @param storage Current Artipie storage instance.
         */
        public DownloadBin(final Storage storage) {
            super(storage, new PathWrap.DownloadBin());
        }

        @Override
        public CompletableFuture<RequestResult> getResult(final RequestLineFrom request,
            final String hostname, final Matcher matcher) {
            final String pkghash = matcher.group(ConansEntity.URI_HASH);
            final String uripath = matcher.group(ConansEntity.URI_PATH);
            return BaseConanSlice.generateJson(
                ConansEntity.PKG_BIN_LIST, file -> {
                    final Key key = new Key.From(
                        String.join(
                            "", uripath, ConansEntity.PKG_BIN_DIR, pkghash,
                            ConansEntity.PKG_REV_DIR, file
                        ));
                    return new Tuple2<>(key, this.getStorage().exists(key));
                }, tuple -> {
                    Optional<String> result = Optional.empty();
                    if (tuple._2()) {
                        result = Optional.of(
                            String.format(
                                ConansEntity.URL_FMT, ConansEntity.PROTOCOL, hostname, tuple._1()
                            )
                        );
                    }
                    return result;
                }, str -> String.join("", "{", str, "}")
            );
        }
    }

    /**
     * Conan /download_url REST APIs.
     * @since 0.1
     */
    public static final class DownloadSrc extends BaseConanSlice {

        /**
         * Ctor.
         * @param storage Current Artipie storage instance.
         */
        public DownloadSrc(final Storage storage) {
            super(storage, new PathWrap.DownloadSrc());
        }

        @Override
        public CompletableFuture<RequestResult> getResult(final RequestLineFrom request,
            final String hostname, final Matcher matcher) {
            final String uripath = matcher.group(ConansEntity.URI_PATH);
            return BaseConanSlice.generateJson(
                ConansEntity.PKG_SRC_LIST, file -> {
                    final Key key = new Key.From(
                        String.join(
                            "", uripath, ConansEntity.PKG_SRC_DIR, file
                    ));
                    return new Tuple2<>(key, this.getStorage().exists(key));
                }, tuple -> {
                    Optional<String> result = Optional.empty();
                    if (tuple._2()) {
                        result = Optional.of(
                            String.format(
                                ConansEntity.URL_FMT, ConansEntity.PROTOCOL, hostname, tuple._1()
                            ));
                    }
                    return result;
                }, str -> String.join("", "{", str, "}")
            );
        }
    }

    /**
     * Conan /search REST APIs for package binaries.
     * @since 0.1
     * @checkstyle ClassDataAbstractionCouplingCheck (99 lines)
     */
    public static final class GetSearchBinPkg extends BaseConanSlice {

        /**
         * Ctor.
         * @param storage Current Artipie storage instance.
         */
        public GetSearchBinPkg(final Storage storage) {
            super(storage, new PathWrap.SearchBinPkg());
        }

        @Override
        public CompletableFuture<RequestResult> getResult(final RequestLineFrom request,
            final String hostname, final Matcher matcher) {
            final String uripath = matcher.group(ConansEntity.URI_PATH);
            final String pkgpath = String.join("", uripath, ConansEntity.PKG_BIN_DIR);
            return this.getStorage().list(new Key.From(pkgpath)).thenCompose(
                keys -> this.findPackageInfo(keys, pkgpath)
            ).thenApply(RequestResult::new);
        }

        /**
         * Converts Conan package binary info to json.
         * @param content Conan conaninfo.txt contents.
         * @param jsonbuilder Target to fill with json data.
         * @param pkghash Conan package hash value.
         * @return CompletableFuture, providing json String with package info.
         * @throws IOException In case of conaninfo.txt contents access problems.
         */
        private static CompletableFuture<String> pkgInfoToJson(
            final com.artipie.asto.Content content,
            final JsonObjectBuilder jsonbuilder,
            final String pkghash
        ) throws IOException {
            final CompletableFuture<String> result = new PublisherAs(content)
                .string(StandardCharsets.UTF_8).thenApply(
                    data -> {
                        final Wini conaninfo;
                        try {
                            conaninfo = new Wini(new StringReader(data));
                        } catch (final IOException exception) {
                            throw new ArtipieIOException(exception);
                        }
                        final JsonObjectBuilder pkgbuilder = Json.createObjectBuilder();
                        conaninfo.forEach(
                            (secname, section) -> {
                                final JsonObjectBuilder jsection = section.entrySet().stream()
                                    .filter(e -> e.getValue() != null).collect(
                                        Json::createObjectBuilder, (js, e) ->
                                            js.add(e.getKey(), e.getValue()),
                                        (js1, js2) -> {
                                        }
                                    );
                                pkgbuilder.add(secname, jsection);
                            });
                        final String hashfield = "recipe_hash";
                        final String hashvalue = conaninfo.get(hashfield).keySet()
                            .iterator().next();
                        pkgbuilder.add(hashfield, hashvalue);
                        jsonbuilder.add(pkghash, pkgbuilder);
                        return jsonbuilder.build().toString();
                    }).toCompletableFuture();
            return result;
        }

        /**
         * Searches Conan package files and generates json package info.
         * @param keys Storage keys for Conan package binary.
         * @param pkgpath Conan package path in Artipie storage.
         * @return Package info as String in CompletableFuture.
         */
        private CompletableFuture<String> findPackageInfo(final Collection<Key> keys,
            final String pkgpath) {
            final Optional<CompletableFuture<String>> result = keys.stream()
                .filter(key -> key.string().endsWith(ConansEntity.CONAN_INFO)).map(
                    key -> this.getStorage().value(key).thenCompose(
                        content -> {
                            try {
                                final String pkghash = GetSearchBinPkg.extractHash(key, pkgpath);
                                return GetSearchBinPkg.pkgInfoToJson(
                                    content, Json.createObjectBuilder(), pkghash
                                );
                            } catch (final IOException exception) {
                                throw new ArtipieIOException(exception);
                            }
                        }
                    )
                ).findFirst();
            return result.orElseGet(
                () -> CompletableFuture.completedFuture(
                    String.format("Package binaries not found: %1$s", pkgpath)
                )
            );
        }

        /**
         * Extract package binary hash from storage key.
         * @param key Artipie storage key instance.
         * @param pkgpath Conan package path.
         * @return Package hash string value.
         */
        private static String extractHash(final Key key, final String pkgpath) {
            final String keystr = key.string();
            final int pathstart = keystr.indexOf(pkgpath);
            final int pathend = pathstart + pkgpath.length();
            final int hashend = keystr.indexOf("/", pathend + 1);
            return keystr.substring(pathend, hashend);
        }
    }

    /**
     * Conan /packages/~hash~ REST APIs.
     * @since 0.1
     * @checkstyle ClassDataAbstractionCouplingCheck (99 lines)
     */
    public static final class GetPkgInfo extends BaseConanSlice {

        /**
         * Ctor.
         * @param storage Current Artipie storage instance.
         */
        public GetPkgInfo(final Storage storage) {
            super(storage, new PathWrap.PkgBinInfo());
        }

        @Override
        public CompletableFuture<RequestResult> getResult(final RequestLineFrom request,
            final String hostname, final Matcher matcher) {
            final String uripath = matcher.group(ConansEntity.URI_PATH);
            final String hash = matcher.group(ConansEntity.URI_HASH);
            return BaseConanSlice.generateJson(
                ConansEntity.PKG_BIN_LIST, name -> {
                    final Key key = new Key.From(
                        String.join(
                            "", uripath, ConansEntity.PKG_BIN_DIR, hash,
                            ConansEntity.PKG_REV_DIR, name
                        ));
                    return new Tuple2<>(key, this.generateMDhash(key));
                }, tuple -> {
                    Optional<String> result = Optional.empty();
                    if (tuple._2().length() > 0) {
                        result = Optional.of(tuple._2());
                    }
                    return result;
                }, str -> String.join("", "{", str, "}")
            );
        }

        /**
         * Generates An md5 hash for package file.
         * @param key Storage key for package file.
         * @return An md5 hash string for file content.
         */
        private CompletableFuture<String> generateMDhash(final Key key) {
            return this.getStorage().exists(key).thenCompose(
                exist -> {
                    final CompletableFuture<String> result;
                    if (exist) {
                        result = this.getStorage().value(key).thenCompose(
                            content -> new PublisherAs(content).bytes().thenApply(
                                data -> {
                                    String hashstr;
                                    try {
                                        final MessageDigest mdg = MessageDigest.getInstance("MD5");
                                        final int hex = 16;
                                        hashstr = new BigInteger(1, mdg.digest(data))
                                            .toString(hex);
                                    } catch (final NoSuchAlgorithmException exception) {
                                        hashstr = "";
                                    }
                                    return hashstr;
                                })
                        );
                    } else {
                        result = CompletableFuture.completedFuture(null);
                    }
                    return result;
                }
            );
        }
    }

    /**
     * Conan /search REST APIs for package recipes.
     * @since 0.1
     */
    public static final class GetSearchSrcPkg extends BaseConanSlice {

        /**
         * Ctor.
         * @param storage Current Artipie storage instance.
         */
        public GetSearchSrcPkg(final Storage storage) {
            super(storage, new PathWrap.SearchSrcPkg());
        }

        @Override
        public CompletableFuture<RequestResult> getResult(final RequestLineFrom request,
            final String hostname, final Matcher matcher) {
            final String question = getQuestion(request);
            return this.getStorage().list(Key.ROOT).thenApply(
                keys -> {
                    final Set<String> recipes = new HashSet<>();
                    for (final Key key : keys) {
                        final String str = key.string();
                        final int start = str.indexOf(ConansEntity.PKG_SRC_DIR);
                        if (start > 0) {
                            String recipe = str.substring(0, start);
                            final int extra = recipe.indexOf("/_/_");
                            if (extra >= 0) {
                                recipe = str.substring(0, extra);
                            }
                            if (recipe.contains(question)) {
                                recipes.add(recipe);
                            }
                        }
                    }
                    final StringBuilder builder = new StringBuilder();
                    for (final String str : recipes) {
                        builder.append(String.format("\"%1$s\",", str));
                    }
                    return new RequestResult(
                        String.format(
                            "{ results: [%1$s] }",
                            builder.substring(0, builder.length() - 1)
                        ));
                });
        }

        /**
         * Extracts question parameter from the query string.
         * @param request Request line object with query string.
         * @return Question ("q") parameter's value, as String.
         */
        private static String getQuestion(final RequestLineFrom request) {
            String result = "";
            final String[] query = request.uri().getQuery().split("=");
            if (query.length == 2 && query[0].equals("q")) {
                result = query[1];
            }
            return result;
        }
    }
}
