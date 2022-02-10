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

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.http.Connection;
import com.artipie.http.Headers;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import io.reactivex.Flowable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.json.Json;
import org.json.JSONException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Publisher;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Test for {@link ConansEntity}.
 * @since 0.1
 * @checkstyle LineLengthCheck (999 lines)
 */
class ConansEntityTest {

    /**
     * Path prefix for conan repository test data.
     */
    private static final String DIR_PREFIX = "conan-test/data";

    @ParameterizedTest
    @MethodSource("conanTestFilesList")
    public void downloadBinTest(final String... files) throws JSONException {
        this.runTest(
            "/v1/conans/zlib/1.2.11/_/_/packages/6af9cc7cb931c5ad942174fd7838eb655717c709/download_urls",
            "http/download_bin_urls.json", files, ConansEntity.DownloadBin::new
        );
    }

    @ParameterizedTest
    @MethodSource("conanTestFilesList")
    public void downloadSrcTest(final String... files) throws JSONException {
        this.runTest(
            "/v1/conans/zlib/1.2.11/_/_/download_urls", "http/download_src_urls.json",
            files, ConansEntity.DownloadSrc::new
        );
    }

    @ParameterizedTest
    @MethodSource("conanTestFilesList")
    public void getSearchBinPkgTest(final String... files) throws JSONException {
        this.runTest(
            "/v1/conans/zlib/1.2.11/_/_/search", "http/pkg_bin_search.json",
            files, ConansEntity.GetSearchBinPkg::new
        );
    }

    @ParameterizedTest
    @MethodSource("conanTestFilesList")
    public void getPkgInfoTest(final String... files) throws JSONException {
        this.runTest(
            "/v1/conans/zlib/1.2.11/_/_/packages/6af9cc7cb931c5ad942174fd7838eb655717c709",
            "http/pkg_bin_info.json", files, ConansEntity.GetPkgInfo::new
        );
    }

    @ParameterizedTest
    @MethodSource("conanTestFilesList")
    public void getSearchSrcPkgTest(final String... files) throws JSONException {
        this.runTest(
            "/v1/conans/search?q=zlib", "http/pkg_src_search.json",
            files, ConansEntity.GetSearchSrcPkg::new
        );
    }

    @ParameterizedTest
    @MethodSource("conanTestFilesList")
    public void digestForPkgTest(final String... files) throws JSONException {
        this.runTest(
            "/v1/conans/zlib/1.2.11/_/_/digest", "http/pkg_digest.json",
            files, ConansEntity.DigestForPkg::new
        );
    }

    @ParameterizedTest
    @MethodSource("conanTestFilesList")
    void getSrcPkgInfoTest(final String... files) throws JSONException {
        this.runTest(
            "/v1/conans/zlib/1.2.11/_/_", "http/pkg_src_info.json",
            files, ConansEntity.GetSrcPkgInfo::new
        );
    }

    /**
     * Runs test on given set of files and request factory. Checks the match with json given.
     * JSONAssert is used for friendly json matching error messages.
     * @param request HTTP request string.
     * @param json Path to json file with expected response value.
     * @param files List of files required for test.
     * @param factory Request instance factory.
     * @throws JSONException For Json parsing errors.
     * @checkstyle ParameterNumberCheck (55 lines)
     */
    private void runTest(final String request, final String json, final String[] files,
        final Function<Storage, Slice> factory) throws JSONException {
        final Storage storage = new InMemoryStorage();
        for (final String file : files) {
            new TestResource(String.join("/", ConansEntityTest.DIR_PREFIX, file))
                .saveTo(storage, new Key.From(file));
        }
        final Response response = factory.apply(storage).response(
            new RequestLine(RqMethod.GET, request).toString(),
            new Headers.From("Host", "localhost:9300"), Content.EMPTY
        );
        final String expected = Json.createReader(
            new TestResource(json).asInputStream()
        ).readObject().toString();
        final AtomicReference<byte[]> out = new AtomicReference<>();
        response.send(new FakeConnection(out)).toCompletableFuture().join();
        final String actual = new String(out.get(), StandardCharsets.UTF_8);
        JSONAssert.assertEquals(expected, actual, true);
    }

    /**
     * Returns test package files list for this tests (without revisions.txt files).
     * @return List of files, as Stream of junit Arguments.
     * @checkstyle LineLengthCheck (20 lines)
     */
    @SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.LineLengthCheck"})
    private static Stream<Arguments> conanTestFilesList() {
        final String[] files = new String[]{
            "zlib/1.2.11/_/_/0/package/6af9cc7cb931c5ad942174fd7838eb655717c709/0/conanmanifest.txt",
            "zlib/1.2.11/_/_/0/package/6af9cc7cb931c5ad942174fd7838eb655717c709/0/conaninfo.txt",
            "zlib/1.2.11/_/_/0/package/6af9cc7cb931c5ad942174fd7838eb655717c709/0/conan_package.tgz",
            "zlib/1.2.11/_/_/0/export/conan_sources.tgz",
            "zlib/1.2.11/_/_/0/export/conan_export.tgz",
            "zlib/1.2.11/_/_/0/export/conanfile.py",
            "zlib/1.2.11/_/_/0/export/conanmanifest.txt",
        };
        return Stream.of(Arguments.of((Object) files));
    }

    /**
     * Fake connection for testing response data.
     * Based on com.artipie.http.hm.RsHasBody.FakeConnection.
     * @since 0.1
     */
    private static final class FakeConnection implements Connection {

        /**
         * Output object for response data.
         */
        private final AtomicReference<byte[]> container;

        /**
         * Ctor.
         * @param container Output object for response data.
         */
        FakeConnection(final AtomicReference<byte[]> container) {
            this.container = container;
        }

        @Override
        public CompletionStage<Void> accept(
            final RsStatus status, final Headers headers, final Publisher<ByteBuffer> body
        ) {
            return CompletableFuture.supplyAsync(
                () -> {
                    final ByteBuffer buffer = Flowable.fromPublisher(body).reduce(
                        (left, right) -> {
                            left.mark();
                            right.mark();
                            final ByteBuffer concat = ByteBuffer.allocate(left.remaining() + right.remaining())
                                .put(left).put(right);
                            left.reset();
                            right.reset();
                            concat.flip();
                            return concat;
                        }).blockingGet(ByteBuffer.allocate(0));
                    final byte[] bytes = new byte[buffer.remaining()];
                    buffer.mark();
                    buffer.get(bytes);
                    buffer.reset();
                    this.container.set(bytes);
                    return null;
                });
        }
    }
}
