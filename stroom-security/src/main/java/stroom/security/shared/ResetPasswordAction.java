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

public class ResetPasswordAction extends Action<UserRef> {
    private static final long serialVersionUID = -6740095230475597845L;

    public ResetPasswordAction() {
    }

    public ResetPasswordAction(final UserRef user, String password) {
        this.user = user;
        this.password = password;
    }

    private UserRef user;
    private String password;

    public UserRef getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String getTaskName() {
        return "Reset Password";
    }
}
