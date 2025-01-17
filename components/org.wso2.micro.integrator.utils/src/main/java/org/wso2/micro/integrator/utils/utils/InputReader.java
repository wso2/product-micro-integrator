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

import java.io.PrintWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * A script for getting user input for any text and password
 */
public class InputReader {
    private InputReader() {
        //disable external instantiation
    }

    private static final int BYTE_ARRAY_SIZE = 256;

    public static String readInput() throws IOException {
        byte b [] = new byte [BYTE_ARRAY_SIZE];
        int i = System.in.read(b);
        String msg = "";
        if (i != -1) {
            msg = new String(b).substring(0, i - 1).trim();
        }
        return msg;
    }

    public static String readPassword(String prompt) throws IOException {
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            String password = null;
            while (password == null || password.length() == 0) {
                password = new PasswordPrompt(prompt, out).getPassword(in);
            }
            return password;
        }
    }
}
