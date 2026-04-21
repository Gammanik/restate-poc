#!/bin/bash
# Quick test to manually verify Temporal workflow works

echo "Creating application in los-service..."
RESULT=$(curl -s -X POST http://localhost:8000/api/applications \
  -H 'Content-Type: application/json' \
  -d '{
    "productId": "personal_loan",
    "userDetails": {
      "emiratesId": "784-1234-5678901-0",
      "name": "Test User",
      "dateOfBirth": "1990-01-01",
      "address": "Dubai",
      "incomeClaimed": 15000
    },
    "loanAmount": 50000
  }')

echo "Result: $RESULT"
APP_ID=$(echo $RESULT | jq -r '.applicationId')
echo "Application ID: $APP_ID"

echo ""
echo "Check Temporal UI: http://localhost:8088"
echo "Check application status: curl http://localhost:8000/api/applications/$APP_ID"
