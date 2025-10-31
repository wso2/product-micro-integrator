@ECHO OFF
SETLOCAL ENABLEDELAYEDEXPANSION

:: ----------------------------------------------------------------------------
::  Copyright 2025 WSO2, LLC. http://www.wso2.org
::
::  Licensed under the Apache License, Version 2.0 (the "License");
::  you may not use this file except in compliance with the License.
::  You may obtain a copy of the License at
::
::      http://www.apache.org/licenses/LICENSE-2.0
::
::  Unless required by applicable law or agreed to in writing, software
::  distributed under the License is distributed on an "AS IS" BASIS,
::  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
::  See the License for the specific language governing permissions and
::  limitations under the License.
:: ----------------------------------------------------------------------------

:: ======= FIPS (to install in default mode) =======
set "BC_FIPS_VERSION=2.0.1"
set "BCPKIX_FIPS_VERSION=2.0.10"
set "BCTLS_FIPS_VERSION=2.0.19"
set "BCUTIL_FIPS_VERSION=2.0.2"

:: Official SHA-1 checksums (Maven Central)
set "EXPECTED_BC_FIPS_CHECKSUM=67cf4d43d0e86b8a493cfdfe266c226ff7ffc410"
set "EXPECTED_BCPKIX_FIPS_CHECKSUM=4cc5a8607f3dd6cd3fb0ee5abc2e7a068adf2cf1"
set "EXPECTED_BCTLS_FIPS_CHECKSUM=9cc33650ede63bc1a8281ed5c8e1da314d50bc76"
set "EXPECTED_BCUTIL_FIPS_CHECKSUM=c11996822d9d0f831b340bf4ea4d9d3e87a8e9de"

:: ======= Legacy (non-FIPS) to restore on DISABLE if no backup exists =======
set "LEGACY_BCPROV_ARTIFACT=bcprov-jdk18on"
set "LEGACY_BCPROV_VERSION=1.78.1"

set "PRG=%~f0"
set "PRGDIR=%~dp0"

:: UPDATED: Force recalculation of CARBON_HOME to avoid system-wide variable conflicts
:: This assumes the script is in the 'bin' directory, and CARBON_HOME is one level up.
PUSHD "%PRGDIR%.."
set "CARBON_HOME=%CD%"
POPD

set "LOCAL_DIR="
set "MAVEN_BASE=https://repo1.maven.org/maven2"

:: Parse options first
:ParseOpts
IF "%~1"=="" GOTO EndParseOpts
IF /I "%~1"=="-f" (
    set "LOCAL_DIR=%~2"
    shift
    shift
    GOTO ParseOpts
)
IF /I "%~1"=="-m" (
    set "MAVEN_BASE=%~2"
    shift
    shift
    GOTO ParseOpts
)
:: Assume first non-option is the main argument
set "ARGUMENT=%~1"
shift
GOTO ParseOpts
:EndParseOpts

IF "%ARGUMENT%"=="" (
    IF "%~1" NEQ "" set "ARGUMENT=%~1"
)

:: Directories
:: !!! VERIFY THIS PATH IS CORRECT on your system !!!
set "BACKUP_DIR=%USERPROFILE%\.wso2-mi\backup"
set "RUNTIME_LIB_DIR=%CARBON_HOME%\wso2\lib"
set "PLUGINS_DIR=%CARBON_HOME%\wso2\components\plugins"

mkdir "%BACKUP_DIR%" 2>NUL
mkdir "%RUNTIME_LIB_DIR%" 2>NUL
mkdir "%PLUGINS_DIR%" 2>NUL

:: Check for dependencies
curl --version >NUL 2>NUL
IF ERRORLEVEL 1 (
    echo ERROR: curl.exe not found or not in PATH.
    echo Please install curl or add it to your system's PATH.
    GOTO :EndScript
)
certutil /? >NUL 2>NUL
IF ERRORLEVEL 1 (
    echo ERROR: certutil.exe not found or not in PATH.
    echo This tool is required for checksum verification.
    GOTO :EndScript
)

:: ---------------------------- Main ----------------------------

IF /I "%ARGUMENT%"=="DISABLE" GOTO :DisableFIPS
IF /I "%ARGUMENT%"=="VERIFY" GOTO :VerifyFIPS
GOTO :InstallFIPS


:InstallFIPS
echo Step 1/4: Backing up ^& removing legacy (non-FIPS) BC jars from plugins...
CALL :backup_and_remove_all_legacy_bc
IF ERRORLEVEL 1 GOTO :EndScript

