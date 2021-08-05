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
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import java.util.List;
import java.util.Objects;
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
        this.index = new RevisionsIndex(this.storage, Key.ROOT, "zlib/1.2.11/_/_");
    }

    @Test
    void recipeInfoTest() {
        final List<Integer> revs = this.index.getRecipeRevisions().toCompletableFuture().join();
        MatcherAssert.assertThat("Expect 1 recipe rev.", revs.size() == 1);
        MatcherAssert.assertThat("rev[0] == 0", revs.get(0) == 0);
        final List<String> pkgs = this.index.getPackageList(revs.get(0))
            .toCompletableFuture().join();
        MatcherAssert.assertThat("Expect 1 pkg binary", pkgs.size() == 1);
        MatcherAssert.assertThat(
            "Got correct pkg hash",
            Objects.equals(pkgs.get(0), RevisionsIndexTest.ZLIB_BIN_PKG)
        );
        final List<Integer> binrevs = this.index.getBinaryRevisions(revs.get(0), pkgs.get(0))
            .toCompletableFuture().join();
        MatcherAssert.assertThat("Expect 1 bin. rev.", binrevs.size() == 1);
        MatcherAssert.assertThat("binrev[0] == 0", binrevs.get(0) == 0);
        MatcherAssert.assertThat(
            "Last (max) recipe rev == 0",
            this.index.getLastRecipeRevision().join() == 0
        );
        MatcherAssert.assertThat(
            "Last (max) bin. rev == 0",
            this.index.getLastBinaryRevision(
                0, RevisionsIndexTest.ZLIB_BIN_PKG
            ).join() == 0
        );
    }

    @Test
    void updateRecipeIndexTest() {
        this.storage.delete(new Key.From(RevisionsIndexTest.ZLIB_SRC_INDEX)).join();
        final List<Integer> result = this.index.updateRecipeIndex().toCompletableFuture().join();
        final List<Integer> revs = this.index.getRecipeRevisions().toCompletableFuture().join();
        MatcherAssert.assertThat("Expect 1 recipe rev.", result.size() == 1);
        MatcherAssert.assertThat("rev[0] == 0", result.get(0) == 0);
        MatcherAssert.assertThat("Expect 1 recipe rev.", revs.size() == 1);
        MatcherAssert.assertThat("rev[0] == 0", revs.get(0) == 0);
    }

    @Test
    void updateBinaryIndexTest() {
        this.storage.delete(new Key.From(RevisionsIndexTest.ZLIB_BIN_INDEX)).join();
        final List<Integer> result = this.index.updateBinaryIndex(
            0, RevisionsIndexTest.ZLIB_BIN_PKG
            ).toCompletableFuture().join();
        final List<Integer> binrevs = this.index.getBinaryRevisions(
            0, RevisionsIndexTest.ZLIB_BIN_PKG
        ).toCompletableFuture().join();
        MatcherAssert.assertThat("Expect 1 bin. rev.", binrevs.size() == 1);
        MatcherAssert.assertThat("binrev[0] == 0", binrevs.get(0) == 0);
        MatcherAssert.assertThat("Expect 1 bins rev.", result.size() == 1);
        MatcherAssert.assertThat("rev[0] == 0", result.get(0) == 0);
    }

    @Test
    void fullIndexUpdateTest() {
        this.storage.delete(new Key.From(RevisionsIndexTest.ZLIB_SRC_INDEX)).join();
        this.storage.delete(new Key.From(RevisionsIndexTest.ZLIB_BIN_INDEX)).join();
        this.index.fullIndexUpdate().toCompletableFuture().join();
        final List<Integer> revs = this.index.getRecipeRevisions().toCompletableFuture().join();
        final List<Integer> binrevs = this.index.getBinaryRevisions(
            0, RevisionsIndexTest.ZLIB_BIN_PKG
        ).toCompletableFuture().join();
        MatcherAssert.assertThat("Expect 1 recipe rev.", revs.size() == 1);
        MatcherAssert.assertThat("rev[0] == 0", revs.get(0) == 0);
        MatcherAssert.assertThat("Expect 1 bin. rev.", binrevs.size() == 1);
        MatcherAssert.assertThat("binrev[0] == 0", binrevs.get(0) == 0);
    }

    @Test
    void recipeRevisionsUpdateTest() {
        MatcherAssert.assertThat(
            "Last recipe rev. == 0",
            this.index.getLastRecipeRevision().join() == 0
        );
        this.index.addRecipeRevision(1).toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Last recipe rev == 1",
            this.index.getLastRecipeRevision().join() == 1
        );
        this.index.removeRecipeRevision(1).toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Last recipe rev == 0",
            this.index.getLastRecipeRevision().join() == 0
        );
    }

    @Test
    void binaryRevisionsUpdateTest() {
        this.index.addBinaryRevision(0, RevisionsIndexTest.ZLIB_BIN_PKG, 1)
            .toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Last bin. rev == 1",
            this.index.getLastBinaryRevision(
                0, RevisionsIndexTest.ZLIB_BIN_PKG
            ).join() == 1
        );
        this.index.removeBinaryRevision(0, RevisionsIndexTest.ZLIB_BIN_PKG, 1)
            .toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Last bin. rev == 0",
            this.index.getLastBinaryRevision(
                0, RevisionsIndexTest.ZLIB_BIN_PKG
            ).join() == 0
        );
    }
}
