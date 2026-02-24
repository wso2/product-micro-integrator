/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.diagnostics.trafficanalyzer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is responsible for calculating the Simple Moving Average of a given data set.
 */
public class SimpleMovingAverage {

    private final Logger log = LogManager.getLogger(SimpleMovingAverage.class);

    private final int windowSize;
    private double ma; // Exponential Moving Average

    private double currentThreshold;
    private final List<Integer> tempList;

    public SimpleMovingAverage(int windowSize) {

        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be > 0");
        }

        this.windowSize = windowSize;
        this.ma = Double.NaN;
        this.currentThreshold = Double.NaN;
        this.tempList = new ArrayList<>();
    }

    public double update(int newDataPoint) {

        if (log.isDebugEnabled()) {
            log.debug("Updating Simple Moving Average with new data point: " + newDataPoint);
        }
        addDataToList(newDataPoint);
        Double ma = findAverage(tempList);
        setMa(ma);
        updateThresholdStd();
        return ma;
    }

    private double findAverage(List<Integer> list) {

        int sum = 0;
        for (int i : list) {
            sum += i;
        }
        double average = (double) sum / list.size();
        if (log.isDebugEnabled()) {
            log.debug("Simple Moving Average: " + average);
        }
        return average;
    }

    public void addDataToList(int newData) {

        if (tempList.size() < windowSize) {
            tempList.add(newData);
        } else {
            tempList.remove(0);
            tempList.add(newData);
        }
    }

    // Update the threshold using the standard deviation
    private void updateThresholdStd() {
        if (tempList.isEmpty() || Double.isNaN(ma)) {
            currentThreshold = Double.NaN;
            return;
        }

        double sumSquaredDiff = 0.0;
        double k = 2;
        for (int value : tempList) {
            double diff = value - ma;
            sumSquaredDiff += diff * diff;
        }
        double std = Math.sqrt(sumSquaredDiff / tempList.size());
        currentThreshold = ma + k * std;
        if (log.isDebugEnabled()) {
            log.debug("Updating Moving Average Threshold: " + currentThreshold);
        }
    }

    public List<Integer> getTempList() {

        return tempList;
    }


    public void setMa(double ma) {

        this.ma = ma;
    }

    public double getCurrentThreshold() {

        return currentThreshold;
    }
}
