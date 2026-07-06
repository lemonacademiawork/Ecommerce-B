@echo off
cd /d "c:\Users\manis\OneDrive\Desktop\LemonAcademy\E-Commerce\backend"
mvn test-compile --no-transfer-progress > test_compile_output.txt 2>&1
echo Exit code: %ERRORLEVEL%
type test_compile_output.txt | findstr /i "error build success failure"
