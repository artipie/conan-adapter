package com.artipie.conan.http;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.http.Headers;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.headers.ContentFileName;
import com.artipie.http.headers.ContentType;
import com.artipie.http.rq.RequestLineFrom;
import com.artipie.http.rs.*;
import io.vavr.Tuple2;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConansEntity {

    public static final String PATH = "^/v1/conans/(?<path>.*)/download_urls$";
    private static final Pattern PATTERN = Pattern.compile(PATH);

    public static class Get implements Slice {

        private final Storage storage;
        private String hostName = "";
        private String protocol = "http://";

        public Get(final Storage storage) {
            this.storage = storage;
        }

        private Key downloadUriKey(String uriPath) {
            return new Key.From(uriPath + "/0/export/conanmanifest.txt");
        }

        private CompletableFuture<String> downloadUriContent(URI uri) {
            final Matcher m = ConansEntity.PATTERN.matcher(uri.getPath());
            final String uriPath = m.matches()? (m.group("path")): null;
            final String[] packageFiles = new String[] {
                    "conan_export.tgz", "conanfile.py", "conanmanifest.txt", "conan_sources.tgz"
            };
            final String packageDir = "/0/export/";
            ArrayList<Tuple2<Key, CompletableFuture<Boolean>>> res = new ArrayList<>();
            ArrayList<CompletableFuture<Boolean>> futures = new ArrayList<>();
            for (final String fileName: packageFiles) {
                final Key k = new Key.From(uriPath + packageDir + fileName);
                CompletableFuture<Boolean> f = storage.exists(k);
                futures.add(f);
                res.add(new Tuple2<>(k, f));
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            CompletableFuture<Boolean>[] arr = new CompletableFuture[0];
            return CompletableFuture.allOf(futures.toArray(arr)).thenCompose( o -> {
                String result = "";
                for (final Tuple2<Key, CompletableFuture<Boolean>> t: res) {
                    String[] parts = t._1().string().split("/");
                    String name = parts[parts.length - 1];
                    result += "\"" + name + "\": \"" + this.protocol + this.hostName + "/" + t._1() + "\","; // full URL!
                }
                return CompletableFuture.completedFuture('{' + result.substring(0, result.length() - 1) + '}');
            });
        }

        @Override
        public Response response(String line, Iterable<Map.Entry<String, String>> headers, Publisher<ByteBuffer> body) {
            for (Map.Entry<String, String> header: headers) {
                if (header.getKey().equals("Host")) {
                    hostName = header.getValue();
                }
            }
            return new AsyncResponse(
                    CompletableFuture
                            .supplyAsync(new RequestLineFrom(line)::uri)
                            .thenCompose(
                                    uri -> {
                                        return downloadUriContent(uri)
                                                .thenCompose(
                                                        exist -> {
                                                            final CompletionStage<Response> result;
                                                            if (exist != null) {
                                                                result = CompletableFuture.completedFuture(
                                                                        new RsWithHeaders(
                                                                                new RsWithBody(
                                                                                StandardRs.OK,
                                                                                exist,
                                                                                StandardCharsets.UTF_8
                                                                            ),
                                                                        "Content-Type", "application/json"));
                                                            } else {
                                                                result = CompletableFuture.completedFuture(
                                                                        new RsWithBody(
                                                                                StandardRs.NOT_FOUND,
                                                                                String.format("URI %s not found.", uri),
                                                                                StandardCharsets.UTF_8
                                                                        )
                                                                );
                                                            }
                                                            return result;
                                                        }
                                                );
                                    }
                            )
            );
        }
    }
}
