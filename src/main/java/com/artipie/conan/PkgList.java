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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Lists binary packages of Conan package.
 * @since 0.1
 */
public class PkgList {

    /**
     * Manifest file stores list of package files with their hashes.
     */
    private static final String CONAN_MANIFEST = "conanmanifest.txt";

    /**
     * File with binary package information on corresponding build configuration.
     */
    private static final String CONAN_INFO = "conaninfo.txt";

    /**
     * Main files of package recipe.
     */
    public static final List<String> PKG_SRC_LIST = Collections.unmodifiableList(
        Arrays.asList(
            PkgList.CONAN_MANIFEST, "conan_export.tgz",
            "conanfile.py", "conan_sources.tgz"
        ));

    /**
     * Main files of package binary.
     */
    public static final List<String> PKG_BIN_LIST = Collections.unmodifiableList(
        Arrays.asList(
            PkgList.CONAN_MANIFEST, PkgList.CONAN_INFO, "conan_package.tgz"
        ));

    /**
     * Current Artipie storage instance.
     */
    private final Storage storage;

    /**
     * Initializes new instance.
     * @param storage Current Artipie storage instance.
     */
    public PkgList(final Storage storage) {
        this.storage = storage;
    }

    /**
     * Returns binary packages list (of hashes) for given recipe revision.
     * @param path Path to package.
     * @return CompletionStage with the list of package binaries (hashes) as strings.
     */
    public CompletionStage<List<String>> getPackageList(final String path) {
        return this.storage.list(new Key.From(path)).thenApply(
            keys -> new ArrayList<>(
                keys.stream().map(key -> PkgList.getNextSubdir(path, key))
                    .filter(s -> s.length() > 0).collect(Collectors.toSet())
            )
        );
    }

    /**
     * Extracts next subdir in key, starting from the base path.
     * @param base Base path for key.
     * @param key Artipie storage key with full path.
     * @return Next subdir name after base, or empty string if none.
     */
    private static String getNextSubdir(final String base, final Key key) {
        final int next = key.string().indexOf('/', base.length() + 1);
        final String result;
        if (next < 0) {
            result = "";
        } else {
            result = key.string().substring(base.length() + 1, next);
        }
        return result;
    }
}
