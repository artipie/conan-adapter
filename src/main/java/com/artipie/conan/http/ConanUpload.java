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

import com.artipie.ArtipieException;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.conan.ConanRepo;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.rq.RequestLineFrom;
import com.artipie.http.rq.RqHeaders;
import com.artipie.http.rs.RsWithBody;
import com.artipie.http.rs.RsWithHeaders;
import com.artipie.http.rs.StandardRs;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.stream.JsonParser;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.utils.StringInputStream;

/**
 * Slice for Conan package data uploading support.
 * @since 0.1
 */
public final class ConanUpload {

    /**
     * Pattern for /v1/conans/{path}/upload_urls.
     */
    public static final PathWrap UPLOAD_SRC_PATH = new PathWrap.UploadSrc();

    /**
     * Error message string for the client.
     */
    private static final String URI_S_NOT_FOUND = "URI %s not found.";

    /**
     * HTTP Content-type header name.
     */
    private static final String CONTENT_TYPE = "Content-Type";

    /**
     * HTTP json application type string.
     */
    private static final String JSON_TYPE = "application/json";

    /**
     * Path part of the request URI.
     */
    private static final String URI_PATH = "path";

    /**
     * Protocol type for download URIs.
     */
    private static final String PROTOCOL = "http://";

    /**
     * Subdir for package recipe (sources).
     */
    private static final String PKG_SRC_DIR = "/0/export/";

    /**
     * Asto storage.
     */
    @SuppressWarnings({"PMD.UnusedPrivateField", "PMD.SingularField"})
    private final Storage asto;

    /**
     * Rpm instance.
     */
    @SuppressWarnings({"PMD.UnusedPrivateField", "PMD.SingularField"})
    private final ConanRepo repo;

    /**
     * Ctor.
     * @param storage Storage object.
     */
    public ConanUpload(final Storage storage) {
        this.asto = storage;
        this.repo = new ConanRepo(storage);
    }

    /**
     * Match pattern for the request.
     * @param line Request line.
     * @param pathwrap Wrapper object for Conan protocol request path.
     * @return Corresponding matcher for the request.
     */
    private static Matcher matchRequest(final String line, final PathWrap pathwrap) {
        final Matcher matcher = pathwrap.getPattern().matcher(
            new RequestLineFrom(line).uri().getPath()
        );
        if (!matcher.matches()) {
            throw new ArtipieException(
                String.join("Request parameters doesn't match: ", line)
            );
        }
        return matcher;
    }

    /**
     * Generates error message for the requested file name.
     * @param filename Requested file name.
     * @return Error message for the response.
     */
    private static CompletableFuture<Response> generateError(final String filename) {
        return CompletableFuture.completedFuture(
            new RsWithBody(
                StandardRs.NOT_FOUND,
                String.format(ConanUpload.URI_S_NOT_FOUND, filename),
                StandardCharsets.UTF_8
            )
        );
    }

    /**
     * Conan /authenticate REST APIs.
     * @since 0.1
     */
    public static final class UploadUrls implements Slice {

        /**
         * Current Artipie storage instance.
         */
        private final Storage storage;

        /**
         * Ctor.
         *
         * @param storage Current Artipie storage instance.
         */
        public UploadUrls(final Storage storage) {
            this.storage = storage;
        }

        @Override
        public Response response(final String line,
            final Iterable<Map.Entry<String, String>> headers, final Publisher<ByteBuffer> body) {
            final Matcher matcher = matchRequest(line, ConanUpload.UPLOAD_SRC_PATH);
            final String filename = matcher.group(ConanUpload.URI_PATH);
            final String hostname = new RqHeaders.Single(headers, "Host").asString();
            return new AsyncResponse(
                this.storage.exists(new Key.From(filename)).thenCompose(
                    exist -> {
                        final CompletableFuture<Response> result;
                        if (exist) {
                            result = generateError(filename);
                        } else {
                            result = UploadUrls.doUploading(body, filename, hostname);
                        }
                        return result;
                    }
                )
            );
        }

        /**
         * Implements uploading from the client to server repository storage.
         * @param body Request body with file data.
         * @param filename Target file name.
         * @param hostname Server host name.
         * @return Respose result of this operation.
         */
        private static CompletableFuture<Response> doUploading(final Publisher<ByteBuffer> body,
            final String filename, final String hostname) {
            return new PublisherAs(body).asciiString().thenApply(
                str -> {
                    final JsonParser parser = Json.createParser(new StringInputStream(str));
                    parser.next();
                    final JsonObjectBuilder result = Json.createObjectBuilder();
                    for (final String key : parser.getObject().keySet()) {
                        final String url = String.join(
                            "", ConanUpload.PROTOCOL, hostname, "/",
                            filename, ConanUpload.PKG_SRC_DIR, key, "?signature=0"
                        );
                        result.add(key, url);
                    }
                    return (Response) new RsWithHeaders(
                        new RsWithBody(
                            StandardRs.OK, result.build().toString(), StandardCharsets.UTF_8
                        ),
                        ConanUpload.CONTENT_TYPE, ConanUpload.JSON_TYPE
                    );
                }
            ).toCompletableFuture();
        }
    }
}
