/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.server.xsltfunctions;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.util.spring.StroomScope;

import java.util.HashMap;
import java.util.Map;

@Component
@Scope(value = StroomScope.TASK)
class TaskScopeMap {
    private final Map<String, String> map = new HashMap<>();

    void put(final String key, final String value) {
        map.put(key, value);
    }

    String get(final String key) {
        return map.get(key);
    }
}
