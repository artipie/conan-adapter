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
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonValue;
import javax.json.stream.JsonParser;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for RevisionsIndex class.
 * @since 0.1
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class RevisionsIndexTest {

    /**
     * ZLIB binary package dir. name (hash).
     */
    static final String ZLIB_BIN_PKG = "6af9cc7cb931c5ad942174fd7838eb655717c709";

    /**
     * Path to zlib package binary index file.
     */
    static final String ZLIB_BIN_INDEX =
        "zlib/1.2.11/_/_/0/package/6af9cc7cb931c5ad942174fd7838eb655717c709/revisions.txt";

    /**
     * Path to zlib package recipe index file.
     */
    static final String ZLIB_SRC_INDEX = "zlib/1.2.11/_/_/revisions.txt";

    /**
     * Test storage.
     */
    private Storage storage;

    /**
     * Test instance.
     */
    private RevisionsIndex index;

    @BeforeEach
    void setUp() {
        this.storage = new InMemoryStorage();
        new TestResource("conan-test/data").addFilesTo(this.storage, Key.ROOT);
        this.index = new RevisionsIndex(this.storage, "zlib/1.2.11/_/_");
    }

    @Test
    void updateRecipeIndex() {
        this.storage.delete(new Key.From(RevisionsIndexTest.ZLIB_SRC_INDEX)).join();
        final List<Integer> result = this.index.updateRecipeIndex().toCompletableFuture().join();
        final JsonParser parser = this.storage.value(
            new Key.From(RevisionsIndexTest.ZLIB_SRC_INDEX)
        ).thenCompose(content -> new PublisherAs(content).asciiString()).thenApply(
            str -> Json.createParser(new StringReader(str))
        ).join();
        parser.next();
        final JsonArray revs = parser.getObject().getJsonArray("revisions");
        final String time = RevisionsIndexTest.getJsonStr(revs.get(0), "time");
        final String revision = RevisionsIndexTest.getJsonStr(revs.get(0), "revision");
        MatcherAssert.assertThat(
            "Checking revision object fields are correct",
            time.length() > 0 && revision.length() > 0 && result.size() == revs.size()
        );
        MatcherAssert.assertThat(
            "Checking revision object values are correct",
            result.get(0) == Integer.parseInt(revision)
                && Instant.parse(time).getEpochSecond() > 0
        );
    }

    @Test
    void updateBinaryIndex() {
        this.storage.delete(new Key.From(RevisionsIndexTest.ZLIB_BIN_INDEX)).join();
        final List<Integer> result = this.index.updateBinaryIndex(
            0, RevisionsIndexTest.ZLIB_BIN_PKG
        ).toCompletableFuture().join();
        final JsonParser parser = this.storage.value(
            new Key.From(RevisionsIndexTest.ZLIB_SRC_INDEX)
        ).thenCompose(content -> new PublisherAs(content).asciiString()).thenApply(
            str -> Json.createParser(new StringReader(str))
        ).join();
        parser.next();
        final JsonArray revs = parser.getObject().getJsonArray("revisions");
        final String time = RevisionsIndexTest.getJsonStr(revs.get(0), "time");
        final String revision = RevisionsIndexTest.getJsonStr(revs.get(0), "revision");
        MatcherAssert.assertThat(
            "Checking revision object fields are correct",
            time.length() > 0 && revision.length() > 0 && result.size() == revs.size()
        );
        MatcherAssert.assertThat(
            "Checking revision object values are correct",
            result.get(0) == Integer.parseInt(revision)
                && Instant.parse(time).getEpochSecond() > 0
        );
    }

    private static String getJsonStr(final JsonValue object, final String key) {
        return object.asJsonObject().get(key).toString().replaceAll("\"", "");
    }
}
