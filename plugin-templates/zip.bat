@echo off
REM Usage: zip.bat <folder> [output]
set SRC=%1
set OUT=%2
if "%SRC%"=="" (echo Usage: zip.bat ^<folder^> [output] & exit /b 1)
if "%OUT%"=="" set OUT=%SRC%
if not exist "%SRC%" (echo Folder not found: %SRC% & exit /b 1)
cd "%SRC%"
jar -cMf "../%OUT%.egg" .
cd ..
echo Created: %OUT%.egg
