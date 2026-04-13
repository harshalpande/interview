@echo off
REM Verification script for Online Java Compiler project (Windows)

echo ================================
echo Online Java Compiler - Setup Verification
echo ================================
echo.

REM Check directory structure
echo Checking project structure...
if exist "backend\" if exist "frontend\" (
    echo   OK - Backend and Frontend directories exist
) else (
    echo   ERROR - Missing backend or frontend directory
    exit /b 1
)

REM Check backend files
echo.
echo Checking backend...
if exist "backend\pom.xml" (
    echo   OK - pom.xml found
) else (
    echo   ERROR - pom.xml not found
    exit /b 1
)

if exist "backend\src\main\java\com\altimetrik\interview\controller\CompilerController.java" (
    echo   OK - CompilerController found
) else (
    echo   ERROR - CompilerController not found
    exit /b 1
)

if exist "backend\src\main\java\com\altimetrik\interview\service\JavaCompilerService.java" (
    echo   OK - JavaCompilerService found
) else (
    echo   ERROR - JavaCompilerService not found
    exit /b 1
)

REM Check frontend files
echo.
echo Checking frontend...
if exist "frontend\package.json" (
    echo   OK - package.json found
) else (
    echo   ERROR - package.json not found
    exit /b 1
)

if exist "frontend\src\components\Editor.tsx" (
    echo   OK - Editor component found
) else (
    echo   ERROR - Editor component not found
    exit /b 1
)

if exist "frontend\src\services\api.ts" (
    echo   OK - API service found
) else (
    echo   ERROR - API service not found
    exit /b 1
)

REM Check Docker files
echo.
echo Checking Docker configuration...
if exist "docker-compose.yml" (
    echo   OK - docker-compose.yml found
) else (
    echo   ERROR - docker-compose.yml not found
    exit /b 1
)

if exist "backend\Dockerfile" (
    echo   OK - Backend Dockerfile found
) else (
    echo   ERROR - Backend Dockerfile not found
    exit /b 1
)

if exist "frontend\Dockerfile" (
    echo   OK - Frontend Dockerfile found
) else (
    echo   ERROR - Frontend Dockerfile not found
    exit /b 1
)

REM Check documentation
echo.
echo Checking documentation...
for %%F in (README.md QUICKSTART.md ARCHITECTURE.md) do (
    if exist "%%F" (
        echo   OK - %%F found
    ) else (
        echo   ERROR - %%F not found
        exit /b 1
    )
)

echo.
echo ================================
echo All checks passed!
echo ================================
echo.
echo Next steps:
echo 1. Docker: docker-compose up --build
echo 2. Open: http://localhost:3000
echo 3. See README.md for more options
echo.
pause
