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

package stroom.streamstore.shared;

public class StreamPermissionException extends RuntimeException {
    private static final long serialVersionUID = -4440960036445588068L;

    private String user;

    public StreamPermissionException(final String user,
                                     final String message) {
        super(message);
        this.user = user;
    }

    @Override
    public String getMessage() {
        String message = getGenericMessage();
        if (message != null) {
            message = message.replace("You do", "User does");

            if (user != null && user.length() > 0) {
                message = message.replace("User does", "User '" + user + "' does");
            }
        }

        return message;
    }

    public String getGenericMessage() {
        return super.getMessage();
    }
}
