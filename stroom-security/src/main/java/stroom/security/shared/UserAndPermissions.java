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

import stroom.util.shared.SharedObject;

import java.util.Set;

public class UserAndPermissions implements SharedObject {
    private static final long serialVersionUID = -174816031610623504L;

    private UserRef userRef;
    private Set<String> appPermissionSet;
    private Integer daysToPasswordExpiry;

    public UserAndPermissions() {
        // Default constructor necessary for GWT serialisation.
    }

    public UserAndPermissions(final UserRef userRef, final Set<String> appPermissionSet, final Integer daysToPasswordExpiry) {
        this.userRef = userRef;
        this.appPermissionSet = appPermissionSet;
        this.daysToPasswordExpiry = daysToPasswordExpiry;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public Set<String> getAppPermissionSet() {
        return appPermissionSet;
    }

    public Integer getDaysToPasswordExpiry() {
        return daysToPasswordExpiry;
    }
}