echo Step 1a/4: Backing up ^& removing specific legacy BC jars from %RUNTIME_LIB_DIR%...
CALL :backup_and_remove_specific_legacy_in_runtime
IF ERRORLEVEL 1 GOTO :EndScript

echo Step 2/4: Staging FIPS jars into backup folder (cache) if missing...
CALL :stage_fips_in_backup "bc-fips" "%BC_FIPS_VERSION%" "%EXPECTED_BC_FIPS_CHECKSUM%"
IF ERRORLEVEL 1 GOTO :EndScript
CALL :stage_fips_in_backup "bcpkix-fips" "%BCPKIX_FIPS_VERSION%" "%EXPECTED_BCPKIX_FIPS_CHECKSUM%"
IF ERRORLEVEL 1 GOTO :EndScript
CALL :stage_fips_in_backup "bctls-fips" "%BCTLS_FIPS_VERSION%" "%EXPECTED_BCTLS_FIPS_CHECKSUM%"
IF ERRORLEVEL 1 GOTO :EndScript
CALL :stage_fips_in_backup "bcutil-fips" "%BCUTIL_FIPS_VERSION%" "%EXPECTED_BCUTIL_FIPS_CHECKSUM%"
IF ERRORLEVEL 1 GOTO :EndScript

echo Step 3/4: Installing FIPS jars from backup into %RUNTIME_LIB_DIR%...
CALL :install_from_backup_to_runtime "bc-fips" "%BC_FIPS_VERSION%"
IF ERRORLEVEL 1 GOTO :EndScript
CALL :install_from_backup_to_runtime "bcpkix-fips" "%BCPKIX_FIPS_VERSION%"
IF ERRORLEVEL 1 GOTO :EndScript
CALL :install_from_backup_to_runtime "bctls-fips" "%BCTLS_FIPS_VERSION%"
IF ERRORLEVEL 1 GOTO :EndScript
CALL :install_from_backup_to_runtime "bcutil-fips" "%BCUTIL_FIPS_VERSION%"
IF ERRORLEVEL 1 GOTO :EndScript

echo Step 4/4: Done. Please restart the server.
GOTO :EndScript


:DisableFIPS
echo Disabling FIPS jars...
CALL :remove_fips_artifact "bc-fips"
CALL :remove_fips_artifact "bcpkix-fips"
CALL :remove_fips_artifact "bctls-fips"
CALL :remove_fips_artifact "bcutil-fips"

echo Restoring legacy BC jars to %RUNTIME_LIB_DIR%...
CALL :restore_legacy_to_dir "%RUNTIME_LIB_DIR%"
IF ERRORLEVEL 1 GOTO :EndScript

echo Restoring WSO2-orbit plugin jar to %PLUGINS_DIR%...
CALL :restore_orbit_bcprov_plugin
IF ERRORLEVEL 1 GOTO :EndScript

echo Done. Please restart the server.
GOTO :EndScript


:VerifyFIPS
set "VERIFY_FAILED=false"

:: 0) Fail if legacy bcprov-jdk18on* in plugins
IF EXIST "%PLUGINS_DIR%\bcprov-jdk18on*.jar" (
    echo Verification failed: Found legacy bcprov-jdk18on* in %PLUGINS_DIR%.
    echo Please run this script without arguments to install FIPS jars.
    GOTO :EndScript
)

:: 1) Fail if any legacy libs exist in wso2/lib
set "LEGACY_LIB_JARS=bcprov-jdk18on-%LEGACY_BCPROV_VERSION%.jar bcpkix-jdk18on-%LEGACY_BCPROV_VERSION%.jar bctls-jdk18on-%LEGACY_BCPROV_VERSION%.jar bcutil-jdk18on-%LEGACY_BCPROV_VERSION%.jar"
FOR %%J IN (%LEGACY_LIB_JARS%) DO (
    IF EXIST "%RUNTIME_LIB_DIR%\%%J" (
        echo Verification failed: Found legacy %%J in %RUNTIME_LIB_DIR%.
        echo Please run this script without arguments to install FIPS jars.
        set "VERIFY_FAILED=true"
    )
)
IF "%VERIFY_FAILED%"=="true" GOTO :EndScript

