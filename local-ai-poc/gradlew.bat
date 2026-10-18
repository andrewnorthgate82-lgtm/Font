@echo off
set DIR=%~dp0
if not "%JAVA_HOME%"=="" set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if "%JAVA_EXE%"=="" set JAVA_EXE=java.exe
"%JAVA_EXE%" -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
