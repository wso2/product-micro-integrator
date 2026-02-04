/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.securevault;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.securevault.secret.AbstractSecretCallbackHandler;
import org.wso2.securevault.secret.SingleSecretCallback;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * This is the default secret call back handler class, shipped with carbon distribution which extends the
 * AbstractSecretCallbackHandler class in synapse secure vault
 *
 * This class retrieves the password of key store and private key as command line input when carbon
 * server is start-up.
 *
 * When server is started as a daemon, this class searches for a simple test file called "password" and read
 * the password from there and delete it.
 *
 * By default, this class assumes that private and key store passwords are same. By passing the
 * key.password=true as a system property in server start-up, default behaviour can be changed
 */
public class DefaultSecretCallbackHandler extends AbstractSecretCallbackHandler {

    private static Log log = LogFactory.getLog(DefaultSecretCallbackHandler.class);

    private static final String KEY_PASSWORD = "key.password";
    private static final String CARBON_HOME = "carbon.home";
    private static final String PERSIST_PASSWORD = "persist.password";
    private static String keyStorePassWord;
    private static String privateKeyPassWord;
    private static File keyDataFile;

    public void handleSingleSecretCallback(SingleSecretCallback singleSecretCallback) {

        if(keyStorePassWord == null && privateKeyPassWord == null){

            String textFileName;
            String textFileName_tmp;
            String textFilePersist;
            boolean sameKeyAndKeyStorePass = true;
            boolean persistPassword = false;
            String passwords[];

            String carbonHome = System.getProperty(CARBON_HOME);

            String osName = System.getProperty("os.name");
            if(osName.toLowerCase().indexOf("win") == -1){
                textFileName = "password";
                textFileName_tmp = "password-tmp";
                textFilePersist = "password-persist";
            } else {
                textFileName = "password.txt";
                textFileName_tmp = "password-tmp.txt";
                textFilePersist = "password-persist.txt";
            }

            String keyPassword = System.getProperty(KEY_PASSWORD);
            if(keyPassword != null && keyPassword.trim().equals("true")){
                sameKeyAndKeyStorePass = false;
            }
            String persistPasswordProperty = System.getProperty(PERSIST_PASSWORD);
            if(persistPasswordProperty != null && persistPasswordProperty.trim().equals("true")){
                persistPassword = true;
            }

            keyDataFile = new File(carbonHome + File.separator + textFileName);

            if(keyDataFile.exists()){
                passwords = readPassword(keyDataFile, sameKeyAndKeyStorePass);
                keyStorePassWord = passwords[0];
                if(sameKeyAndKeyStorePass){
                    privateKeyPassWord = keyStorePassWord;
                } else {
                    privateKeyPassWord = passwords[1];
                }
                if(!persistPassword){
                    if(!renameConfigFile(textFileName_tmp)){
                        handleException("Error renaming Password config File");
                    }
                }
            } else {
                keyDataFile = new File(carbonHome + File.separator + textFileName_tmp);
                if(keyDataFile.exists()){
                    passwords = readPassword(keyDataFile, sameKeyAndKeyStorePass);
                    keyStorePassWord = passwords[0];
                    if(sameKeyAndKeyStorePass){
                        privateKeyPassWord = keyStorePassWord;
                    } else {
                        privateKeyPassWord = passwords[1];
                    }
                    if(!persistPassword){
                        if (!deleteConfigFile()) {
                            handleException("Error deleting Password config File");
                        }
                    }
                } else {
                    keyDataFile =  new File(carbonHome + File.separator + textFilePersist);
                    if(keyDataFile.exists()){
                        passwords = readPassword(keyDataFile, sameKeyAndKeyStorePass);
                        keyStorePassWord = passwords[0];
                        if(sameKeyAndKeyStorePass){
                            privateKeyPassWord = keyStorePassWord;
                        } else {
                            privateKeyPassWord = passwords[1];
                        }
                    } else{
                        Console console;
                        char[] password;
                        if(sameKeyAndKeyStorePass){
                            if ((console = System.console()) != null && (password = console.readPassword("[%s]",
                                    "Enter KeyStore and Private Key Password :")) != null) {
                                keyStorePassWord = String.valueOf(password);
                                privateKeyPassWord= keyStorePassWord;
                            }
                        } else {
                            if ((console = System.console()) != null &&
                                    (password = console.readPassword("[%s]",
                                            "Enter KeyStore Password :")) != null) {
                                keyStorePassWord = String.valueOf(password);
                            }
                            if ((console = System.console()) != null &&
                                    (password = console.readPassword("[%s]",
                                            "Enter Private Key Password : ")) != null) {
                                privateKeyPassWord = String.valueOf(password);
                            }
                        }
                    }
                }
            }
        }
        if(singleSecretCallback.getId().equals("identity.key.password")){
            singleSecretCallback.setSecret(privateKeyPassWord);
        } else {
            singleSecretCallback.setSecret(keyStorePassWord);
        }
    }


    private String[] readPassword(File file, boolean sameKeyAndKeyStorePass){

        String stringLines[] = new String[2];
        FileInputStream inputStream = null;
        BufferedReader bufferedReader = null;
        try{
            inputStream = new FileInputStream(file);
            bufferedReader= new BufferedReader(new InputStreamReader(inputStream));
            if(sameKeyAndKeyStorePass){
                stringLines[0] = bufferedReader.readLine();
            } else {
                stringLines[0] = bufferedReader.readLine();
                stringLines[1] = bufferedReader.readLine();
            }
        }catch (Exception e){
            handleException("Error reading password from text file ", e);
        } finally {
            try {
                if(bufferedReader != null){
                    bufferedReader.close();
                }
                if(inputStream != null){
                    inputStream.close();
                }
            } catch (IOException e) {
                log.warn("Error closing output stream of text file");
            }
        }
        return stringLines;
    }

    private boolean deleteConfigFile(){
        FileOutputStream outputStream = null;
        BufferedWriter bufferedWriter = null;
        try{
            outputStream = new FileOutputStream(keyDataFile);
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
            bufferedWriter.write("!@#$%^&*()SDFGHJZXCVBNM!@#$%^&*");
        }catch (Exception e){
            handleException("Error writing values to text file ", e);
        } finally {
            try {
                if(bufferedWriter != null){
                    bufferedWriter.close();
                }
                if(outputStream != null){
                    outputStream.close();
                }
            } catch (IOException e) {
                log.warn("Error closing output stream of text file");
            }
        }

        return keyDataFile.exists() && keyDataFile.delete();
    }

    private boolean renameConfigFile(String fileName){
        if (keyDataFile.exists()){
            File newConfigFile = new File(System.getProperty(CARBON_HOME) + File.separator + fileName);
            return keyDataFile.renameTo(newConfigFile);
        }
        return false;
    }

    private static void handleException(String msg, Exception e) {
        log.error(msg, e);
        throw new SecretCallbackHandlerException(msg, e);
    }

    private static void handleException(String msg) {
        log.error(msg);
        throw new SecretCallbackHandlerException(msg);
    }
}

