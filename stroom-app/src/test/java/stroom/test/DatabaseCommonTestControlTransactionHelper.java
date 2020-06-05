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

package stroom.test;

import stroom.legacy.db.LegacyDbConnProvider;
import stroom.test.common.util.db.DbTestUtil;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * <p>
 * Class to help with testing.
 * </p>
 */
class DatabaseCommonTestControlTransactionHelper {
    private final LegacyDbConnProvider legacyDbConnProvider;

    @Inject
    DatabaseCommonTestControlTransactionHelper(final LegacyDbConnProvider legacyDbConnProvider) {
        this.legacyDbConnProvider = legacyDbConnProvider;
    }

    void clearAllTables() {
        try (final Connection connection = legacyDbConnProvider.getConnection()) {
            DbTestUtil.clearAllTables(connection);
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