:: 2) Check presence of required FIPS jars (and verify checksum)
CALL :_verify_fips_jar "bc-fips" "%BC_FIPS_VERSION%" "%EXPECTED_BC_FIPS_CHECKSUM%"
CALL :_verify_fips_jar "bcpkix-fips" "%BCPKIX_FIPS_VERSION%" "%EXPECTED_BCPKIX_FIPS_CHECKSUM%"
CALL :_verify_fips_jar "bctls-fips" "%BCTLS_FIPS_VERSION%" "%EXPECTED_BCTLS_FIPS_CHECKSUM%"
CALL :_verify_fips_jar "bcutil-fips" "%BCUTIL_FIPS_VERSION%" "%EXPECTED_BCUTIL_FIPS_CHECKSUM%"

IF "%VERIFY_FAILED%"=="true" (
    echo Verification failed: One or more FIPS jars missing or invalid.
    GOTO :EndScript
)

echo Verification successful: All FIPS dependencies present; no non-FIPS jars detected.
GOTO :EndScript

:: ============================================================================
:: SUBROUTINES
:: ============================================================================
GOTO :EOF

:: ---------- Verify Checksum (Windows implementation) ----------
:verify_checksum
    set "FILE=%~1"
    set "EXPECTED=%~2"
    IF "%EXPECTED%"=="" (
        echo Skipping checksum for %~nx1
        EXIT /B 0
    )

    set "GOT="
    FOR /F "skip=1 tokens=*" %%G IN ('certutil -hashfile "%FILE%" SHA1') DO (
        set "GOT=%%G"
        GOTO :gotHash
    )
    :gotHash
    IF NOT DEFINED GOT (
        echo ERROR: Could not get hash for %~nx1 using certutil.
        EXIT /B 1
    )

    set "GOT=%GOT: =%"

    IF /I "%GOT%" NEQ "%EXPECTED%" (
        echo Checksum mismatch for %~nx1
        EXIT /B 1
    )

    echo Checksum OK for %~nx1
    EXIT /B 0

:: ---------- Backup & remove all legacy BC from plugins ----------
:backup_and_remove_all_legacy_bc
    IF EXIST "%PLUGINS_DIR%\" (
        CALL :backup_and_remove_bc_jars_in_dir "%PLUGINS_DIR%"
    )

    IF EXIST "%BACKUP_DIR%\*.jar" (
        echo Backup present in %BACKUP_DIR%
    ) ELSE (
        echo No legacy BC jars were found to back up.
    )
    EXIT /B 0

:: ---------- Backup & remove any NON-FIPS BC jars in a dir ----------
:backup_and_remove_bc_jars_in_dir
    set "DIR=%~1"

    :: Define the specific jar to look for
    set "ORBIT_PLUGIN_JAR=bcprov-jdk18on_%LEGACY_BCPROV_VERSION%.wso2v1.jar"
    set "FILE_PATH=%DIR%\%ORBIT_PLUGIN_JAR%"

    mkdir "%BACKUP_DIR%" 2>NUL

    :: Check if that specific file exists
    IF EXIST "%FILE_PATH%" (
        echo Backing up legacy BC jar from %DIR% -> %BACKUP_DIR%
        echo   Moved %FILE_PATH%

        move /Y "%FILE_PATH%" "%BACKUP_DIR%\%ORBIT_PLUGIN_JAR%" >NUL
        IF ERRORLEVEL 1 (
            echo ERROR: Access is denied. Failed to move %ORBIT_PLUGIN_JAR%.
            echo Please ensure the server is stopped and run as administrator.
            EXIT /B 1
        )
    ) ELSE (
        echo No legacy BC jars in %DIR%
    )

    EXIT /B 0

:: ---------- Backup & remove specific legacy BC jars from RUNTIME_LIB_DIR ----------
:backup_and_remove_specific_legacy_in_runtime
    mkdir "%BACKUP_DIR%" 2>NUL

    set "LEGACY_RUNTIME_JARS=bcprov-jdk18on-%LEGACY_BCPROV_VERSION%.jar bctls-jdk18on-%LEGACY_BCPROV_VERSION%.jar bcpkix-jdk18on-%LEGACY_BCPROV_VERSION%.jar bcutil-jdk18on-%LEGACY_BCPROV_VERSION%.jar"

    set "FOUND_ANY=false"
    FOR %%J IN (%LEGACY_RUNTIME_JARS%) DO (
        IF EXIST "%RUNTIME_LIB_DIR%\%%J" (
            set "FOUND_ANY=true"
            echo Found legacy runtime jar: %%J
            move /Y "%RUNTIME_LIB_DIR%\%%J" "%BACKUP_DIR%\%%J" >NUL
            echo   Moved %%J -> %BACKUP_DIR%
        )
    )

    IF "%FOUND_ANY%"=="false" (
        echo No specified legacy BC jars found in %RUNTIME_LIB_DIR%
    ) ELSE (
        echo Legacy BC jars removed from %RUNTIME_LIB_DIR%
    )
    EXIT /B 0

