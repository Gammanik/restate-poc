#!/bin/bash
set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Restate vs Temporal Credit Check POC ===${NC}\n"

# Function to submit application
submit_application() {
    local product=$1
    local name=$2
    local amount=$3

    echo -e "${GREEN}Submitting $product application for $name${NC}"

    curl -s -X POST http://localhost:8000/api/credit-check \
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

# Check if services are running
check_services() {
    echo -e "${BLUE}Checking services...${NC}"

    if curl -sf http://localhost:9070 > /dev/null; then
        echo -e "${GREEN}✓ Restate is running${NC}"
    else
        echo -e "${RED}✗ Restate is not running. Start with: docker compose up restate${NC}"
        exit 1
    fi

    if curl -sf http://localhost:8090/config/status > /dev/null; then
        echo -e "${GREEN}✓ httpbin-proxy is running${NC}"
    else
        echo -e "${RED}✗ httpbin-proxy is not running. Start with: ./gradlew :httpbin-proxy:bootRun${NC}"
        exit 1
    fi
}

case "${1:-help}" in
    happy-path)
        echo -e "${BLUE}Scenario 1: Happy Path (Personal Loan)${NC}"
        echo "This will submit a personal loan application and auto-approve it"
        echo "Expected: Application goes through all 5 stages and gets approved"
        echo ""
        submit_application "personal_loan" "Ahmed Al-Mansoori" 50000
        echo -e "\n${GREEN}Check Restate UI: http://localhost:9070${NC}"
        ;;

    auto-loan)
        echo -e "${BLUE}Scenario 2: Auto Loan (No Open Banking)${NC}"
        echo "Auto loans skip Open Banking stage per configuration"
        echo ""
        submit_application "auto_loan" "Fatima Hassan" 75000
        echo -e "\n${GREEN}Check Restate UI: http://localhost:9070${NC}"
        ;;

    mortgage)
        echo -e "${BLUE}Scenario 3: Mortgage (All Stages)${NC}"
        echo "Mortgage applications go through all stages with longer timeouts"
        echo ""
        submit_application "mortgage" "Mohammed Ali" 500000
        echo -e "\n${GREEN}Check Restate UI: http://localhost:9070${NC}"
        ;;

    check)
        check_services
        ;;

    *)
        echo "Usage: ./demo-script.sh [command]"
        echo ""
        echo "Commands:"
        echo "  happy-path  - Submit personal loan (auto-approve scenario)"
        echo "  auto-loan   - Submit auto loan (skips Open Banking)"
        echo "  mortgage    - Submit mortgage (all stages, long timeouts)"
        echo "  check       - Check if services are running"
        echo ""
        echo "Setup:"
        echo "  1. Start infrastructure: docker compose up -d restate postgres temporal temporal-ui"
        echo "  2. Start httpbin-proxy: ./gradlew :httpbin-proxy:bootRun &"
        echo "  3. Start Restate service: ./gradlew :restate-impl:run &"
        echo "  4. Start LOS service: WORKFLOW_ENGINE=restate ./gradlew :los-service:bootRun &"
        echo ""
        echo "UI URLs:"
        echo "  - Restate: http://localhost:9070"
        echo "  - Temporal: http://localhost:8088"
        ;;
esac
