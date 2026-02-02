/*
 *  Copyright (c) 2023, WSO2 LLC (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.wso2.micro.integrator.utils.utils;

public final class ServerConstants {

    private ServerConstants() {
    }
    public static final String CARBON_HOME = "carbon.home";
    public static final String CARBON_CONFIG_DIR_PATH = "carbon.config.dir.path";

    //=============================================
    //          HTTP Constants
    //=============================================
    public static class HTTPConstants {

        /**
         * @deprecated
         */
        public static final String HTTP_METHOD_GET = "GET";
        /**
         * @deprecated
         */
        public static final String HTTP_METHOD_POST = "POST";
    }

}