:: ---------- Stage FIPS jar into BACKUP_DIR (cache), then install to runtime ----------
:stage_fips_in_backup
    set "ARTIFACT=%~1"
    set "VERSION=%~2"
    set "EXPECTED=%~3"

    set "STAGED_JARNAME=%ARTIFACT%-%VERSION%.jar"
    set "STAGED=%BACKUP_DIR%\%STAGED_JARNAME%"

    IF DEFINED LOCAL_DIR (
        IF EXIST "%LOCAL_DIR%\%STAGED_JARNAME%" (
            echo Using local %LOCAL_DIR%\%STAGED_JARNAME% -> %STAGED%
            copy /Y "%LOCAL_DIR%\%STAGED_JARNAME%" "%STAGED%" >NUL
            GOTO :VerifyStaged
        )
    )

    IF NOT EXIST "%STAGED%" (
        echo Staging %ARTIFACT%-%VERSION% into backup folder: %STAGED%
        CALL :download_to_path "%ARTIFACT%" "%VERSION%" "%STAGED%"
        IF ERRORLEVEL 1 EXIT /B 1
    ) ELSE (
        echo Found staged in backup: %STAGED%
    )

    :VerifyStaged
    CALL :verify_checksum "%STAGED%" "%EXPECTED%"
    IF ERRORLEVEL 1 (
        del "%STAGED%" 2>NUL
        echo Removed bad staged jar: %STAGED%
        echo Re-downloading...
        CALL :download_to_path "%ARTIFACT%" "%VERSION%" "%STAGED%"
        IF ERRORLEVEL 1 EXIT /B 1
        CALL :verify_checksum "%STAGED%" "%EXPECTED%"
        IF ERRORLEVEL 1 (
            echo Checksum failed again. Deleting corrupt file.
            del "%STAGED%" 2>NUL
            EXIT /B 1
        )
    )
    EXIT /B 0

:: ---------- Download helper ----------
:download_to_path
    set "ARTIFACT=%~1"
    set "VERSION=%~2"
    set "DEST=%~3"
    set "FILENAME="
    FOR %%F IN ("%DEST%") DO set "FILENAME=%%~nxF"

    set "URL=%MAVEN_BASE%/org/bouncycastle/%ARTIFACT%/%VERSION%/%FILENAME%"
    echo Downloading %URL%
    curl -fL -o "%DEST%" "%URL%"
    IF ERRORLEVEL 1 (
        echo ERROR: curl download failed for %URL%
        EXIT /B 1
    )
    EXIT /B 0

:: ---------- Install FIPS jar from backup to runtime ----------
:install_from_backup_to_runtime
    set "ARTIFACT=%~1"
    set "VERSION=%~2"

    set "STAGED_JARNAME=%ARTIFACT%-%VERSION%.jar"
    set "STAGED=%BACKUP_DIR%\%STAGED_JARNAME%"

    IF NOT EXIST "%STAGED%" (
        echo ERROR: staged jar missing: %STAGED%
        EXIT /B 1
    )

    set "DEST=%RUNTIME_LIB_DIR%\%STAGED_JARNAME%"
    copy /Y "%STAGED%" "%DEST%" >NUL
    IF ERRORLEVEL 1 (
        echo ERROR: Access is denied.
        echo Please run this script as an Administrator.
        EXIT /B 1
    )
    echo Installed %STAGED_JARNAME% -> %RUNTIME_LIB_DIR%
    EXIT /B 0

:: ---------- Remove all FIPS jars from runtime ----------
:remove_fips_artifact
    set "ARTIFACT=%~1"
    del "%RUNTIME_LIB_DIR%\%ARTIFACT%-*.jar" 2>NUL
    echo Removed %ARTIFACT% jars from %RUNTIME_LIB_DIR%
    EXIT /B 0

:: ---------- Restore legacy (non-FIPS) BC jars ONLY to a given dir ----------
:restore_legacy_to_dir
    set "TARGET_DIR=%~1"
    set "VER=%LEGACY_BCPROV_VERSION%"

    mkdir "%BACKUP_DIR%" 2>NUL
    mkdir "%TARGET_DIR%" 2>NUL

    set "LEGACY_JARS=bcprov-jdk18on-%VER%.jar bcpkix-jdk18on-%VER%.jar bctls-jdk18on-%VER%.jar bcutil-jdk18on-%VER%.jar"

    FOR %%J IN (%LEGACY_JARS%) DO (
        CALL :_restore_legacy_jar "%%J" "%TARGET_DIR%"
        IF ERRORLEVEL 1 EXIT /B 1
    )
    EXIT /B 0

