#!/bin/bash

echo "===================================="
echo "NovaFacts - Setup Inicial"
echo "===================================="

echo ""
echo "[1/6] Verificando Java..."

if ! command -v java &> /dev/null
then
    echo "ERROR: Java no está instalado."
    exit 1
fi

java --version

echo ""
echo "[2/6] Verificando Node.js..."

if ! command -v node &> /dev/null
then
    echo "ERROR: Node.js no está instalado."
    exit 1
fi

node --version

echo ""
echo "[3/6] Verificando npm..."

npm --version

echo ""
echo "[4/6] Instalando dependencias del frontend..."

cd ..
cd project-frontend || exit

npm install

cd ..

echo ""
echo "[5/6] Compilando backend..."

cd backend || exit

chmod +x mvnw

./mvnw clean compile

echo ""
echo "Ejecutando pruebas..."

./mvnw test

cd ..

echo ""
echo "[6/6] Preparación finalizada."
echo ""
echo "Backend:"
echo "   cd backend"
echo "   ./mvnw spring-boot:run"
echo ""
echo "Frontend:"
echo "   cd project-frontend"
echo "   npm run dev"
echo ""
echo "Setup completado correctamente."
