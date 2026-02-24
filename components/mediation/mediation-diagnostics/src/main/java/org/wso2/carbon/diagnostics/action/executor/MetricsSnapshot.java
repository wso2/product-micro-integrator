/*
 *  Copyright (c) 2024, WSO2 LLC. (http://www.wso2.org).
 *
 *   WSO2 LLC. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.diagnostics.action.executor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wso2.diagnostics.actionexecutor.ActionExecutor;
import org.wso2.diagnostics.actionexecutor.ServerProcess;
import org.wso2.diagnostics.utils.JMXDataRetriever;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.wso2.diagnostics.utils.JMXDataRetriever.getAttributeValue;

public class MetricsSnapshot implements ActionExecutor {
    private static final Logger log = LogManager.getLogger(MetricsSnapshot.class);

    private final String pid;

    public MetricsSnapshot() {
        this.pid = ServerProcess.getProcessId();
    }

    /**
     * Method used to take server information.
     *
     * @param folderPath folder path of the dump folder
     */
    @Override
    public void execute(String folderPath) {

        File folder = new File(folderPath);
        if (folder.isDirectory()) {

            String filepath = folderPath + "/metrics-snapshot.txt";
            try (FileWriter writer = new FileWriter(filepath)) {
                writer.write(getServerMetrics());
                log.info("MetricsSnapshot executed successfully.");
            } catch (IOException e) {
                log.error("Unable to write server information to file.", e);
            }
        }
    }

    /**
     * Method used to get server information.
     *
     * @return server information
     */
    public String getServerMetrics() {
        String metrics = "";
        metrics += "Server Metrics\n";
        metrics += "==============\n";
        metrics += "Memory Usage: " + JMXDataRetriever.getMemoryUsage(pid) + "%\n";
        metrics += "CPU Usage: " + JMXDataRetriever.getCpuUsage(pid) + "%\n";
        metrics += "\n";

        metrics += "Http Listener Metrics\n";
        metrics += "==============\n";
        metrics += "Active Connections: " + getAttributeValue("http-listener", pid, "ActiveConnections") + "\n";
        metrics += "LastSecondConnections: " + getAttributeValue("http-listener", pid, "LastSecondConnections") + "\n";
        metrics += "Last5SecondConnections: " + getAttributeValue("http-listener", pid, "Last5SecondConnections") + "\n";
        metrics += "Last15SecondConnections: " + getAttributeValue("http-listener", pid, "Last15SecondConnections") + "\n";
        metrics += "LastMinuteConnections: " + getAttributeValue("http-listener", pid, "LastMinuteConnections") + "\n";
        metrics += "LastSecondRequests: " + getAttributeValue("http-listener", pid, "LastSecondRequests") + "\n";
        metrics += "Last15SecondRequests: " + getAttributeValue("http-listener", pid, "Last15SecondRequests") + "\n";
        metrics += "LastMinuteRequests: " + getAttributeValue("http-listener", pid, "LastMinuteRequests") + "\n";

        metrics += "\n";
        metrics += "Https Listener Metrics\n";
        metrics += "==============\n";
        metrics += "Active Connections: " + getAttributeValue("https-listener", pid, "ActiveConnections") + "\n";
        metrics += "LastSecondConnections: " + getAttributeValue("https-listener", pid, "LastSecondConnections") + "\n";
        metrics += "Last5SecondConnections: " + getAttributeValue("https-listener", pid, "Last5SecondConnections") + "\n";
        metrics += "Last15SecondConnections: " + getAttributeValue("https-listener", pid, "Last15SecondConnections") + "\n";
        metrics += "LastMinuteConnections: " + getAttributeValue("https-listener", pid, "LastMinuteConnections") + "\n";
        metrics += "LastSecondRequests: " + getAttributeValue("https-listener", pid, "LastSecondRequests") + "\n";
        metrics += "Last15SecondRequests: " + getAttributeValue("https-listener", pid, "Last15SecondRequests") + "\n";
        metrics += "LastMinuteRequests: " + getAttributeValue("https-listener", pid, "LastMinuteRequests") + "\n";

        metrics += "\n";
        metrics += "Http Sender Metrics\n";
        metrics += "==============\n";
        metrics += "Active Connections: " + getAttributeValue("http-sender", pid, "ActiveConnections") + "\n";
        metrics += "LastSecondConnections: " + getAttributeValue("http-sender", pid, "LastSecondConnections") + "\n";
        metrics += "Last5SecondConnections: " + getAttributeValue("http-sender", pid, "Last5SecondConnections") + "\n";
        metrics += "Last15SecondConnections: " + getAttributeValue("http-sender", pid, "Last15SecondConnections") + "\n";
        metrics += "LastMinuteConnections: " + getAttributeValue("http-sender", pid, "LastMinuteConnections") + "\n";
        metrics += "LastSecondRequests: " + getAttributeValue("http-sender", pid, "LastSecondRequests") + "\n";
        metrics += "Last15SecondRequests: " + getAttributeValue("http-sender", pid, "Last15SecondRequests") + "\n";
        metrics += "LastMinuteRequests: " + getAttributeValue("http-sender", pid, "LastMinuteRequests") + "\n";

        metrics += "\n";
        metrics += "Https Sender Metrics\n";
        metrics += "==============\n";
        metrics += "Active Connections: " + getAttributeValue("https-sender", pid, "ActiveConnections") + "\n";
        metrics += "LastSecondConnections: " + getAttributeValue("https-sender", pid, "LastSecondConnections") + "\n";
        metrics += "Last5SecondConnections: " + getAttributeValue("https-sender", pid, "Last5SecondConnections") + "\n";
        metrics += "Last15SecondConnections: " + getAttributeValue("https-sender", pid, "Last15SecondConnections") + "\n";
        metrics += "LastMinuteConnections: " + getAttributeValue("https-sender", pid, "LastMinuteConnections") + "\n";
        metrics += "LastSecondRequests: " + getAttributeValue("https-sender", pid, "LastSecondRequests") + "\n";
        metrics += "Last15SecondRequests: " + getAttributeValue("https-sender", pid, "Last15SecondRequests") + "\n";
        metrics += "LastMinuteRequests: " + getAttributeValue("https-sender", pid, "LastMinuteRequests") + "\n";
        metrics += "\n";

        metrics += "Http Listener RequestMap\n";
        metrics += "==============\n";
        metrics += JMXDataRetriever.getAttributeValue("http-listener", pid, "RequestSizesMap");
        metrics += "\n";

        metrics += "Http Listener ResponseMap\n";
        metrics += "==============\n";
        metrics += JMXDataRetriever.getAttributeValue("http-listener", pid, "ResponseSizesMap");
        metrics += "\n";

        metrics += "Https Listener RequestMap\n";
        metrics += "==============\n";
        metrics += JMXDataRetriever.getAttributeValue("https-listener", pid, "RequestSizesMap");
        metrics += "\n";

        metrics += "Https Listener ResponseMap\n";
        metrics += "==============\n";
        metrics += JMXDataRetriever.getAttributeValue("https-listener", pid, "ResponseSizesMap");
        metrics += "\n";

        metrics += "Http Sender RequestMap\n";
        metrics += "==============\n";
        metrics += JMXDataRetriever.getAttributeValue("http-sender", pid, "RequestSizesMap");
        metrics += "\n";

        metrics += "Http Sender ResponseMap\n";
        metrics += "==============\n";
        metrics += JMXDataRetriever.getAttributeValue("http-sender", pid, "ResponseSizesMap");
        metrics += "\n";

        metrics += "Https Sender RequestMap\n";
        metrics += "==============\n";
        metrics += JMXDataRetriever.getAttributeValue("https-sender", pid, "RequestSizesMap");
        metrics += "\n";

        metrics += "Https Sender ResponseMap\n";
        metrics += "==============\n";
        metrics += JMXDataRetriever.getAttributeValue("https-sender", pid, "ResponseSizesMap");
        metrics += "\n";

        return metrics;
    }
}
