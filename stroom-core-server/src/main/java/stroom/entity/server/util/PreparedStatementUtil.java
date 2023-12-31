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

package stroom.entity.server.util;

import stroom.util.logging.StroomLogger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;

/**
 * Utility Class
 */
public class PreparedStatementUtil {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(PreparedStatementUtil.class);

    public static void setArguments(final PreparedStatement ps, final Iterable<Object> args) throws SQLException {
        if (args != null) {
            int index = 1;
            for (final Object o : args) {
                try {
                    if (o instanceof Long) {
                        ps.setLong(index, (Long) o);
                    } else if (o instanceof Integer) {
                        ps.setInt(index, (Integer) o);
                    } else if (o instanceof Double) {
                        ps.setDouble(index, (Double) o);
                    } else if (o instanceof Byte) {
                        ps.setByte(index, (Byte) o);
                    } else if (o instanceof String) {
                        ps.setString(index, ((String) o));
                    } else if (o instanceof Boolean) {
                        ps.setBoolean(index, ((Boolean) o));
                    } else {
                        ps.setObject(index, o);
                    }
                } catch (final SQLSyntaxErrorException syntaxError) {
                    throw new SQLSyntaxErrorException("Unable to set arg " + index + " (" + o + ") in arg list " + args);
                }
                index++;
            }
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public static PreparedStatement prepareStatement(final Connection connection, final String sql)
            throws SQLException {
        try {
            return connection.prepareStatement(sql);
        } catch (final SQLException sqlEx) {
            LOGGER.error("prepareStatement() - %s %s", sql, sqlEx.getMessage());
            throw sqlEx;
        }
    }

    public static ResultSet createCloseStatementResultSet(final PreparedStatement statement) throws SQLException {
        final ResultSet resultSet = statement.executeQuery();
        return (ResultSet) Proxy.newProxyInstance(SqlUtil.class.getClassLoader(), new Class[]{ResultSet.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(final Object proxy, final Method method, final Object[] args)
                            throws Throwable {
                        try {
                            final Object r = method.invoke(resultSet, args);
                            if (method.getName().equals("close")) {
                                statement.close();
                            }
                            return r;
                        } catch (final Throwable th) {
                            throw th;
                        }
                    }
                });
    }
}
