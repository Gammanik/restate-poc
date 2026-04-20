#!/bin/bash

# Test script for Restate Loan Application POC
# This script tests various loan scenarios

set -e

BASE_URL="http://localhost:8081"
RESTATE_ADMIN_URL="http://localhost:9070"

echo "============================================"
echo "Restate Loan Application - API Test Script"
echo "============================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if services are registered
echo "Checking if Restate services are registered..."
SERVICES=$(curl -s ${RESTATE_ADMIN_URL}/services | grep -o '"name"' | wc -l || true)

if [ "$SERVICES" -lt 4 ]; then
    echo -e "${RED}ERROR: Services not registered with Restate!${NC}"
    echo "Please register services first:"
    echo "  curl -X POST http://localhost:9070/endpoints -H 'Content-Type: application/json' -d '{\"uri\": \"http://localhost:9080\"}'"
    exit 1
else
    echo -e "${GREEN}✓ Services registered${NC}"
fi

echo ""
echo "============================================"
echo "Test 1: High-quality applicant (should be APPROVED)"
echo "============================================"
curl -X POST ${BASE_URL}/api/checkCredit \
  -H "Content-Type: application/json" \
  -d '{
    "applicantName": "Alice Johnson",
    "amount": 50000,
    "income": 400000
  }' | jq '.'

echo ""
echo ""
echo "============================================"
echo "Test 2: Low-quality applicant (should be REJECTED)"
echo "============================================"
curl -X POST ${BASE_URL}/api/checkCredit \
  -H "Content-Type: application/json" \
  -d '{
    "applicantName": "Bob Smith",
    "amount": 100000,
    "income": 30000
  }' | jq '.'

echo ""
echo ""
echo "============================================"
echo "Test 3: Borderline applicant (should be MANUAL_REVIEW)"
echo "============================================"
curl -X POST ${BASE_URL}/api/checkCredit \
  -H "Content-Type: application/json" \
  -d '{
    "applicantName": "Charlie Brown",
    "amount": 75000,
    "income": 160000
  }' | jq '.'

echo ""
echo ""
echo "============================================"
echo "Test 4: Edge case - exactly at threshold"
echo "============================================"
curl -X POST ${BASE_URL}/api/checkCredit \
  -H "Content-Type: application/json" \
  -d '{
    "applicantName": "Diana Prince",
    "amount": 100000,
    "income": 500000
  }' | jq '.'

echo ""
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}All tests completed!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Show recent invocations
echo "Recent workflow invocations:"
curl -s ${RESTATE_ADMIN_URL}/invocations | jq '.invocations[] | {id: .id, target: .target, status: .status}' | head -20

echo ""
echo "View all invocations in Restate UI: ${RESTATE_ADMIN_URL}"
