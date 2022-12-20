/*
 * Copyright 2022 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.view.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.view.shared.ViewDoc;
import stroom.view.shared.ViewResource;
import stroom.util.shared.EntityServiceException;

import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class ViewResourceImpl implements ViewResource {

    private final Provider<ViewStore> viewStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    ViewResourceImpl(final Provider<ViewStore> viewStoreProvider,
                      final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.viewStoreProvider = viewStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public ViewDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(viewStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public ViewDoc update(final String uuid, final ViewDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(viewStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(ViewDoc.DOCUMENT_TYPE)
                .build();
    }
}
