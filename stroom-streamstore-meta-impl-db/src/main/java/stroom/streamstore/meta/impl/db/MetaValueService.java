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

package stroom.streamstore.meta.impl.db;

import stroom.entity.shared.Clearable;
import stroom.entity.shared.Flushable;
import stroom.streamstore.meta.api.Stream;
import stroom.streamstore.shared.StreamDataRow;

import java.util.List;
import java.util.Map;

interface MetaValueService extends Flushable, Clearable {
    void addAttributes(Stream stream, Map<String, String> attributes);

    List<StreamDataRow> decorateStreamsWithAttributes(List<Stream> streamList);
}
