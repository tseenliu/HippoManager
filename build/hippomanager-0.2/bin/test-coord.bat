@REM hippomanager launcher script
@REM
@REM Environment:
@REM JAVA_HOME - location of a JDK home dir (optional if java on path)
@REM CFG_OPTS  - JVM options (optional)
@REM Configuration:
@REM HIPPOMANAGER_config.txt found in the HIPPOMANAGER_HOME.
@setlocal enabledelayedexpansion

@echo off

if "%HIPPOMANAGER_HOME%"=="" set "HIPPOMANAGER_HOME=%~dp0\\.."

set "APP_LIB_DIR=%HIPPOMANAGER_HOME%\lib\"

rem Detect if we were double clicked, although theoretically A user could
rem manually run cmd /c
for %%x in (!cmdcmdline!) do if %%~x==/c set DOUBLECLICKED=1

rem FIRST we load the config file of extra options.
set "CFG_FILE=%HIPPOMANAGER_HOME%\HIPPOMANAGER_config.txt"
set CFG_OPTS=
if exist "%CFG_FILE%" (
  FOR /F "tokens=* eol=# usebackq delims=" %%i IN ("%CFG_FILE%") DO (
    set DO_NOT_REUSE_ME=%%i
    rem ZOMG (Part #2) WE use !! here to delay the expansion of
    rem CFG_OPTS, otherwise it remains "" for this loop.
    set CFG_OPTS=!CFG_OPTS! !DO_NOT_REUSE_ME!
  )
)

rem We use the value of the JAVACMD environment variable if defined
set _JAVACMD=%JAVACMD%

if "%_JAVACMD%"=="" (
  if not "%JAVA_HOME%"=="" (
    if exist "%JAVA_HOME%\bin\java.exe" set "_JAVACMD=%JAVA_HOME%\bin\java.exe"
  )
)

if "%_JAVACMD%"=="" set _JAVACMD=java

rem Detect if this java is ok to use.
for /F %%j in ('"%_JAVACMD%" -version  2^>^&1') do (
  if %%~j==java set JAVAINSTALLED=1
  if %%~j==openjdk set JAVAINSTALLED=1
)

rem BAT has no logical or, so we do it OLD SCHOOL! Oppan Redmond Style
set JAVAOK=true
if not defined JAVAINSTALLED set JAVAOK=false

if "%JAVAOK%"=="false" (
  echo.
  echo A Java JDK is not installed or can't be found.
  if not "%JAVA_HOME%"=="" (
    echo JAVA_HOME = "%JAVA_HOME%"
  )
  echo.
  echo Please go to
  echo   http://www.oracle.com/technetwork/java/javase/downloads/index.html
  echo and download a valid Java JDK and install before running hippomanager.
  echo.
  echo If you think this message is in error, please check
  echo your environment variables to see if "java.exe" and "javac.exe" are
  echo available via JAVA_HOME or PATH.
  echo.
  if defined DOUBLECLICKED pause
  exit /B 1
)


rem We use the value of the JAVA_OPTS environment variable if defined, rather than the config.
set _JAVA_OPTS=%JAVA_OPTS%
if "!_JAVA_OPTS!"=="" set _JAVA_OPTS=!CFG_OPTS!

rem We keep in _JAVA_PARAMS all -J-prefixed and -D-prefixed arguments
rem "-J" is stripped, "-D" is left as is, and everything is appended to JAVA_OPTS
set _JAVA_PARAMS=
set _APP_ARGS=

:param_loop
call set _PARAM1=%%1
set "_TEST_PARAM=%~1"

if ["!_PARAM1!"]==[""] goto param_afterloop


rem ignore arguments that do not start with '-'
if "%_TEST_PARAM:~0,1%"=="-" goto param_java_check
set _APP_ARGS=!_APP_ARGS! !_PARAM1!
shift
goto param_loop

:param_java_check
if "!_TEST_PARAM:~0,2!"=="-J" (
  rem strip -J prefix
  set _JAVA_PARAMS=!_JAVA_PARAMS! !_TEST_PARAM:~2!
  shift
  goto param_loop
)

if "!_TEST_PARAM:~0,2!"=="-D" (
  rem test if this was double-quoted property "-Dprop=42"
  for /F "delims== tokens=1,*" %%G in ("!_TEST_PARAM!") DO (
    if not ["%%H"] == [""] (
      set _JAVA_PARAMS=!_JAVA_PARAMS! !_PARAM1!
    ) else if [%2] neq [] (
      rem it was a normal property: -Dprop=42 or -Drop="42"
      call set _PARAM1=%%1=%%2
      set _JAVA_PARAMS=!_JAVA_PARAMS! !_PARAM1!
      shift
    )
  )
) else (
  if "!_TEST_PARAM!"=="-main" (
    call set CUSTOM_MAIN_CLASS=%%2
    shift
  ) else (
    set _APP_ARGS=!_APP_ARGS! !_PARAM1!
  )
)
shift
goto param_loop
:param_afterloop

set _JAVA_OPTS=!_JAVA_OPTS! !_JAVA_PARAMS!
:run

set "APP_CLASSPATH=%APP_LIB_DIR%\default.hippomanager-0.2.jar;%APP_LIB_DIR%\org.scala-lang.scala-library-2.11.8.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-actor_2.11-2.5.3.jar;%APP_LIB_DIR%\com.typesafe.config-1.3.1.jar;%APP_LIB_DIR%\org.scala-lang.modules.scala-java8-compat_2.11-0.7.0.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-remote_2.11-2.5.3.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-stream_2.11-2.5.3.jar;%APP_LIB_DIR%\org.reactivestreams.reactive-streams-1.0.0.jar;%APP_LIB_DIR%\com.typesafe.ssl-config-core_2.11-0.2.1.jar;%APP_LIB_DIR%\org.scala-lang.modules.scala-parser-combinators_2.11-1.0.4.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-protobuf_2.11-2.5.3.jar;%APP_LIB_DIR%\io.netty.netty-3.10.6.Final.jar;%APP_LIB_DIR%\io.aeron.aeron-driver-1.2.5.jar;%APP_LIB_DIR%\io.aeron.aeron-client-1.2.5.jar;%APP_LIB_DIR%\org.agrona.agrona-0.9.5.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-persistence_2.11-2.5.3.jar;%APP_LIB_DIR%\org.iq80.leveldb.leveldb-0.7.jar;%APP_LIB_DIR%\org.iq80.leveldb.leveldb-api-0.7.jar;%APP_LIB_DIR%\com.google.guava.guava-16.0.1.jar;%APP_LIB_DIR%\org.fusesource.leveldbjni.leveldbjni-all-1.8.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-http_2.11-10.0.9.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-http-core_2.11-10.0.9.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-parsing_2.11-10.0.9.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-http-spray-json_2.11-10.0.9.jar;%APP_LIB_DIR%\io.spray.spray-json_2.11-1.3.3.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-cluster_2.11-2.5.3.jar;%APP_LIB_DIR%\com.typesafe.akka.akka-distributed-data_2.11-2.5.3.jar;%APP_LIB_DIR%\org.lmdbjava.lmdbjava-0.0.5.jar;%APP_LIB_DIR%\com.github.jnr.jnr-ffi-2.1.2.jar;%APP_LIB_DIR%\com.github.jnr.jffi-1.2.14-native.jar;%APP_LIB_DIR%\com.github.jnr.jffi-1.2.14.jar;%APP_LIB_DIR%\org.ow2.asm.asm-5.0.3.jar;%APP_LIB_DIR%\org.ow2.asm.asm-commons-5.0.3.jar;%APP_LIB_DIR%\org.ow2.asm.asm-tree-5.0.3.jar;%APP_LIB_DIR%\org.ow2.asm.asm-analysis-5.0.3.jar;%APP_LIB_DIR%\org.ow2.asm.asm-util-5.0.3.jar;%APP_LIB_DIR%\com.github.jnr.jnr-x86asm-1.0.2.jar;%APP_LIB_DIR%\com.github.jnr.jnr-constants-0.9.6.jar;%APP_LIB_DIR%\net.cakesolutions.scala-kafka-client_2.11-0.9.0.0.jar;%APP_LIB_DIR%\org.apache.kafka.kafka-clients-0.9.0.1.jar;%APP_LIB_DIR%\org.slf4j.slf4j-api-1.7.21.jar;%APP_LIB_DIR%\org.xerial.snappy.snappy-java-1.1.1.7.jar;%APP_LIB_DIR%\net.jpountz.lz4.lz4-1.2.0.jar;%APP_LIB_DIR%\org.slf4j.log4j-over-slf4j-1.7.21.jar;%APP_LIB_DIR%\net.cakesolutions.scala-kafka-client-akka_2.11-0.9.0.0.jar;%APP_LIB_DIR%\org.scala-lang.scala-reflect-2.11.8.jar"
set "APP_MAIN_CLASS=com.cathay.dtag.hippo.manager.app.test.TestCoord"

if defined CUSTOM_MAIN_CLASS (
    set MAIN_CLASS=!CUSTOM_MAIN_CLASS!
) else (
    set MAIN_CLASS=!APP_MAIN_CLASS!
)

rem Call the application and pass all arguments unchanged.
"%_JAVACMD%" !_JAVA_OPTS! !HIPPOMANAGER_OPTS! -cp "%APP_CLASSPATH%" %MAIN_CLASS% !_APP_ARGS!

@endlocal


:end

exit /B %ERRORLEVEL%
