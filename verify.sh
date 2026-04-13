#!/bin/bash
# Verification script for Online Java Compiler project

echo "================================"
echo "Online Java Compiler - Setup Verification"
echo "================================"
echo ""

# Check directory structure
echo "✓ Checking project structure..."
if [ -d "backend" ] && [ -d "frontend" ]; then
    echo "  ✓ Backend and Frontend directories exist"
else
    echo "  ✗ Missing backend or frontend directory"
    exit 1
fi

# Check backend files
echo ""
echo "✓ Checking backend..."
if [ -f "backend/pom.xml" ]; then
    echo "  ✓ pom.xml found"
else
    echo "  ✗ pom.xml not found"
    exit 1
fi

if [ -f "backend/src/main/java/com/altimetrik/interview/controller/CompilerController.java" ]; then
    echo "  ✓ CompilerController found"
else
    echo "  ✗ CompilerController not found"
    exit 1
fi

if [ -f "backend/src/main/java/com/altimetrik/interview/service/JavaCompilerService.java" ]; then
    echo "  ✓ JavaCompilerService found"
else
    echo "  ✗ JavaCompilerService not found"
    exit 1
fi

# Check frontend files
echo ""
echo "✓ Checking frontend..."
if [ -f "frontend/package.json" ]; then
    echo "  ✓ package.json found"
else
    echo "  ✗ package.json not found"
    exit 1
fi

if [ -f "frontend/src/components/Editor.tsx" ]; then
    echo "  ✓ Editor component found"
else
    echo "  ✗ Editor component not found"
    exit 1
fi

if [ -f "frontend/src/services/api.ts" ]; then
    echo "  ✓ API service found"
else
    echo "  ✗ API service not found"
    exit 1
fi

# Check Docker files
echo ""
echo "✓ Checking Docker configuration..."
if [ -f "docker-compose.yml" ]; then
    echo "  ✓ docker-compose.yml found"
else
    echo "  ✗ docker-compose.yml not found"
    exit 1
fi

if [ -f "backend/Dockerfile" ]; then
    echo "  ✓ Backend Dockerfile found"
else
    echo "  ✗ Backend Dockerfile not found"
    exit 1
fi

if [ -f "frontend/Dockerfile" ]; then
    echo "  ✓ Frontend Dockerfile found"
else
    echo "  ✗ Frontend Dockerfile not found"
    exit 1
fi

# Check documentation
echo ""
echo "✓ Checking documentation..."
for doc in README.md QUICKSTART.md ARCHITECTURE.md; do
    if [ -f "$doc" ]; then
        echo "  ✓ $doc found"
    else
        echo "  ✗ $doc not found"
        exit 1
    fi
done

echo ""
echo "================================"
echo "✓ All checks passed!"
echo "================================"
echo ""
echo "Next steps:"
echo "1. Docker start: docker-compose up --build"
echo "2. Open: http://localhost:3000"
echo "3. See README.md for more options"
echo ""
