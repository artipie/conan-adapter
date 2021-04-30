package com.artipie.conan.http;

import com.artipie.asto.Storage;
import com.artipie.conan.ConanRepoConfig;
import com.artipie.http.Slice;
import com.artipie.http.auth.*;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rt.ByMethodsRule;
import com.artipie.http.rt.RtRulePath;
import com.artipie.http.rt.SliceRoute;
import com.artipie.http.slice.SliceDownload;

public class ConanSlice extends Slice.Wrap  {

    ConanSlice(Storage storage) {
        this(storage, Permissions.FREE, Authentication.ANONYMOUS, new ConanRepoConfig.Simple());
    }

    ConanSlice(Storage storage, ConanRepoConfig config) {
        this(storage, Permissions.FREE, Authentication.ANONYMOUS, config);
    }

    public ConanSlice(
            final Storage storage,
            final Permissions perms,
            final Authentication auth,
            final ConanRepoConfig config
    ) {
        super(
                new SliceRoute(
                        new RtRulePath(
                                new ByMethodsRule(RqMethod.GET),
                                new BasicAuthSlice(
                                        new SliceDownload(storage),
                                        auth,
                                        new Permission.ByName(perms, Action.Standard.READ)
                                )
                        ),
                        new RtRulePath(
                                new ByMethodsRule(RqMethod.PUT),
                                new BasicAuthSlice(
                                        new ConanUpload(storage, config),
                                        auth,
                                        new Permission.ByName(perms, Action.Standard.WRITE)
                                )
                        )
                )
        );
    }
}
