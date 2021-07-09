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
import com.artipie.http.Headers;
import com.artipie.http.Slice;
import com.artipie.http.auth.Action;
import com.artipie.http.auth.Authentication;
import com.artipie.http.auth.BasicAuthSlice;
import com.artipie.http.auth.Permission;
import com.artipie.http.auth.Permissions;
import com.artipie.http.headers.ContentType;
import com.artipie.http.headers.Header;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithBody;
import com.artipie.http.rs.RsWithHeaders;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.rt.ByMethodsRule;
import com.artipie.http.rt.RtRule;
import com.artipie.http.rt.RtRulePath;
import com.artipie.http.rt.SliceRoute;
import com.artipie.http.slice.SliceDownload;
import com.artipie.http.slice.SliceSimple;
import com.google.common.collect.Lists;
import java.nio.charset.StandardCharsets;
import javax.json.Json;

/**
 * Artipie {@link Slice} for Conan repository HTTP API.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
public final class ConanSlice extends Slice.Wrap {

    /**
     * Ctor.
     * @param storage Storage object.
     */
    public ConanSlice(final Storage storage) {
        this(storage, Permissions.FREE, Authentication.ANONYMOUS);
    }

    /**
     * Ctor.
     *
     * @param storage Storage object.
     * @param perms Permissions.
     * @param auth Authentication parameters.
     */
    public ConanSlice(
        final Storage storage,
        final Permissions perms,
        final Authentication auth
    ) {
        super(
            new SliceRoute(
                new RtRulePath(
                    new RtRule.ByPath("^/v1/ping$"),
                    new SliceSimple(
                        new RsWithHeaders(
                            new RsWithStatus(RsStatus.ACCEPTED),
                            new Headers.From("X-Conan-Server-Capabilities", "")
                        )
                    )
                ),
                new RtRulePath(
                    new RtRule.ByPath("/v1/conans/search"),
                    new BasicAuthSlice(
                        new SliceSimple(
                            new RsWithBody(
                                new RsWithHeaders(
                                    new RsWithStatus(RsStatus.OK),
                                    new ContentType(String.format("application/json")),
                                    new Header("Server", "Artipie/0.1")
                                ),
                                Json.createObjectBuilder().add(
                                    "results", Json.createArrayBuilder(
                                        Lists.newArrayList(
                                            "test1/1.0", "test2/0.1"
                                            ))
                                ).build().toString().getBytes(StandardCharsets.UTF_8)
                            )
                        ),
                        auth,
                        new Permission.ByName(perms, Action.Standard.WRITE)
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath(ConansEntity.DLOAD_SRC_PATH.getPath()),
                        ByMethodsRule.Standard.GET
                    ),
                    new BasicAuthSlice(
                        new ConansEntity.GetDownload(storage),
                        auth,
                        new Permission.ByName(perms, Action.Standard.READ)
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath(ConansEntity.SEARCH_PKG_PATH.getPath()),
                        ByMethodsRule.Standard.GET
                    ),
                    new BasicAuthSlice(
                        new ConansEntity.GetSearchPkg(storage),
                        auth,
                        new Permission.ByName(perms, Action.Standard.READ)
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new RtRule.ByPath(ConansEntity.PKG_BIN_INFO_PATH.getPath()),
                        ByMethodsRule.Standard.GET
                    ),
                    new BasicAuthSlice(
                        new ConansEntity.GetPkgInfo(storage),
                        auth,
                        new Permission.ByName(perms, Action.Standard.READ)
                    )
                ),
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
                        new ConanUpload(storage),
                        auth,
                        new Permission.ByName(perms, Action.Standard.WRITE)
                    )
                )
            )
        );
    }
}
