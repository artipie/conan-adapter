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

import io.vavr.Tuple2;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Encapsulates handling of collections of CompletableFutures.
 * Here CompletableFuture::join() won't block since it's used after CompletableFuture.allOf().
 * @since 0.1
 */
public final class Completables {

    /**
     * Private ctor. Static methods only.
     */
    private Completables() {
    }

    /**
     * Returns CompletableFuture with extracted results from all CompletableFutures in the List.
     * @param futures List of CompletableFuture objects to wait & collect result.
     * @param <T> Type of the data for CompletableFuture.
     * @return CompletableFuture with the List of results.
     */
    public static <T> CompletableFuture<List<T>> forItems(
        final List<CompletableFuture<T>> futures
    ) {
        @SuppressWarnings("rawtypes") final CompletableFuture[] arr = futures
            .toArray(new CompletableFuture[0]);
        return CompletableFuture.allOf(arr).thenApply(
            nothing -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList())
        );
    }

    /**
     * Returns CompletableFuture with extracted results from all CompletableFutures in the tuples.
     * @param futures List of Tuples of Key + its CompletableFuture.
     * @param <K> Type of the key.
     * @param <V> Type of the value, CompletableFuture result.
     * @return CompletableFuture with the List of Tuples with K & V directly.
     */
    public static <K, V> CompletableFuture<List<Tuple2<K, V>>> forTuples(
        final List<Tuple2<K, CompletableFuture<V>>> futures
    ) {
        @SuppressWarnings("rawtypes") final CompletableFuture[] arr = futures
            .stream().map(Tuple2::_2).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(arr).thenApply(
            nothing -> futures.stream().map(
                tuple -> new Tuple2<>(tuple._1(), tuple._2().join())
            ).collect(Collectors.toList())
        );
    }
}
