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

    echo -e "${GREEN}Submitting application: $product for $name${NC}"

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
        echo "Scenario: Personal Loan (all stages)"
        submit "personal_loan" "Ahmed Al-Mansoori" 50000
        echo -e "\n${GREEN}UI: http://localhost:9070${NC}"
        ;;

    auto-loan)
        echo "Scenario: Auto Loan (skip Open Banking)"
        submit "auto_loan" "Fatima Hassan" 75000
        echo -e "\n${GREEN}UI: http://localhost:9070${NC}"
        ;;

    mortgage)
        echo "Scenario: Mortgage (all stages, large amount)"
        submit "mortgage" "Mohammed Ali" 500000
        echo -e "\n${GREEN}UI: http://localhost:9070${NC}"
        ;;

    *)
        echo "Usage: ./demo-script.sh [command]"
        echo ""
        echo "Commands:"
        echo "  happy-path  - Personal loan (all stages)"
        echo "  auto-loan   - Auto loan (skip Open Banking)"
        echo "  mortgage    - Mortgage (all stages, large amount)"
        echo ""
        echo "Start:"
        echo "  docker compose up -d"
        echo "  ./gradlew :httpbin-proxy:bootRun &"
        echo "  ./gradlew :restate-impl:run &"
        echo "  ./gradlew :los-service:bootRun &"
        echo ""
        echo "UI: http://localhost:9070 (Restate) | http://localhost:8088 (Temporal)"
        ;;
esac
