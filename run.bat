@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

if not exist backend\out mkdir backend\out
if exist backend\sources.txt del backend\sources.txt

for /r backend\src %%f in (*.java) do (
    set "p=%%f"
    set "p=!p:\=/!"
    echo "!p!">> backend\sources.txt
)

echo Compiling...
javac -encoding UTF-8 -cp "backend\lib\*" -d backend\out @backend\sources.txt
if errorlevel 1 (
    echo.
    echo Compilation failed. Read the errors above.
    pause
    exit /b 1
)

echo Starting server...
java -cp "backend\out;backend\lib\*" Main
pause
