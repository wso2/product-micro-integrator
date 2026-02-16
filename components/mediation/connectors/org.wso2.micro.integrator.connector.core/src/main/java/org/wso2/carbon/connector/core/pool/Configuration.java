/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.connector.core.pool;

/**
 * Holds the connection pool configurations
 *
 * @deprecated This class is deprecated. Use the new connector core module available at
 * <a href="https://github.com/wso2-extensions/mi-connector-core">https://github.com/wso2-extensions/mi-connector-core</a>
 */
@Deprecated
public class Configuration {

    private Integer maxActiveConnections;
    private Integer maxIdleConnections;
    private Integer minIdleConnections;
    private Long maxWaitTime;
    private long poolConnectionAgedTimeout = 0;
    private int retryCount = 5;
    private boolean isAgedTimeoutEnabled = false;
    private Long minEvictionTime;
    private Long evictionCheckInterval;
    private String exhaustedAction;
    private Boolean testOnReturn;
    private Boolean testOnBorrow;
    private Boolean testWhileIdle;
    private Integer numTestsPerEvictionRun;
    private Long softMinEvictableIdleTimeMillis;

    public Integer getMaxActiveConnections() {

        return maxActiveConnections;
    }

    public void setMaxActiveConnections(Integer maxActiveConnections) {

        this.maxActiveConnections = maxActiveConnections;
    }

    public Integer getMaxIdleConnections() {

        return maxIdleConnections;
    }

    public void setMaxIdleConnections(Integer maxIdleConnections) {

        this.maxIdleConnections = maxIdleConnections;
    }

    public Integer getMinIdleConnections() {

        return minIdleConnections;
    }

    public void setMinIdleConnections(Integer minIdleConnections) {

        this.minIdleConnections = minIdleConnections;
    }

    public Long getMaxWaitTime() {

        return maxWaitTime;
    }

    public void setMaxWaitTime(Long maxWaitTime) {

        this.maxWaitTime = maxWaitTime;
    }

    public Long getMinEvictionTime() {

        return minEvictionTime;
    }

    public void setMinEvictionTime(Long minEvictionTime) {

        this.minEvictionTime = minEvictionTime;
    }

    public Long getEvictionCheckInterval() {

        return evictionCheckInterval;
    }

    public void setEvictionCheckInterval(Long evictionCheckInterval) {

        this.evictionCheckInterval = evictionCheckInterval;
    }

    public String getExhaustedAction() {

        return exhaustedAction;
    }

    public void setExhaustedAction(String exhaustedAction) {

        this.exhaustedAction = exhaustedAction;
    }

    public Boolean getTestOnReturn() {

        return testOnReturn;
    }

    public void setTestOnReturn(Boolean testOnReturn) {

        this.testOnReturn = testOnReturn;
    }

    public Boolean getTestOnBorrow() {

        return testOnBorrow;
    }

    public void setTestOnBorrow(Boolean testOnBorrow) {

        this.testOnBorrow = testOnBorrow;
    }

    public Boolean getTestWhileIdle() {

        return testWhileIdle;
    }

    public void setTestWhileIdle(Boolean testWhileIdle) {

        this.testWhileIdle = testWhileIdle;
    }

    public Integer getNumTestsPerEvictionRun() {

        return numTestsPerEvictionRun;
    }

    public void setNumTestsPerEvictionRun(Integer numTestsPerEvictionRun) {

        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    public Long getSoftMinEvictableIdleTimeMillis() {

        return softMinEvictableIdleTimeMillis;
    }

    public void setSoftMinEvictableIdleTimeMillis(Long softMinEvictableIdleTimeMillis) {

        this.softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
    }


    public long getPoolConnectionAgedTimeout() {
        return poolConnectionAgedTimeout;
    }

    public void setPoolConnectionAgedTimeout(long poolConnectionAgedTimeout) {
        this.poolConnectionAgedTimeout = poolConnectionAgedTimeout;
    }

    public boolean isAgedTimeoutEnabled() {
        return isAgedTimeoutEnabled;
    }

    public void setAgedTimeoutEnabled(boolean agedTimeoutEnabled) {
        isAgedTimeoutEnabled = agedTimeoutEnabled;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
}
