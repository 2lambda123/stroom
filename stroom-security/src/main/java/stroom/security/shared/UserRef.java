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

import stroom.entity.shared.DocRef;

public class UserRef extends DocRef {
    private static final long serialVersionUID = 5883121212911541301L;

    private boolean group;
    private boolean enabled;

    public UserRef() {
        // Default constructor necessary for GWT serialisation.
    }

    public UserRef(final String type, final String uuid, final String name, final boolean group, final boolean enabled) {
        super(type, null, uuid, name);
        this.group = group;
        this.enabled = enabled;
    }

    public boolean isGroup() {
        return group;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
