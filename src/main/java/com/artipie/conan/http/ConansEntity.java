package com.artipie.conan.http;

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
import io.vavr.Tuple2;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ini4j.Profile;
import org.ini4j.Wini;
import org.reactivestreams.Publisher;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class ConansEntity {

    public static final String DOWNLOAD_PATH = "^/v1/conans/(?<path>.*)/download_urls$";
    private static final Pattern DOWNLOAD_PATTERN = Pattern.compile(ConansEntity.DOWNLOAD_PATH);

    public static final String DOWNLOAD_PKG_PATH = "^/v1/conans/(?<path>.*)/packages/(?<hash>[0-9,a-f]*)/download_urls$";
    private static final Pattern DOWNLOAD_PKG_PATTERN = Pattern.compile(ConansEntity.DOWNLOAD_PKG_PATH);

    public static final String SEARCH_PKG_PATH = "^/v1/conans/(?<path>.*)/search$";
    private static final Pattern SEARCH_PKG_PATTERN = Pattern.compile(ConansEntity.SEARCH_PKG_PATH);

    public static final String PKG_INFO_PATH = "^/v1/conans/(?<path>.*)/packages/(?<hash>[0-9,a-f]*)$";
    private static final Pattern PKG_INFO_PATTERN = Pattern.compile(ConansEntity.PKG_INFO_PATH);

    private static final String PROTOCOL = "http://";

    public static class GetDownload implements Slice {

        private final Storage storage;
        private String hostName;

        public GetDownload(final Storage storage) {
            this.storage = storage;
            this.hostName = "";
        }

        private CompletableFuture<String> downloadUriContent(final URI uri) {
            Matcher uriMatcher = ConansEntity.DOWNLOAD_PKG_PATTERN.matcher(uri.getPath());
            String pkgHash = null;
            String uriPath;
            String packageDir;
            String[] packageFiles;
            if (uriMatcher.matches()) {
                uriPath = uriMatcher.group("path");
                pkgHash = uriMatcher.group("hash");
                packageDir = "/0/package/";
                packageFiles = new String[] {
                    "conaninfo.txt", "conanmanifest.txt", "conan_package.tgz"
                };
            } else {
                uriMatcher = ConansEntity.DOWNLOAD_PATTERN.matcher(uri.getPath());
                uriPath = uriMatcher.matches()?uriMatcher.group("path"): "";
                packageDir = "/0/export/";
                packageFiles = new String[] {
                    "conan_export.tgz", "conanfile.py", "conanmanifest.txt", "conan_sources.tgz"
                };
            }
            final String uriRevDir = "/0/";
            final ArrayList<Tuple2<Key, CompletableFuture<Boolean>>> res = new ArrayList<>();
            final ArrayList<CompletableFuture<Boolean>> futures = new ArrayList<>();
            for (final String fileName: packageFiles) {
                Key k;
                if (pkgHash == null) {
                    k = new Key.From(uriPath + packageDir + fileName);
                } else {
                    k = new Key.From(uriPath + packageDir + pkgHash + uriRevDir + fileName);
                }
                final CompletableFuture<Boolean> f = storage.exists(k);
                futures.add(f);
                res.add(new Tuple2<>(k, f));
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            final CompletableFuture<Boolean>[] arr = new CompletableFuture[0];
            return CompletableFuture.allOf(futures.toArray(arr)).thenCompose( o -> {
                final StringBuilder result = new StringBuilder();
                for (final Tuple2<Key, CompletableFuture<Boolean>> t: res) {
                    if (t._2().join()) {
                        final String[] parts = t._1().string().split("/");
                        final String name = parts[parts.length - 1];
                        result.append("\"" + name + "\": \"" + ConansEntity.PROTOCOL + this.hostName + "/" + t._1() + "\",");
                    }
                }
                return CompletableFuture.completedFuture('{' + result.substring(0, result.length() - 1) + '}');
            });
        }

        @Override
        public Response response(final String line, final Iterable<Map.Entry<String, String>> headers, final Publisher<ByteBuffer> body) {
            for (final Map.Entry<String, String> header: headers) {
                if (header.getKey().equals("Host")) {
                    hostName = header.getValue();
                }
            }
            return new AsyncResponse(
                CompletableFuture
                    .supplyAsync(new RequestLineFrom(line)::uri)
                    .thenCompose(
                        uri -> downloadUriContent(uri)
                            .thenCompose(
                                exist -> {
                                    final CompletionStage<Response> result;
                                    if (exist == null) {
                                        result = CompletableFuture.completedFuture(
                                            new RsWithBody(
                                                StandardRs.NOT_FOUND,
                                                String.format("URI %s not found.", uri),
                                                StandardCharsets.UTF_8
                                            )
                                        );
                                    } else {
                                        result = CompletableFuture.completedFuture(
                                            new RsWithHeaders(
                                                new RsWithBody(
                                                    StandardRs.OK,
                                                    exist,
                                                    StandardCharsets.UTF_8
                                                ),
                                                "Content-Type", "application/json"));
                                    }
                                    return result;
                                }
                            )
                    )
            );
        }
    }

    public static class GetSearchPkg implements Slice {

        private final Storage storage;

        public GetSearchPkg(final Storage storage) {
            this.storage = storage;
        }

        /**
         * Returns pkg storage location, uses '0' as revision, since revisions are part of conan/v2 protocol.
         * @return
         */
        String getPkgPathComponent() {
            return "/0/package/";
        }

        private CompletableFuture<String> searchPkg(final URI uri) {
            final Matcher m = ConansEntity.SEARCH_PKG_PATTERN.matcher(uri.getPath());
            final String uriPath = m.matches()? m.group("path"): null;
            final String keyPath = uriPath + getPkgPathComponent();
            return this.storage.list(new Key.From(keyPath)).thenCompose(keys -> {
                for (final Key key: keys) {
                    final String keyStr = key.string();
                    if (keyStr.endsWith("conaninfo.txt")) {
                        return this.storage.value(key).thenApply(content -> {
                            final JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
                            final String recipeHashField = "recipe_hash";
                            final int pathStart = keyStr.indexOf(keyPath);
                            final int pathEnd = pathStart + keyPath.length();
                            final int hashEnd = keyStr.indexOf("/", pathEnd + 1);
                            final String recipeHash = keyStr.substring(pathEnd, hashEnd);
                            final JsonObjectBuilder recipeObjBuilder = Json.createObjectBuilder();
                            try {
                                final Wini conaninfo = new Wini(new StringReader(new PublisherAs(content).string(StandardCharsets.UTF_8).toCompletableFuture().join()));
                                final String recipeInfoHash = conaninfo.get(recipeHashField).keySet().iterator().next();
                                for (final String section : conaninfo.keySet()) {
                                    final JsonObjectBuilder sectionObjBuilder = Json.createObjectBuilder();
                                    final Profile.Section sectionObj = conaninfo.get(section);
                                    for (final String skey : sectionObj.keySet()) {
                                        final String value = sectionObj.get(skey);
                                        if (value != null) {
                                            sectionObjBuilder.add(skey, value);
                                        }
                                    }
                                    recipeObjBuilder.add(section, sectionObjBuilder);
                                }
                                recipeObjBuilder.add(recipeHashField, recipeInfoHash);
                                jsonBuilder.add(recipeHash, recipeObjBuilder);
                            } catch (Exception e) {
                                e.printStackTrace();
                                return "";
                            }
                            return jsonBuilder.build().toString();
                        });
                    }
                }
                return CompletableFuture.completedFuture("Recipe not found: " + uriPath);
            });
        }

        @Override
        public Response response(final String line, final Iterable<Map.Entry<String, String>> headers, final Publisher<ByteBuffer> body) {
            return new AsyncResponse(
                CompletableFuture
                    .supplyAsync(new RequestLineFrom(line)::uri)
                    .thenCompose(
                        uri -> searchPkg(uri)
                            .thenCompose(
                                exist -> {
                                    final CompletionStage<Response> result;
                                    if (exist == null) {
                                        result = CompletableFuture.completedFuture(
                                            new RsWithBody(
                                                StandardRs.NOT_FOUND,
                                                String.format("URI %s not found.", uri),
                                                StandardCharsets.UTF_8
                                            )
                                        );
                                    } else {
                                        result = CompletableFuture.completedFuture(
                                            new RsWithHeaders(
                                                new RsWithBody(
                                                    StandardRs.OK,
                                                    exist,
                                                    StandardCharsets.UTF_8
                                                ),
                                                "Content-Type", "application/json"));
                                    }
                                    return result;
                                }
                            )
                    )
            );
        }
    }

    public static class GetPkgInfo implements Slice {

        private final Storage storage;

        public GetPkgInfo(final Storage storage) {
            this.storage = storage;
        }

        private CompletableFuture<String> hashUriContent(final URI uri) {
            final Matcher m = ConansEntity.PKG_INFO_PATTERN.matcher(uri.getPath());
            final String uriPath = m.matches()? m.group("path"): null;
            final String hash = m.matches()? m.group("hash"): null;
            final String[] packageFiles = new String[] {
                "conan_package.tgz", "conanmanifest.txt", "conaninfo.txt"
            };
            final String packageDir = "/0/package/";
            final ArrayList<Tuple2<Key, CompletableFuture<String>>> res = new ArrayList<>();
            final ArrayList<CompletableFuture<String>> futures = new ArrayList<>();
            for (final String filename: packageFiles) {
                final Key k = new Key.From(uriPath + packageDir + hash + "/0/" +  filename);
                final CompletableFuture<String> f = storage.exists(k).thenCompose(exist -> {
                    if (exist) {
                        return storage.value(k).thenCompose(content -> {
                            String md5hash = null;
                            try {
                                byte[] data = new PublisherAs(content).bytes().toCompletableFuture().join();
                                MessageDigest dg = MessageDigest.getInstance("MD5");
                                byte[] digest = dg.digest(data);
                                BigInteger v = new BigInteger(1, digest);
                                md5hash = v.toString(16);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return CompletableFuture.completedFuture(md5hash);
                        });
                    } else {
                        return CompletableFuture.completedFuture(null);
                    }
                });
                futures.add(f);
                res.add(new Tuple2<>(k, f));
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            final CompletableFuture<String>[] arr = new CompletableFuture[0];
            return CompletableFuture.allOf(futures.toArray(arr)).thenCompose( o -> {
                final StringBuilder result = new StringBuilder();
                for (final Tuple2<Key, CompletableFuture<String>> t: res) {
                    final String[] parts = t._1().string().split("/");
                    final String name = parts[parts.length - 1];
                    result.append("\"" + name + "\": \"" + t._2().join() + "\",");
                }
                return CompletableFuture.completedFuture('{' + result.substring(0, result.length() - 1) + '}');
            });
        }

        @Override
        public Response response(final String line, final Iterable<Map.Entry<String, String>> headers, final Publisher<ByteBuffer> body) {
            return new AsyncResponse(
                CompletableFuture
                    .supplyAsync(new RequestLineFrom(line)::uri)
                    .thenCompose(
                        uri -> this.hashUriContent(uri)
                            .thenCompose(
                                exist -> {
                                    final CompletionStage<Response> result;
                                    if (exist == null) {
                                        result = CompletableFuture.completedFuture(
                                            new RsWithBody(
                                                StandardRs.NOT_FOUND,
                                                String.format("URI %s not found.", uri),
                                                StandardCharsets.UTF_8
                                            )
                                        );
                                    } else {
                                        result = CompletableFuture.completedFuture(
                                            new RsWithHeaders(
                                                new RsWithBody(
                                                    StandardRs.OK,
                                                    exist,
                                                    StandardCharsets.UTF_8
                                                ),
                                                "Content-Type", "application/json"));
                                    }
                                    return result;
                                }
                            )
                    )
            );
        }
    }
}
