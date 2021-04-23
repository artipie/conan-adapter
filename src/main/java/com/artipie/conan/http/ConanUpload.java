package com.artipie.conan.http;

import com.artipie.asto.Storage;
import com.artipie.conan.ConanRepo;
import com.artipie.conan.ConanRepoConfig;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import org.reactivestreams.Publisher;

import java.nio.ByteBuffer;
import java.util.Map;

public class ConanUpload implements Slice {

    /**
     * Asto storage.
     */
    private final Storage asto;

    /**
     * Rpm instance.
     */
    private final ConanRepo repo;

    public ConanUpload(Storage storage, ConanRepoConfig config) {
        this.asto = storage;
        this.repo = new ConanRepo(storage, config);
    }

    @Override
    public Response response(String s, Iterable<Map.Entry<String, String>> iterable, Publisher<ByteBuffer> publisher) {
        return null;
    }
}
