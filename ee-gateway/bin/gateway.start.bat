@REM
@REM Copyright 2007-2017, Kaazing Corporation. All rights reserved.
@REM

@echo off

if "%OS%" == "Windows_NT" SETLOCAL EnableDelayedExpansion
rem ---------------------------------------------------------------------------
rem Windows start script for Kaazing Gateway
rem ---------------------------------------------------------------------------

cd %~dp0


rem A temporary variable for the location of the gateway installation,
rem to allow determining the conf and lib subdirectories (assumed to 
rem be siblings to this script's 'bin' directory).
set GW_HOME=..
set GATEWAY_HOME=%~dp0\..
set SCRIPTED_ARGS=%GATEWAY_HOME%\lib\enterprise-args.csv

rem --------------------------- Broker specific start ---------------------------

for /f %%i in ('dir /b %GW_HOME%\brokers\qpid-java-broker-*') do set QPID_DIR=%%i
for /f %%i in ('dir /b %GW_HOME%\brokers\apache-activemq-*') do set ACTIVEMQ_DIR=%%i

set ACTIVEMQ_HOME=%GATEWAY_HOME%\brokers\%ACTIVEMQ_DIR%
set QPID_HOME=%GATEWAY_HOME%\brokers\%QPID_DIR%

set ARG0=%0
set ARGS=

:loop
if not "%1" == "" (
    if "%1" == "--broker" set USE_BROKER=1
    if "%1" == "/broker" set USE_BROKER=1

    if defined USE_BROKER (
        set BROKER=%2
        shift
    ) else (
        set ARGS=%ARGS% %1
    )
    shift
    goto :loop
)

if defined USE_BROKER (
    if /i "%BROKER%" == "AMQP" (
        echo "Starting Apache QPid AMQP 0-9-1 broker"
        start "" "%QPID_HOME%\bin\qpid-server.bat -os"
    ) else (
        if /i "%BROKER%" == "JMS" (
            echo "Starting Apache ActiveMQ JMS broker"
            start "" "%ACTIVEMQ_HOME%\bin\win64\activemq.bat"
        ) else (
            echo "%ARG0%: '%BROKER%' broker not supported. Valid values for the 'broker' command-line parameter are 'AMQP' and 'JMS'."
            exit /b 2
        )
    )
)

rem ---------------------------- Broker specific end ----------------------------

rem You can define various Java system properties by setting the value
rem of the GATEWAY_OPTS environment variable before calling this script.
rem The script itself should not be changed. For example, the setting
rem below sets the Java maximum memory to 512MB.
if "%GATEWAY_OPTS%" == "" (
    set GATEWAY_OPTS=-Xmx512m
)

rem You can opt into using early access features by setting the value 
rem of the GATEWAY_FEATURES environment variable to a comma separated 
rem list of features to enable before calling this script.
rem The script itself should not be changed.
set FEATURE_OPTS= 
if not "%GATEWAY_FEATURES%" == "" (
   echo Enabling early access features: %GATEWAY_FEATURES%
   set GATEWAY_FEATURES=%GATEWAY_FEATURES: =%
   set FEATURE_OPTS=-Dfeature.!GATEWAY_FEATURES:,= -Dfeature.!
)

rem Checking if Java installed
java -version 1>nul 2>nul || (
    echo Java is not installed. Cannot start the Gateway.
    exit /b 2
)

rem Obtains Java version: format is (Java 8 = 18, Java 7 = 17)
for /f eol^=J^ tokens^=2-5^ delims^=.-_^" %%j in ('java -fullversion 2^>^&1') do set "jver=%%j%%k"

rem Checks Java version
if %jver% LSS 18 (
    echo Java 8 or higher must be installed to start the Gateway.
    exit /b 1
)

rem Create the classpath.

java %FEATURE_OPTS% %GATEWAY_OPTS% -Djava.library.path="%JAVA_LIBRARY_PATH%" -XX:+HeapDumpOnOutOfMemoryError -cp "%GW_HOME%\lib\*;%GW_HOME%\lib\ext\*" org.kaazing.gateway.server.WindowsMain %ARGS%
