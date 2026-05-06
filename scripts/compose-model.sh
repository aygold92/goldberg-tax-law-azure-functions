#!/bin/bash
set -e

RESOURCE_GROUP=$(grep RESOURCE_GROUP scripts/config.env | cut -d '=' -f2)
DOCUMENT_SERVICE_NAME=$(grep DOCUMENT_SERVICE_NAME scripts/config.env | cut -d '=' -f2)
MODEL_ID=$(jq -r '.modelId' scripts/extractor-model-config.json)
SETTINGS_FILE="common.local.settings.json"
API_KEY=$(az cognitiveservices account keys list --name "$DOCUMENT_SERVICE_NAME" --resource-group "$RESOURCE_GROUP" --query key1 -o tsv)
POLL_INTERVAL=5

echo "Composing model: $MODEL_ID"

# Submit the compose request; capture headers separately to extract Operation-Location
HTTP_STATUS=$(curl -s -w "%{http_code}" \
  -o /tmp/compose-response.json \
  -D /tmp/compose-headers.txt \
  -X POST \
  "https://$DOCUMENT_SERVICE_NAME.cognitiveservices.azure.com/documentintelligence/documentModels:compose?api-version=2024-11-30" \
  -H "Ocp-Apim-Subscription-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d @scripts/extractor-model-config.json)

echo "HTTP Status: $HTTP_STATUS"

if [[ ! "$HTTP_STATUS" =~ ^2 ]]; then
  echo "Compose request failed:" >&2
  cat /tmp/compose-response.json >&2
  exit 1
fi

OPERATION_URL=$(grep -i "^operation-location:" /tmp/compose-headers.txt | tr -d '\r' | awk '{print $2}')
if [[ -z "$OPERATION_URL" ]]; then
  echo "No Operation-Location header found in response." >&2
  exit 1
fi

echo "Polling for completion: $OPERATION_URL"

# Poll until succeeded or failed
while true; do
  STATUS_JSON=$(curl -s \
    -H "Ocp-Apim-Subscription-Key: $API_KEY" \
    "$OPERATION_URL")

  STATUS=$(echo "$STATUS_JSON" | jq -r '.status')
  echo "  status: $STATUS"

  if [[ "$STATUS" == "succeeded" ]]; then
    sed -i '' "s/\"DocumentIntelligence.ExtractorModel\": \".*\"/\"DocumentIntelligence.ExtractorModel\": \"$MODEL_ID\"/" "$SETTINGS_FILE"
    echo "Done. Updated $SETTINGS_FILE: DocumentIntelligence.ExtractorModel = $MODEL_ID"
    break
  elif [[ "$STATUS" == "failed" ]]; then
    echo "Compose failed:" >&2
    echo "$STATUS_JSON" | jq '.error' >&2
    exit 1
  fi

  sleep $POLL_INTERVAL
done