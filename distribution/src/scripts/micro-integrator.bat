@echo off
REM ---------------------------------------------------------------------------
REM        Copyright 2018 WSO2, Inc. http://www.wso2.org
REM
REM  Licensed under the Apache License, Version 2.0 (the "License");
REM  you may not use this file except in compliance with the License.
REM  You may obtain a copy of the License at
REM
REM      http://www.apache.org/licenses/LICENSE-2.0
REM
REM  Unless required by applicable law or agreed to in writing, software
REM  distributed under the License is distributed on an "AS IS" BASIS,
REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM  See the License for the specific language governing permissions and
REM  limitations under the License.

rem ---------------------------------------------------------------------------
rem Main Script for WSO2 Carbon
rem
rem Environment Variable Prerequisites
rem
rem   CARBON_HOME   Home of CARBON installation. If not set I will  try
rem                   to figure it out.
rem
rem   JAVA_HOME       Must point at your Java Development Kit installation.
rem
rem   JAVA_OPTS       (Optional) Java runtime options used when the commands
rem                   is executed.
rem ---------------------------------------------------------------------------

rem --------- NOTE: This is an edited wso2server.sh script to facilitate
rem spark environment variables for WSO2DAS!
rem ----- if JAVA_HOME is not set we're not happy ------------------------------
:checkJava

if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
goto checkServer

:noJavaHome
echo "You must set the JAVA_HOME variable before running CARBON."
goto end

rem ----- Only set CARBON_HOME if not already set ----------------------------
:checkServer
rem %~sdp0 is expanded pathname of the current script under NT with spaces in the path removed
SET CARBON_HOME=%~sdp0..
SET curDrive=%cd:~0,1%
SET wsasDrive=%CARBON_HOME:~0,1%
if not "%curDrive%" == "%wsasDrive%" %wsasDrive%:

rem find CARBON_HOME if it does not exist due to either an invalid value passed
rem by the user or the %0 problem on Windows 9x
if not exist "%CARBON_HOME%\bin\version.txt" goto noServerHome

set AXIS2_HOME=%CARBON_HOME%
goto updateClasspath

:noServerHome
echo CARBON_HOME is set incorrectly or CARBON could not be located. Please set CARBON_HOME.
goto end

rem ----- update classpath -----------------------------------------------------
:updateClasspath

setlocal EnableDelayedExpansion
cd %CARBON_HOME%
set CARBON_CLASSPATH=
FOR %%C in ("%CARBON_HOME%\bin\*.jar") DO set CARBON_CLASSPATH=!CARBON_CLASSPATH!;".\bin\%%~nC%%~xC"
set CARBON_CLASSPATH="%JAVA_HOME%\lib\tools.jar";%CARBON_CLASSPATH%
set CARBON_CLASSPATH=%CARBON_CLASSPATH%;".\wso2\lib\*"

rem ----- Process the input command -------------------------------------------

rem Slurp the command line arguments. This loop allows for an unlimited number
rem of arguments (up to the command line limit, anyway).

set ENV_FILE_PATH=

