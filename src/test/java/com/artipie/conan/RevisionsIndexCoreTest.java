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

import com.artipie.asto.memory.InMemoryStorage;
import java.util.Arrays;
import java.util.List;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for RevisionsIndexCoreTest class.
 * @since 0.1
 * @checkstyle MagicNumberCheck (199 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class RevisionsIndexCoreTest {

    /**
     * Test instance.
     */
    private RevisionsIndexCore core;

    @BeforeEach
    public void setUp() {
        this.core = new RevisionsIndexCore(new InMemoryStorage());
    }

    @Test
    public void noRevdataSize() {
        final String path = "revisions.new";
        MatcherAssert.assertThat(
            "Revisions list size is incorrect",
            this.core.getRevisions(path).toCompletableFuture().join().size() == 0
        );
    }

    @Test
    public void emptyRevdataSize() {
        final String path = "revisions.new";
        this.core.addToRevdata(0, path).join();
        this.core.removeRevision(0, path).join();
        MatcherAssert.assertThat(
            "Revisions list size is incorrect",
            this.core.getRevisions(path).toCompletableFuture().join().size() == 0
        );
    }

    @Test
    public void fillNewRevdata() {
        final String path = "revisions.new";
        this.core.addToRevdata(1, path).join();
        this.core.addToRevdata(2, path).join();
        this.core.addToRevdata(3, path).join();
        final List<Integer> revs = this.core.getRevisions(path).toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Revisions list size is incorrect",
            revs.size() == 3
        );
        MatcherAssert.assertThat(
            "Revisions list contents is incorrect",
            revs.equals(Arrays.asList(1, 2, 3))
        );
    }

    @Test
    public void removeFromNoRevdata() {
        final String path = "revisions.new";
        this.core.removeRevision(0, path).join();
        MatcherAssert.assertThat(
            "Revisions list size is incorrect",
            this.core.getRevisions(path).toCompletableFuture().join().size() == 0
        );
    }

    @Test
    public void removeFromEmptyRevdata() {
        final String path = "revisions.new";
        this.core.addToRevdata(0, path).join();
        this.core.removeRevision(0, path).join();
        this.core.removeRevision(0, path).join();
        MatcherAssert.assertThat(
            "Revisions list size is incorrect",
            this.core.getRevisions(path).toCompletableFuture().join().size() == 0
        );
    }

    @Test
    public void removeFromRevdata() {
        final String path = "revisions.new";
        this.core.addToRevdata(0, path).join();
        this.core.addToRevdata(1, path).join();
        this.core.addToRevdata(2, path).join();
        this.core.removeRevision(1, path).join();
        MatcherAssert.assertThat(
            "Revisions list size is incorrect",
            this.core.getRevisions(path).toCompletableFuture().join().size() == 2
        );
        MatcherAssert.assertThat(
            "Revisions list contents is incorrect",
            this.core.getRevisions(path).toCompletableFuture().join().equals(Arrays.asList(0, 2))
        );
    }

    @Test
    public void emptyRevValue() {
        final String path = "revisions.new";
        MatcherAssert.assertThat(
            "Revision value is incorrect",
            this.core.getLastRev(path).toCompletableFuture().join().equals(-1)
        );
    }

    @Test
    public void lastRevValue() {
        final String path = "revisions.new";
        this.core.addToRevdata(1, path).join();
        this.core.addToRevdata(3, path).join();
        this.core.addToRevdata(2, path).join();
        MatcherAssert.assertThat(
            "Revision value is incorrect",
            this.core.getLastRev(path).toCompletableFuture().join().equals(3)
        );
    }
}
