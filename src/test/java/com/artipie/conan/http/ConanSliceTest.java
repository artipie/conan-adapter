package com.artipie.conan.http;

import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.conan.ConanRepoConfig;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.vertx.VertxSliceServer;
import com.jcabi.log.Logger;
import io.vertx.reactivex.core.Vertx;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNot;
import org.hamcrest.core.StringContains;
import org.hamcrest.text.MatchesPattern;
import org.hamcrest.text.StringContainsInOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.testcontainers.containers.GenericContainer;
import static org.junit.jupiter.api.Assertions.*;

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
        // saving as local temp container or prepare it in advance separately?
        Logger.debug(this, this.cntn.execInContainer("conan", "profile", "new", "--detect", "default").getStdout());
        Logger.debug(this, this.cntn.execInContainer("conan", "profile", "update", "settings.compiler.libcxx=libstdc++11", "default").getStdout());
        Logger.debug(this, this.cntn.execInContainer("conan", "profile", "show", "default").getStdout());
    }

    @Test
    void fetchWorks() throws Exception {
        //
    }

    @AfterEach
    void stop() {
        this.server.stop();
        this.cntn.stop();
    }
}