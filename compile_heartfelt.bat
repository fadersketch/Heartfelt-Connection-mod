@echo off
rem Compile heartfelt_connection: run fix_classpath.py + gen_compile.py first, then this script.
rem javac lookup order: JAVA_HOME -> Minecraft runtime -> PATH
cd /d "%~dp0"
set "JAVAC=javac"
if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC=%JAVA_HOME%\bin\javac.exe"
)
"%JAVAC%" @compile_heartfelt.txt 2>&1
echo EXITCODE=%ERRORLEVEL%
