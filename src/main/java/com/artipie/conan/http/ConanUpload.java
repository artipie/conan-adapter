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

import com.artipie.asto.Storage;
import com.artipie.conan.ConanRepo;
import com.artipie.conan.ConanRepoConfig;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import java.nio.ByteBuffer;
import java.util.Map;
import org.reactivestreams.Publisher;

/**
 * Slice for Conan package data uploading support.
 * @since 0.1
 */
public final class ConanUpload implements Slice {

    /**
     * Asto storage.
     */
    private final Storage asto;

    /**
     * Rpm instance.
     */
    private final ConanRepo repo;

    /**
     * Ctor.
     * @param storage Storage object.
     * @param config Config object.
     */
    public ConanUpload(final Storage storage, final ConanRepoConfig config) {
        this.asto = storage;
        this.repo = new ConanRepo(storage, config);
    }

    /**
     * Returns storage.
     * @return Asto storage object.
     */
    public Storage getAsto() {
        return this.asto;
    }

    /**
     * Returns repository.
     * @return Repository object.
     */
    public ConanRepo getRepo() {
        return this.repo;
    }

    @Override
    public Response response(final String line, final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body) {
        return null;
    }
}
