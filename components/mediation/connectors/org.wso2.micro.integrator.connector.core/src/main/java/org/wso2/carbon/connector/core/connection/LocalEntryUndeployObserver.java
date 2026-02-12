/*
 *  Copyright (c) 2023, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.connector.core.connection;


import org.apache.synapse.config.AbstractSynapseObserver;
import org.apache.synapse.config.Entry;

/**
 * Listen for local entry un deploy events
 * and cleanup connections originated by that local entry.
 */
public class LocalEntryUndeployObserver extends AbstractSynapseObserver {
    private final String localEntryName;

    private LocalEntryUndeployCallBack callback;

    public LocalEntryUndeployObserver(String localEntryName) {
        this.localEntryName = localEntryName;
    }

    @Override
    public void entryRemoved(Entry entry) {
        if (this.callback != null && entry.getKey().equals(localEntryName)) {
            this.callback.onLocalEntryUndeploy(entry.getKey());
        }
    }
    public void setCallback(LocalEntryUndeployCallBack callback) {
        this.callback = callback;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalEntryUndeployObserver localEntryUndeployObserver = (LocalEntryUndeployObserver) o;
        return localEntryName.equals(localEntryUndeployObserver.localEntryName);
    }

    public int hashCode() {
        return localEntryName.hashCode();
    }

}
