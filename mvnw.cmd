@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Batch Script for MoveInSync Settlement Engine
@REM ----------------------------------------------------------------------------

@IF "%DEBUG%" == "" @ECHO OFF
@SETLOCAL

@REM Auto-detect JAVA_HOME if not already configured in environment
IF "%JAVA_HOME%" == "" (
    IF EXIST "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot" (
        SET "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
    )
)

IF "%JAVA_HOME%" == "" (
    FOR /F "tokens=* USEBACKQ" %%F IN (`powershell -NoProfile -Command "$env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path','User'); $j = (Get-Command java -ErrorAction SilentlyContinue); if ($j) { Split-Path (Split-Path $j.Source) }"`) DO (
        SET "JAVA_HOME=%%F"
    )
)

SET "WRAPPER_DIR=%~dp0.mvn\wrapper"
SET "MAVEN_VERSION=3.9.9"
SET "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%"
SET "MAVEN_BIN=%MAVEN_HOME%\bin\mvn.cmd"

IF NOT EXIST "%MAVEN_BIN%" (
    ECHO [INFO] Apache Maven %MAVEN_VERSION% not found locally.
    ECHO [INFO] Downloading and bootstrapping Apache Maven %MAVEN_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "$url = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip';" ^
        "$destDir = [System.IO.Path]::GetDirectoryName('%MAVEN_HOME%');" ^
        "New-Item -ItemType Directory -Force -Path $destDir | Out-Null;" ^
        "$zipFile = Join-Path $destDir 'maven.zip';" ^
        "[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12;" ^
        "Write-Host '[INFO] Downloading from' $url '...';" ^
        "(New-Object System.Net.WebClient).DownloadFile($url, $zipFile);" ^
        "Write-Host '[INFO] Extracting to' $destDir '...';" ^
        "Expand-Archive -Path $zipFile -DestinationPath $destDir -Force;" ^
        "Remove-Item -Path $zipFile -Force;" ^
        "Write-Host '[INFO] Maven bootstrap complete.'"
)

IF NOT EXIST "%MAVEN_BIN%" (
    ECHO [ERROR] Failed to locate or bootstrap Maven executable at %MAVEN_BIN%
    EXIT /B 1
)

"%MAVEN_BIN%" %*
EXIT /B %ERRORLEVEL%
