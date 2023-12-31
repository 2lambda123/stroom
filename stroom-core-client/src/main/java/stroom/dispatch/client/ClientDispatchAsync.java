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

package stroom.dispatch.client;

import stroom.dispatch.shared.Action;
import stroom.util.shared.SharedObject;
import stroom.widget.util.client.Future;

public interface ClientDispatchAsync {
    <R extends SharedObject> Future<R> exec(Action<R> task);

    <R extends SharedObject> Future<R> exec(Action<R> task, String message);

    <R extends SharedObject> Future<R> exec(Action<R> task, boolean showWorking);

    String getImportFileURL();
}
