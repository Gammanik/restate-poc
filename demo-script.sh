#!/bin/bash
set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== Restate vs Temporal POC ===${NC}\n"

submit() {
    local product=$1
    local name=$2
    local amount=$3

    echo -e "${GREEN}Отправка заявки: $product для $name${NC}"

    curl -s -X POST http://localhost:8000/api/applications \
        -H 'Content-Type: application/json' \
        -d "{
            \"productId\": \"$product\",
            \"userDetails\": {
                \"emiratesId\": \"784-1234-5678901-0\",
                \"name\": \"$name\",
                \"dateOfBirth\": \"1990-01-01\",
                \"address\": \"Dubai, UAE\",
                \"incomeClaimed\": 15000
            },
            \"loanAmount\": $amount
        }" | jq
}

case "${1:-help}" in
    happy-path)
        echo "Сценарий: Personal Loan (все стадии)"
        submit "personal_loan" "Ahmed Al-Mansoori" 50000
        echo -e "\n${GREEN}UI: http://localhost:9070${NC}"
        ;;

    auto-loan)
        echo "Сценарий: Auto Loan (без Open Banking)"
        submit "auto_loan" "Fatima Hassan" 75000
        echo -e "\n${GREEN}UI: http://localhost:9070${NC}"
        ;;

    mortgage)
        echo "Сценарий: Mortgage (большая сумма, все проверки)"
        submit "mortgage" "Mohammed Ali" 500000
        echo -e "\n${GREEN}UI: http://localhost:9070${NC}"
        ;;

    *)
        echo "Использование: ./demo-script.sh [команда]"
        echo ""
        echo "Команды:"
        echo "  happy-path  - Personal loan (все стадии)"
        echo "  auto-loan   - Auto loan (пропуск Open Banking)"
        echo "  mortgage    - Mortgage (все стадии, большая сумма)"
        echo ""
        echo "Старт:"
        echo "  docker compose up -d"
        echo "  ./gradlew :httpbin-proxy:bootRun &"
        echo "  ./gradlew :restate-impl:run &"
        echo "  ./gradlew :los-service:bootRun &"
        echo ""
        echo "UI: http://localhost:9070 (Restate) | http://localhost:8088 (Temporal)"
        ;;
esac
