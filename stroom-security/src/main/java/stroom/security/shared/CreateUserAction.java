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

package stroom.security.shared;

import stroom.dispatch.shared.Action;

public class CreateUserAction extends Action<UserRef> {
    private String name;
    private boolean group;

    public CreateUserAction() {
    }

    public CreateUserAction(final String name, final boolean group) {
        this.name = name;
        this.group = group;
    }

    public String getName() {
        return name;
    }

    public boolean isGroup() {
        return group;
    }

    @Override
    public String getTaskName() {
        return "Create user";
    }
}