:setupArgs
if ""%1""=="""" goto doneStart

:: Check if the argument is --env-file
if "%~1"=="--env-file" goto setEnvPath

if ""%1""==""-run""     goto commandLifecycle
if ""%1""==""--run""    goto commandLifecycle
if ""%1""==""run""      goto commandLifecycle

if ""%1""==""-restart""  goto commandLifecycle
if ""%1""==""--restart"" goto commandLifecycle
if ""%1""==""restart""   goto commandLifecycle

if ""%1""==""debug""    goto commandDebug
if ""%1""==""-debug""   goto commandDebug
if ""%1""==""--debug""  goto commandDebug

if ""%1""==""version""   goto commandVersion
if ""%1""==""-version""  goto commandVersion
if ""%1""==""--version"" goto commandVersion

if ""%1""==""stop""   goto stopServer
if ""%1""==""-stop""  goto stopServer
if ""%1""==""--stop"" goto stopServer

if ""%1""==""car""   goto setCar
if ""%1""==""-car""  goto setCar
if ""%1""==""--car"" goto setCar

shift
goto setupArgs

:setEnvPath
shift
set ENV_FILE_PATH=%1
goto loadEnvFile

:loadEnvFile
if not exist "%ENV_FILE_PATH%" (
    echo Error: File "%ENV_FILE_PATH%" not found.
) else (
    :: Read the .env file line by line and set environment variables
    for /f "usebackq tokens=1,* delims==" %%A in ("%ENV_FILE_PATH%") do (
        :: Ignore lines starting with # or empty lines
        if not "%%A"=="" if not "%%A:~0,1%"=="#" (
            set "key=%%A"
            set "value=%%B"
            :: Trim spaces if needed
            for /f "tokens=* delims= " %%a in ("!key!") do set "key=%%a"
            for /f "tokens=* delims= " %%b in ("!value!") do set "value=%%b"
            :: Export the variable to the environment
            set "!key!=!value!"
        )
    )
    echo Environment variables loaded from "%ENV_FILE_PATH%".
)
goto setupArgs

rem ----- commandVersion -------------------------------------------------------
:commandVersion
shift
type "%CARBON_HOME%\bin\version.txt"
type "%CARBON_HOME%\bin\wso2carbon-version.txt"
goto end

rem ----- commandDebug ---------------------------------------------------------
:commandDebug
shift
set DEBUG_PORT=%1
if "%DEBUG_PORT%"=="" goto noDebugPort
if not "%JAVA_OPTS%"=="" echo Warning !!!. User specified JAVA_OPTS will be ignored, once you give the --debug option.
set JAVA_OPTS=-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=%DEBUG_PORT%
echo Please start the remote debugging client to continue...
goto findJdk

:noDebugPort
echo Please specify the debug port after the --debug option
goto end

:stopServer
set /p processId= < %CARBON_HOME%\wso2carbon.pid
echo Stopping the Micro Integrator Server
taskkill /F /PID %processId%
goto end

rem ----- commandLifecycle -----------------------------------------------------
:commandLifecycle
goto findJdk

:doneStart
if "%OS%"=="Windows_NT" @setlocal
if "%OS%"=="WINNT" @setlocal

:setCar
set CAR_NAME=%2

rem ---------- Handle the SSL Issue with proper JDK version --------------------
rem find the version of the jdk
:findJdk

set CMD=RUN %*

:checkJdk
PATH %PATH%;%JAVA_HOME%\bin\
for /f tokens^=2-5^ delims^=.-_+^" %%j in ('java -fullversion 2^>^&1') do set "JAVA_VERSION=%%j%%k"
if %JAVA_VERSION% LSS 110 goto unknownJdk
goto supportedJdk

:unknownJdk
echo Starting WSO2 MI (in unsupported JDK %JAVA_VERSION%)
echo [ERROR] WSO2 MI is supported only between JDK 11 and JDK 21"
goto end

:supportedJdk
goto runServer

rem ----------------- Execute The Requested Command ----------------------------

:runServer
cd %CARBON_HOME%

rem ------------------ Remove tmp folder on startup -----------------------------
set TMP_DIR=%CARBON_HOME%\tmp
if exist "%TMP_DIR%" (
    cd "%TMP_DIR%"
    del *.* /s /q > nul
    FOR /d %%G in ("*.*") DO rmdir %%G /s /q
    cd ..
)

rem ---------- Add jars to classpath --c _CLASSPATH%

if %JAVA_VERSION% GEQ 110 set CARBON_CLASSPATH=.\wso2\lib\*;%CARBON_CLASSPATH%
if %JAVA_VERSION% GEQ 110 set JAVA_VER_BASED_OPTS=--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.xml/com.sun.org.apache.xerces.internal.util=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens java.rmi/sun.rmi.transport=ALL-UNNAMED --add-opens=java.management/sun.management=ALL-UNNAMED --add-exports=jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED

rem ---------------- Setting default profile for Runtime if not parsed --------------

set profileSet=false

for %%x in (%*) do (
    if "%%x" == "%x:-Dprofile%" (
        set profileSet=true
    )
)

if "%profileSet%" == "false" (
    set profile=-Dprofile=micro-integrator-default
)

set CMD_LINE_ARGS=-Xbootclasspath/a:%CARBON_XBOOTCLASSPATH% -Xms256m -Xmx1024m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath="%CARBON_HOME%\repository\logs\heap-dump.hprof" -Dcom.sun.management.jmxremote -classpath %CARBON_CLASSPATH% %JAVA_OPTS% -Djava.endorsed.dirs=%JAVA_ENDORSED% -DandesConfig=broker.xml -Dcarbon.registry.root=/ -Dcarbon.home="%CARBON_HOME%" -Dlogfiles.home="%CARBON_HOME%\repository\logs" -Dwso2.server.standalone=true -Djava.command="%JAVA_HOME%\bin\java" -Djava.opts="%JAVA_OPTS%" -Djava.io.tmpdir="%CARBON_HOME%\tmp" -Dcatalina.base="%CARBON_HOME%\wso2\lib\tomcat" -Dwso2.carbon.xml="%CARBON_HOME%\conf\carbon.xml" -Dwso2.registry.xml="%CARBON_HOME%\conf\registry.xml" -Dwso2.user.mgt.xml="%CARBON_HOME%\conf\user-mgt.xml" -Dwso2.transports.xml="%CARBON_HOME%\conf\mgt-transports.xml" -Djava.util.logging.config.file="%CARBON_HOME%\conf\log4j.properties" -Dcarbon.config.dir.path="%CARBON_HOME%\conf" -DNonUserCoreMode=true -DNonRegistryMode=true -Dcarbon.logs.path="%CARBON_HOME%\repository\logs" -Dcomponents.repo="%CARBON_HOME%\wso2\components\plugins" -Dcarbon.config.dir.path="%CARBON_HOME%\conf" -Dcarbon.components.dir.path="%CARBON_HOME%\wso2\components" -Dcarbon.dropins.dir.path="%CARBON_HOME%\dropins" -Dcarbon.external.lib.dir.path="%CARBON_HOME%\lib" -Dcarbon.patches.dir.path="%CARBON_HOME%\patches" -Dcarbon.internal.lib.dir.path="%CARBON_HOME%\wso2\lib" -Dconf.location="%CARBON_HOME%\conf" -Dcom.atomikos.icatch.file="%CARBON_HOME%\wso2\lib\transactions.properties" -Dei.extendedURIBasedDispatcher=org.wso2.micro.integrator.core.handlers.IntegratorStatefulHandler -Dcom.atomikos.icatch.hide_init_file_path="true" -Dorg.apache.jasper.compiler.Parser.STRICT_QUOTE_ESCAPING=false -Dorg.apache.jasper.runtime.BodyContentImpl.LIMIT_BUFFER=true -Dcom.sun.jndi.ldap.connect.pool.authentication=simple -Dcom.sun.jndi.ldap.connect.pool.timeout=3000 -Dorg.terracotta.quartz.skipUpdateCheck=true -Dcarbon.classpath=%CARBON_CLASSPATH% -Dfile.encoding=UTF8 -Dlogger.server.name="micro-integrator" -Dqpid.conf="\conf\advanced" -Dproperties.file.path=default -Djavax.xml.xpath.XPathFactory:http://java.sun.com/jaxp/xpath/dom=net.sf.saxon.xpath.XPathFactoryImpl -DavoidConfigHashRead=true -DenableReadinessProbe=true -DenableLivenessProbe=true -DenableManagementApi=true -DskipStartupExtensions=false -Dpolyglot.engine.WarnInterpreterOnly=false -Dautomation.mode.seq.car.name="%CAR_NAME%" -Dlog4j2.contextSelector="org.apache.logging.log4j.core.async.AsyncLoggerContextSelector" -Dorg.ops4j.pax.logging.logReaderEnabled=false -Dorg.ops4j.pax.logging.eventAdminEnabled=false %JAVA_VER_BASED_OPTS% %profile% -Dorg.apache.activemq.SERIALIZABLE_PACKAGES="*" --add-exports=jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED -Djdk.util.zip.disableZip64ExtraFieldValidation=true -Djdk.nio.zipfs.allowDotZipEntry=true

:runJava
rem echo JAVA_HOME environment variable is set to %JAVA_HOME%
rem echo CARBON_HOME environment variable is set to %CARBON_HOME%
"%JAVA_HOME%\bin\java" %CMD_LINE_ARGS% org.wso2.micro.integrator.bootstrap.Bootstrap %CMD%
if "%ERRORLEVEL%"=="121" goto runJava
:end
goto endlocal

:endlocal

:END