:: ----- Helper for restore_legacy_to_dir -----
:_restore_legacy_jar
    set "JAR=%~1"
    set "TARGET_DIR=%~2"
    set "TARGET_PATH=%TARGET_DIR%\%JAR%"
    set "VER=%LEGACY_BCPROV_VERSION%"

    :: Prefer restore from backup
    IF EXIST "%BACKUP_DIR%\%JAR%" (
        copy /Y "%BACKUP_DIR%\%JAR%" "%TARGET_PATH%" >NUL
        IF ERRORLEVEL 1 (
            echo ERROR: Access is denied copying from backup.
            echo Please run this script as an Administrator.
            EXIT /B 1
        )
        echo Restored %JAR% -> %TARGET_DIR%
        EXIT /B 0
    )

    :: Otherwise, download from Maven Central
    set "BASE="
    FOR /F "tokens=1 delims=-" %%B IN ("%JAR%") DO set "BASE=%%B"

    set "URL=%MAVEN_BASE%/org/bouncycastle/%BASE%-jdk18on/%VER%/%JAR%"
    set "STAGED=%BACKUP_DIR%\%JAR%"

    echo Downloading %URL%
    curl -fL -o "%STAGED%" "%URL%"
    IF ERRORLEVEL 1 (
        echo Download failed for %JAR%.
        EXIT /B 1
    )

    copy /Y "%STAGED%" "%TARGET_PATH%" >NUL
    IF ERRORLEVEL 1 (
        echo ERROR: Access is denied copying downloaded file.
        echo Please run this script as an Administrator.
        EXIT /B 1
    )
    echo Installed %JAR% -> %TARGET_DIR%
    EXIT /B 0

:: ---------- Restore WSO2-orbit bcprov plugin jar to components/plugins ----------
:restore_orbit_bcprov_plugin
    set "VER=%LEGACY_BCPROV_VERSION%"
    set "PLUGIN_JAR=bcprov-jdk18on_%VER%.wso2v1.jar"
    set "TARGET_DIR=%PLUGINS_DIR%"
    set "TARGET_PATH=%TARGET_DIR%\%PLUGIN_JAR%"
    set "BACKUP_PATH=%BACKUP_DIR%\%PLUGIN_JAR%"

    mkdir "%BACKUP_DIR%" 2>NUL
    mkdir "%TARGET_DIR%" 2>NUL

    :: Check if it exists in backup
    IF EXIST "%BACKUP_PATH%" (
        echo Restoring %PLUGIN_JAR% from backup...
        move /Y "%BACKUP_PATH%" "%TARGET_PATH%" >NUL
        IF ERRORLEVEL 1 (
            echo ERROR: Access is denied moving plugin from backup.
            echo Please run this script as an Administrator.
            EXIT /B 1
        )
        echo Moved %PLUGIN_JAR% -> %TARGET_DIR%
        EXIT /B 0
    )

    :: If not found in backup or target, it means we don't have it to restore.
    echo WARNING: %PLUGIN_JAR% not found in backup or target; skipping plugin restore.
    EXIT /B 0

:: ---------- Helper for VERIFY ----------
:_verify_fips_jar
    set "ARTIFACT=%~1"
    set "VERSION=%~2"
    set "EXPECTED=%~3"

    set "JAR=%ARTIFACT%-%VERSION%.jar"
    set "PATH=%RUNTIME_LIB_DIR%\%JAR%"

    IF NOT EXIST "%PATH%" (
        echo Verification failed: Missing %JAR% in %RUNTIME_LIB_DIR%
        set "VERIFY_FAILED=true"
        EXIT /B 0
    )

    CALL :verify_checksum "%PATH%" "%EXPECTED%"
    IF ERRORLEVEL 1 (
        echo Verification failed: Checksum mismatch for %JAR% in %RUNTIME_LIB_DIR%
        set "VERIFY_FAILED=true"
        EXIT /B 0
    )
    EXIT /B 0


:: ============================================================================
:: END OF SCRIPT
:: ============================================================================
:EndScript
ENDLOCAL
EXIT /B %ERRORLEVEL%
