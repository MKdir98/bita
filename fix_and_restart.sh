#!/bin/bash

# Script to fix the Flyway migration issue and restart the application

echo "🔧 Fixing Flyway migration..."

# Database credentials (adjust if needed)
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5433}
DB_NAME=${DB_NAME:-bita_db}
DB_USERNAME=${DB_USERNAME:-bita}
DB_PASSWORD=${DB_PASSWORD:-bita123}

# Clean up failed migration
echo "📝 Cleaning up failed migration from Flyway history..."
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME -c "DELETE FROM flyway_schema_history WHERE version = '1';" 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Flyway history cleaned successfully!"
else
    echo "⚠️  Could not clean Flyway history (table might not exist yet, which is OK)"
fi

echo ""
echo "🚀 Now restart your Spring Boot application."
echo "   The migration will run automatically on startup."
echo ""
echo "   Run: mvn spring-boot:run"
echo "   Or restart your IDE run configuration"
