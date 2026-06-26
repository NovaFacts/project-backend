#!/bin/bash

echo "===================================="
echo "NovaFacts - Setup Inicial"
echo "===================================="

# ── Environment check ─────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

if [ ! -f "$ENV_FILE" ]; then
    echo ""
    echo "ERROR: Falta el archivo .env"
    echo ""
    echo "  cp .env.example .env"
    echo "  # Edita .env y completa POSTGRES_PASSWORD y JWT_SECRET"
    echo ""
    exit 1
fi

# Load .env so variables are available to docker compose and mvnw
set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

if [ -z "$JWT_SECRET" ]; then
    echo ""
    echo "ERROR: JWT_SECRET no está definido en .env"
    echo ""
    echo "  Genera un secreto seguro con:"
    echo "    openssl rand -base64 48"
    echo "  y agrégalo a .env como:"
    echo "    JWT_SECRET=<resultado>"
    echo ""
    exit 1
fi

if [ -z "$POSTGRES_PASSWORD" ]; then
    echo ""
    echo "ERROR: POSTGRES_PASSWORD no está definido en .env"
    echo ""
    exit 1
fi
# ─────────────────────────────────────────────────────────────────────────────

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
cd project-frontend/frontend || exit

npm install

cd ..
cd ..

echo ""
echo "[5/6] Compilando backend..."

cd project-backend || exit

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
cd project-backend || exit
sudo docker compose down
sudo docker compose up -d
echo ""
echo "Frontend:"
cd ..
cd project-frontend/frontend || exit

if lsof -Pi :5173 -sTCP:LISTEN -t >/dev/null ; then
    sudo fuser -k 5173/tcp 2>/dev/null || true
fi

npm run dev
echo ""
echo "Setup completado correctamente."
