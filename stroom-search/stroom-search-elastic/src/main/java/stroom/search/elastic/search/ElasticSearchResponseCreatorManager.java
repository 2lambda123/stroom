/*
 * Copyright 2017 Crown Copyright
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

package stroom.search.elastic.search;

import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorCacheFactory;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.query.common.v2.StoreFactory;
import stroom.util.spring.StroomFrequencySchedule;

import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;

@SuppressWarnings("unused") // used by DI
@Component("elasticSearchResponseCreatorManager")
public class ElasticSearchResponseCreatorManager implements SearchResponseCreatorManager {

    private final SearchResponseCreatorCache cache;

    @Inject
    public ElasticSearchResponseCreatorManager(
            @Named("elasticInMemorySearchResponseCreatorCacheFactory") final SearchResponseCreatorCacheFactory cacheFactory,
            @Named("elasticSearchStoreFactory") final StoreFactory storeFactory) {

        SearchResponseCreatorCache cache = cacheFactory.create(storeFactory);
        this.cache = cache;
    }

    @Override
    public SearchResponseCreator get(final SearchResponseCreatorCache.Key key) {
        return cache.get(key);
    }

    @Override
    public void remove(final SearchResponseCreatorCache.Key key) {
        cache.remove(key);
    }

    @Override
    @SuppressWarnings("unused") //called by stroom lifecycle
    @StroomFrequencySchedule("10s")
    public void evictExpiredElements() {
        cache.evictExpiredElements();
    }
}