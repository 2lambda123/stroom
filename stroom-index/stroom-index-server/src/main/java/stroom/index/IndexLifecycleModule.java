/*
 * Copyright 2018 Crown Copyright
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

package stroom.index;

import stroom.util.lifecycle.AbstractLifecycleModule;
import stroom.util.lifecycle.RunnableWrapper;

import javax.inject.Inject;

public class IndexLifecycleModule extends AbstractLifecycleModule {
    @Override
    protected void configure() {
        super.configure();
        bindStartup().to(IndexShardWriterCacheStartup.class);
        bindShutdown().to(IndexShardWriterCacheShutdown.class);
    }

    private static class IndexShardWriterCacheStartup extends RunnableWrapper {
        @Inject
        IndexShardWriterCacheStartup(final IndexShardWriterCacheImpl indexShardWriterCache) {
            super(indexShardWriterCache::startup);
        }
    }

    private static class IndexShardWriterCacheShutdown extends RunnableWrapper {
        @Inject
        IndexShardWriterCacheShutdown(final IndexShardWriterCacheImpl indexShardWriterCache) {
            super(indexShardWriterCache::shutdown);
        }
    }
}