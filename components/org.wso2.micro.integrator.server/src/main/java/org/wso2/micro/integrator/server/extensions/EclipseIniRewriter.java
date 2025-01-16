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

package org.wso2.micro.integrator.server.extensions;

import org.wso2.micro.integrator.server.LauncherConstants;
import org.wso2.micro.integrator.server.MicroIntegratorLaunchExtension;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A bug in p2 libs(https://bugs.eclipse.org/bugs/show_bug.cgi?id=344153) prevent us from using bundle pooling with
 * -roaming option. A absolute path get written to eclipse.ini/null.ini file found under p2 profile. Here we are rewriting
 * the absolute path at the server startup.
 */
public class EclipseIniRewriter implements MicroIntegratorLaunchExtension {
    private static final Logger logger = Logger.getLogger(EclipseIniRewriter.class.getName());
    private static final String CARBON_OSGI_DIR_LOCATION;

    static {
        String componentsPath = System.getProperty(LauncherConstants.CARBON_COMPONENTS_DIR_PATH);
        if (componentsPath == null) {
            CARBON_OSGI_DIR_LOCATION = Paths.get(System.getProperty("carbon.home"), "repository", "components")
                    .toString();
        } else {
            CARBON_OSGI_DIR_LOCATION = componentsPath;
        }
    }

    public void perform() {
        File eclipseIni = null;
        String profileName = System.getProperty(LauncherConstants.PROFILE, LauncherConstants.DEFAULT_CARBON_PROFILE);
        String profileLocation = CARBON_OSGI_DIR_LOCATION + File.separator + profileName;
        try {
            // when accessing canonical path, the method actually checks the file in the FS.
            profileLocation = new File(profileLocation).getCanonicalPath();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "The directory : " + profileName + "does not exist..", e);
        }
        // getting the file null.ini
        eclipseIni = new File(profileLocation + File.separator + "null.ini");
        if (eclipseIni.exists()) {
            logger.log(Level.FINE, "processing the null.ini file found in " + profileLocation);

            rewriteFile(eclipseIni, profileLocation);
            return;
        }
        // null.ini does not exist. trying with eclipse.ini
        eclipseIni = new File(profileLocation + File.separator + "eclipse.ini");
        if (eclipseIni.exists()) {
            logger.log(Level.FINE, "processing the eclispe.ini file found in " + profileLocation);
            rewriteFile(eclipseIni, profileLocation);
            return;
        }

    }

    //  used delete/create method to rewrite file
    private void rewriteFile(File file, String profileLocation) {
        file.delete();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.write("-install\n");
            pw.write(profileLocation);
            pw.flush();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error while writing to file " + file.getName(), e);
        }
    }
}
