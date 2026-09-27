@echo off
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot
set PATH=%JAVA_HOME%\bin;c:\Users\garag\OneDrive\Documents\Opencraft4\run\natives;c:\Users\garag\OneDrive\Documents\Opencraft4\natives\ultralight-legacy\bin;%PATH%
cd /d c:\Users\garag\OneDrive\Documents\Opencraft4
if not exist run mkdir run
echo %DATE% %TIME% LAUNCH >> run\quit.log
C:\Users\garag\apache-maven-3.9.9\bin\mvn.cmd -q process-resources compile exec:java > launch-external.log 2>&1
set EXITCODE=%ERRORLEVEL%
echo %DATE% %TIME% PROCESS_EXIT code=%EXITCODE% >> run\quit.log
echo [Opencraft] process exit code=%EXITCODE% >> launch-external.log
exit /b %EXITCODE%
