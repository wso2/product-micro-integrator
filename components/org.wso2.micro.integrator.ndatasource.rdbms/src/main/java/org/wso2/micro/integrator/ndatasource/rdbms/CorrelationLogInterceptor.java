/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.ndatasource.rdbms;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.interceptor.AbstractQueryReport;

/**
 * Time-Logging interceptor for JDBC pool.
 * Logs the time taken to execute the query in each pool-ed connection.
 */

public class CorrelationLogInterceptor extends AbstractQueryReport {

    private static final Log correlationLog = LogFactory.getLog("correlation");
    private static Log log = LogFactory.getLog(CorrelationLogInterceptor.class);

    private static final String CORRELATION_LOG_CALL_TYPE_VALUE = "jdbc";
    private static final String CORRELATION_LOG_SEPARATOR = "|";
    private static final String CORRELATION_LOG_SYSTEM_PROPERTY = "enableCorrelationLogs";
    private static final String BLACKLISTED_THREADS_SYSTEM_PROPERTY =
            "org.wso2.CorrelationLogInterceptor.BlacklistedThreads";
    private static final String DEFAULT_BLACKLISTED_THREAD = "MessageDeliveryTaskThreadPool";
    private List<String> blacklistedThreadList = new ArrayList<>();

    public CorrelationLogInterceptor() {

        String blacklistedThreadNames = System.getProperty(BLACKLISTED_THREADS_SYSTEM_PROPERTY);

        if (blacklistedThreadNames == null) {
            blacklistedThreadList.add(DEFAULT_BLACKLISTED_THREAD);
        }

        if (!isEmpty(blacklistedThreadNames)) {
            blacklistedThreadList.addAll(Arrays.asList(split(blacklistedThreadNames, ',')));
        }
    }

    public boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    public static String[] split(String str, char separatorChar) {
        if (str == null) {
            return null;
        } else {
            int len = str.length();
            if (len == 0) {
                return new String[0];
            } else {
                List list = new ArrayList();
                int i = 0;
                int start = 0;
                boolean match = false;

                while(i < len) {
                    if (str.charAt(i) == separatorChar) {
                        if (match) {
                            list.add(str.substring(start, i));
                            match = false;
                        }

                        ++i;
                        start = i;
                    } else {
                        match = true;
                        ++i;
                    }
                }

                if (match) {
                    list.add(str.substring(start, i));
                }

                return (String[])list.toArray(new String[list.size()]);
            }
        }
    }

    @Override
    public void closeInvoked() {

    }

    @Override
    protected void prepareStatement(String s, long l) {

    }

    @Override
    protected void prepareCall(String s, long l) {

    }

    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {

        try {
            if (Boolean.parseBoolean(System.getProperty(CORRELATION_LOG_SYSTEM_PROPERTY))) {
                return invokeProxy(method, args, statement, time);
            } else {
                return statement;
            }

        } catch (Exception e) {
            log.warn("Unable to create statement proxy for slow query report.", e);
            return statement;
        }
    }

    private Object invokeProxy(Method method, Object[] args, Object statement, long time) throws Exception {

        String name = method.getName();
        String sql = null;
        Constructor<?> constructor = null;

        if (this.compare("prepareStatement", name)) {
            sql = (String) args[0];
            constructor = this.getConstructor(1, PreparedStatement.class);
            if (sql != null) {
                this.prepareStatement(sql, time);
            }
        } else if (this.compare("prepareCall", name)) {
            sql = (String) args[0];
            constructor = this.getConstructor(2, CallableStatement.class);
            this.prepareCall(sql, time);
        } else {
            return statement;
        }

        if (constructor != null) {
            return constructor.newInstance(new StatementProxy(statement, sql));
        } else {
            return null;
        }
    }

    /**
     * Proxy Class that is used to calculate and log the time taken for queries
     */
    protected class StatementProxy implements InvocationHandler {

        protected boolean closed = false;
        protected Object delegate;
        protected final String query;

        public StatementProxy(Object parent, String query) {

            this.delegate = parent;
            this.query = query;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            String name = method.getName();
            boolean close = CorrelationLogInterceptor.this.compare("close", name);
            //Checks if the method name is of type closed and will not log time taken for those methods
            if (close && this.closed) {
                return null;
            } else if (CorrelationLogInterceptor.this.compare("isClosed", name)) {
                return this.closed;
            } else if (this.closed) {
                throw new SQLException("Statement closed.");
            } else {
                boolean process;
                process = CorrelationLogInterceptor.this.isExecute(method, false);

                long start = System.currentTimeMillis();
                Object result = null;

                if (this.delegate != null) {
                    result = method.invoke(this.delegate, args);
                }

                //If the query is an execute type of query the time taken is calculated and logged
                if (process) {
                    long delta = System.currentTimeMillis() - start;
                    CorrelationLogInterceptor.this.reportQuery(this.query, args, name, start, delta);
                    logQueryDetails(start, delta, name);
                }

                if (close) {
                    this.closed = true;
                    this.delegate = null;
                }

                return result;
            }
        }

        /**
         * Logs the details from the query
         *
         * @param start      Query start time
         * @param delta      Time taken for query
         * @param methodName Name of the method executing
         */
        private void logQueryDetails(long start, long delta, String methodName) throws SQLException {

            if (!(this.delegate instanceof PreparedStatement)) {
                return;
            }

            PreparedStatement preparedStatement = (PreparedStatement) this.delegate;
            if (preparedStatement.getConnection() == null) {
                return;
            }

            if (isCurrentThreadBlacklisted()) {
                return;
            }

            DatabaseMetaData metaData = preparedStatement.getConnection().getMetaData();
            if (correlationLog.isInfoEnabled()) {
                List<String> logPropertiesList = new ArrayList<>();
                logPropertiesList.add(Long.toString(delta));
                logPropertiesList.add(CORRELATION_LOG_CALL_TYPE_VALUE);
                logPropertiesList.add(Long.toString(start));
                logPropertiesList.add(methodName);
                logPropertiesList.add(this.query);
                logPropertiesList.add(metaData.getURL());
                correlationLog.info(createFormattedLog(logPropertiesList));
            }
        }

        private boolean isCurrentThreadBlacklisted() {

            String threadName = Thread.currentThread().getName();

            for (String thread : blacklistedThreadList) {
                if (threadName.startsWith(thread)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Creates the log line that should be printed
         *
         * @param logPropertiesList Contains the log values that should be printed in the log
         * @return The log line
         */
        private String createFormattedLog(List<String> logPropertiesList) {

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String property : logPropertiesList) {
                sb.append(property);
                if (count < logPropertiesList.size() - 1) {
                    sb.append(CORRELATION_LOG_SEPARATOR);
                }
                count++;
            }
            return sb.toString();
        }
    }
}
