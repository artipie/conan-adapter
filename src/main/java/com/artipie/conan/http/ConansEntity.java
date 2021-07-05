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
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.rq.RequestLineFrom;
import com.artipie.http.rs.RsWithBody;
import com.artipie.http.rs.RsWithHeaders;
import com.artipie.http.rs.StandardRs;
import com.google.common.base.Strings;
import io.vavr.Tuple2;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import org.ini4j.Wini;
import org.reactivestreams.Publisher;

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
     * Pattern for /download_urls request (package sources).
     */
    public static final PathWrap DLOAD_SRC_PATH = new PathWrap.DownloadSrc();

    /**
     * Pattern for /download_urls request (package binary).
     */
    public static final PathWrap DLOAD_BIN_PATH = new PathWrap.DownloadBin();

    /**
     * Pattern for /search reqest (for package binaries).
     */
    public static final PathWrap SEARCH_PKG_PATH = new PathWrap.SearchPkg();

    /**
     * Pattern for package binary info by its hash.
     */
    public static final PathWrap PKG_BIN_INFO_PATH = new PathWrap.PkgBinInfo();

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
     * Error message string for the client.
     */
    private static final String URI_S_NOT_FOUND = "URI %s not found.";

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
     * HTTP Content-type header name.
     */
    private static final String CONTENT_TYPE = "Content-Type";

    /**
     * HTTP json application type string.
     */
    private static final String JSON_TYPE = "application/json";

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
    public static final class GetDownload implements Slice {

        /**
         * Current Artipie storage instance.
         */
        private final Storage storage;

        /**
         * Ctor.
         *
         * @param storage Current Artipie storage instance.
         */
        public GetDownload(final Storage storage) {
            this.storage = storage;
        }

        @Override
        public Response response(
            final String line,
            final Iterable<Map.Entry<String, String>> headers,
            final Publisher<ByteBuffer> body
        ) {
            final String hostname = getHost(headers);
            return new AsyncResponse(
                CompletableFuture.supplyAsync(new RequestLineFrom(line)::uri).thenCompose(
                    uri -> this.getDownloadUriContent(uri, hostname).thenCompose(
                        content -> {
                            final Response result;
                            if (Strings.isNullOrEmpty(content)) {
                                result = new RsWithBody(
                                    StandardRs.NOT_FOUND,
                                    String.format(ConansEntity.URI_S_NOT_FOUND, uri),
                                    StandardCharsets.UTF_8
                                );
                            } else {
                                result = new RsWithHeaders(
                                    new RsWithBody(
                                        StandardRs.OK, content, StandardCharsets.UTF_8
                                    ),
                                    ConansEntity.CONTENT_TYPE, ConansEntity.JSON_TYPE
                                );
                            }
                            return CompletableFuture.completedFuture(result);
                        }
                    )
                )
            );
        }

        /**
         * Extracts host name for HTTP headers.
         * @param headers Iterable of headers.
         * @return Host name as String.
         */
        private static String getHost(final Iterable<Map.Entry<String, String>> headers) {
            String hostname = "";
            for (final Map.Entry<String, String> header : headers) {
                if (header.getKey().equals("Host")) {
                    hostname = header.getValue();
                }
            }
            return hostname;
        }

        /**
         * Generates package files URIs for downloading.
         *
         * @param uri Conan package URI.
         * @param hostname Host name or IP to generate download URLs.
         * @return Json data String in CompletableFuture with files URIs.
         */
        private CompletableFuture<String> getDownloadUriContent(final URI uri,
            final String hostname) {
            Matcher urimatcher = ConansEntity.DLOAD_BIN_PATH.getPattern().matcher(uri.getPath());
            final String pkghash;
            final String uripath;
            final String[] packagefiles;
            if (urimatcher.matches()) {
                uripath = urimatcher.group(ConansEntity.URI_PATH);
                pkghash = urimatcher.group(ConansEntity.URI_HASH);
                packagefiles = ConansEntity.PKG_BIN_LIST;
            } else {
                urimatcher = ConansEntity.DLOAD_SRC_PATH.getPattern().matcher(uri.getPath());
                if (urimatcher.matches()) {
                    uripath = urimatcher.group(ConansEntity.URI_PATH);
                } else {
                    uripath = "";
                }
                packagefiles = ConansEntity.PKG_SRC_LIST;
                pkghash = "";
            }
            return GetDownload.generateUrlsData(
                hostname, Arrays.stream(packagefiles).map(
                    file -> {
                        final Key key = GetDownload.getKey(uripath, pkghash, file);
                        return new Tuple2<>(key, this.storage.exists(key));
                    }
                ).collect(Collectors.toList())
            );
        }

        /**
         * Create storage Key, based of URI request parameters.
         * @param uripath Conan package path from URI.
         * @param pkghash Conan package binary hash from URI. null for package recipe (sources).
         * @param filename Conan file name from URI.
         * @return Artipie storage Key instance.
         */
        private static Key getKey(final String uripath, final String pkghash,
            final String filename) {
            final Key key;
            if (Strings.isNullOrEmpty(pkghash)) {
                key = new Key.From(String.join("", uripath, ConansEntity.PKG_SRC_DIR, filename));
            } else {
                key = new Key.From(
                    String.join(
                        "", uripath, ConansEntity.PKG_BIN_DIR, pkghash,
                        ConansEntity.PKG_REV_DIR, filename
                    ));
            }
            return key;
        }

        /**
         * Generates URIs Json for given storage Keys list.
         * @param hostname Host name or IP to generate download URLs.
         * @param keychecks Collection of Pairs of Key + existence checks.
         * @return Json data String in CompletableFuture with files URIs.
         */
        private static CompletableFuture<String> generateUrlsData(final String hostname,
            final List<Tuple2<Key, CompletableFuture<Boolean>>> keychecks
        ) {
            return CompletableFuture.allOf(
                keychecks.stream().map(Tuple2::_2).toArray(CompletableFuture[]::new)
            ).thenCompose(
                unused -> {
                    final StringBuilder result = keychecks.stream().filter(
                        tuple -> tuple._2().join()
                    ).map(
                        tuple -> new Tuple2<>(
                            tuple._1().string(), tuple._1().string().split("/")
                        )
                    ).map(
                        t -> String.format(
                            "\"%1$s\":\"%2$s%3$s/%4$s\",", t._2()[t._2().length - 1],
                            ConansEntity.PROTOCOL, hostname, t._1()
                        )
                    ).collect(
                        StringBuilder::new, StringBuilder::append, (ignored1, ignored2) -> {
                        });
                    return CompletableFuture.completedFuture(
                        String.join(
                            "", "{",
                            result.substring(0, result.length() - 1),
                            "}"
                        )
                    );
                }
            );
        }
    }

    /**
     * Conan /search REST APIs for package binaries.
     * @since 0.1
     * @checkstyle ClassDataAbstractionCouplingCheck (99 lines)
     */
    public static final class GetSearchPkg implements Slice {
        /**
         * Current Artipie storage instance.
         */
        private final Storage storage;

        /**
         * Ctor.
         *
         * @param storage Current Artipie storage instance.
         */
        public GetSearchPkg(final Storage storage) {
            this.storage = storage;
        }

        @Override
        public Response response(final String line,
            final Iterable<Map.Entry<String, String>> headers, final Publisher<ByteBuffer> body) {
            return new AsyncResponse(
                CompletableFuture.supplyAsync(new RequestLineFrom(line)::uri).thenCompose(
                    uri -> this.searchPkg(uri).thenCompose(
                        pkginfo -> {
                            final Response result;
                            if (Strings.isNullOrEmpty(pkginfo)) {
                                result = new RsWithBody(
                                    StandardRs.NOT_FOUND,
                                    String.format(ConansEntity.URI_S_NOT_FOUND, uri),
                                    StandardCharsets.UTF_8
                                );
                            } else {
                                result = new RsWithHeaders(
                                    new RsWithBody(
                                        StandardRs.OK, pkginfo, StandardCharsets.UTF_8
                                    ),
                                    ConansEntity.CONTENT_TYPE, ConansEntity.JSON_TYPE
                                );
                            }
                            return CompletableFuture.completedFuture(result);
                        }
                    )
                )
            );
        }

        /**
         * Converts Conan package binary info to json.
         * @param content Conan conaninfo.txt contents.
         * @param jsonbuilder Target to fill with json data.
         * @param pkghash Conan package hash value.
         * @throws IOException In case of conaninfo.txt contents access problems.
         */
        private static void pkgInfoToJson(
            final com.artipie.asto.Content content,
            final JsonObjectBuilder jsonbuilder,
            final String pkghash
        ) throws IOException {
            final Wini conaninfo = new Wini(
                new StringReader(new PublisherAs(content)
                    .string(StandardCharsets.UTF_8).toCompletableFuture().join()
                ));
            final JsonObjectBuilder pkgbuilder = Json.createObjectBuilder();
            conaninfo.forEach(
                (secname, section) -> {
                    final JsonObjectBuilder jsection = section.entrySet().stream()
                        .filter(e -> e.getValue() != null).collect(
                            Json::createObjectBuilder, (js, e) -> js.add(e.getKey(), e.getValue()),
                            (js1, js2) -> { }
                        );
                    pkgbuilder.add(secname, jsection);
                });
            final String hashfield = "recipe_hash";
            final String hashvalue = conaninfo.get(hashfield).keySet().iterator().next();
            pkgbuilder.add(hashfield, hashvalue);
            jsonbuilder.add(pkghash, pkgbuilder);
        }

        /**
         * Searches for Conan package binaries and returns conaninfo.txt in json form.
         *
         * @param uri Conan package search request URI.
         * @return Contents of conaninfo.txt converted to json form.
         */
        private CompletableFuture<String> searchPkg(final URI uri) {
            final Matcher matcher = ConansEntity.SEARCH_PKG_PATH
                .getPattern().matcher(uri.getPath());
            String uripath = "";
            if (matcher.matches()) {
                uripath = matcher.group(ConansEntity.URI_PATH);
            }
            final String pkgpath = String.join("", uripath, ConansEntity.PKG_BIN_DIR);
            return this.storage.list(new Key.From(pkgpath)).thenCompose(
                keys -> this.findPackageInfo(keys, pkgpath)
            );
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
                    key -> this.storage.value(key).thenApply(
                        content -> {
                            final String conaninfo;
                            try {
                                final JsonObjectBuilder jsonbuilder = Json.createObjectBuilder();
                                final String pkghash = GetSearchPkg.extractHash(key, pkgpath);
                                GetSearchPkg.pkgInfoToJson(content, jsonbuilder, pkghash);
                                conaninfo = jsonbuilder.build().toString();
                            } catch (final IOException exception) {
                                throw new ArtipieIOException(exception);
                            }
                            return conaninfo;
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
    public static final class GetPkgInfo implements Slice {

        /**
         * Artipie storage.
         */
        private final Storage storage;

        /**
         * Ctor.
         * @param storage Current Artipie storage instance.
         */
        public GetPkgInfo(final Storage storage) {
            this.storage = storage;
        }

        @Override
        public Response response(final String line,
            final Iterable<Map.Entry<String, String>> headers, final Publisher<ByteBuffer> body) {
            return new AsyncResponse(
                CompletableFuture.supplyAsync(new RequestLineFrom(line)::uri).thenCompose(
                    uri -> this.getPkgInfoJson(uri).thenCompose(
                        pkginfo -> {
                            final Response result;
                            if (Strings.isNullOrEmpty(pkginfo)) {
                                result = new RsWithBody(
                                    StandardRs.NOT_FOUND,
                                    String.format(ConansEntity.URI_S_NOT_FOUND, uri),
                                    StandardCharsets.UTF_8
                                );
                            } else {
                                result = new RsWithHeaders(
                                    new RsWithBody(
                                        StandardRs.OK, pkginfo, StandardCharsets.UTF_8
                                    ),
                                    ConansEntity.CONTENT_TYPE, ConansEntity.JSON_TYPE
                                );
                            }
                            return CompletableFuture.completedFuture(result);
                        }
                    )
                )
            );
        }

        /**
         * Generates json for given storage keys and generated content.
         * @param results List of pairs (storage key; generated content).
         * @return Json text in CompletableFuture.
         */
        private static CompletableFuture<String> generateJson(
            final List<Tuple2<Key, CompletableFuture<String>>> results) {
            return CompletableFuture.allOf(
                results.stream().map(Tuple2::_2).toArray(CompletableFuture[]::new)
            ).thenCompose(
                ignored -> {
                    final StringBuilder result = new StringBuilder();
                    for (final Tuple2<Key, CompletableFuture<String>> pair : results) {
                        final String[] parts = pair._1().string().split("/");
                        final String name = parts[parts.length - 1];
                        result.append(String.format("\"%1$s\":\"%2$s\",", name, pair._2().join()));
                    }
                    return CompletableFuture.completedFuture(
                        String.join(
                            "", "{",
                            result.substring(0, result.length() - 1),
                            "}"
                        )
                    );
                }
            );
        }

        /**
         * Generates Conan package info json for given Conan client request URI.
         * @param uri Conan client request URI.
         * @return Package info json String in CompletableFuture.
         */
        private CompletableFuture<String> getPkgInfoJson(final URI uri) {
            final Matcher matcher = ConansEntity.PKG_BIN_INFO_PATH
                .getPattern().matcher(uri.getPath());
            final String uripath;
            final String hash;
            if (matcher.matches()) {
                uripath = matcher.group(ConansEntity.URI_PATH);
                hash = matcher.group(ConansEntity.URI_HASH);
            } else {
                uripath = "";
                hash = "";
            }
            return GetPkgInfo.generateJson(Arrays.stream(ConansEntity.PKG_BIN_LIST).map(
                name -> {
                    final Key key = new Key.From(
                        String.join(
                            "", uripath, ConansEntity.PKG_BIN_DIR, hash,
                            ConansEntity.PKG_REV_DIR, name
                        )
                    );
                    return new Tuple2<>(key, this.generateMDhash(key));
                }
                ).collect(Collectors.toList())
            );
        }

        /**
         * Generates An md5 hash for package file.
         * @param key Storage key for package file.
         * @return An md5 hash string for file content.
         */
        private CompletableFuture<String> generateMDhash(final Key key) {
            return this.storage.exists(key).thenCompose(
                exist -> {
                    final CompletableFuture<String> result;
                    if (exist) {
                        result = this.storage.value(key).thenCompose(
                            content -> {
                                String hashstr;
                                try {
                                    final byte[] data = new PublisherAs(content)
                                        .bytes().toCompletableFuture().join();
                                    final MessageDigest mdg = MessageDigest.getInstance("MD5");
                                    final int hex = 16;
                                    hashstr = new BigInteger(1, mdg.digest(data)).toString(hex);
                                } catch (final NoSuchAlgorithmException exception) {
                                    hashstr = "";
                                }
                                return CompletableFuture.completedFuture(hashstr);
                            }
                        );
                    } else {
                        result = CompletableFuture.completedFuture(null);
                    }
                    return result;
                }
            );
        }
    }
}
