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
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.conan.ConanRepoConfig;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.vertx.VertxSliceServer;
import com.jcabi.log.Logger;
import io.vertx.reactivex.core.Vertx;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;

/**
 * Test case for {@link ConanSlice}.
 * Suppressed PMD.AvoidDuplicateLiterals due to CLI commands.
 * @since 0.1
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class ConanSliceTest {
    /**
     * Vertx instance.
     */
    private static final Vertx VERTX = Vertx.vertx();

    /**
     * Temporary directory for all tests.
     * @checkstyle VisibilityModifierCheck (3 lines)
     */
    @TempDir
    Path tmp;

    /**
     * Test storage.
     */
    private Storage storage;

    /**
     * Artipie port.
     */
    private int port;

    /**
     * Vertx slice server instance.
     */
    private VertxSliceServer server;

    /**
     * Container.
     */
    private GenericContainer<?> cntn;

    public Storage getStorage() {
        return this.storage;
    }

    public int getPort() {
        return this.port;
    }

    @BeforeEach
    void init() throws IOException, InterruptedException {
        this.storage = new InMemoryStorage();
        this.server = new VertxSliceServer(
            ConanSliceTest.VERTX,
                new LoggingSlice(
                    new ConanSlice(
                        this.storage,
                        new ConanRepoConfig.Simple()
                    )
                )
            );
        this.server.start();
        this.cntn = new GenericContainer<>("conanio/gcc8")
            .withCommand("tail", "-f", "/dev/null")
            .withWorkingDirectory("/home/")
            .withFileSystemBind(this.tmp.toString(), "/home");
        this.cntn.start();
        Logger.debug(
            this, this.cntn.execInContainer(
            "conan", "profile", "new", "--detect", "default"
            ).getStdout()
        );
        Logger.debug(
            this, this.cntn.execInContainer(
            "conan", "profile", "update", "settings.compiler.libcxx=libstdc++11", "default"
            ).getStdout()
        );
        Logger.debug(
            this, this.cntn.execInContainer(
            "conan", "profile", "show", "default"
            ).getStdout()
        );
        Logger.debug(
            this, this.cntn.execInContainer(
            "conan", "remote", "add", "conan-local", "http://localhost:9300"
            ).getStdout()
        );
    }

    @Test
    void fetchWorks() throws Exception {
        Logger.debug(
            this, this.cntn.execInContainer(
            "conan", "download", "-r", "conan-local", "zlib/1.2.11@"
            ).getStdout()
        );
    }

    @AfterEach
    void stop() {
        this.server.stop();
        this.cntn.stop();
    }
}
