#!/bin/bash
set -euo pipefail

aws --endpoint-url 'https://kinesis-mock:4567/' --no-verify-ssl kinesis list-streams

KINESIS_STREAM_NAME=test-stream

aws --endpoint-url 'https://kinesis-mock:4567/' --no-verify-ssl kinesis create-stream --stream-name $KINESIS_STREAM_NAME --shard-count 1 --stream-mode-details StreamMode=PROVISIONED

base64_content=$(echo '{"test": "data"}' | base64)

aws --endpoint-url 'https://kinesis-mock:4567/' --no-verify-ssl kinesis put-record \
  --stream-name $KINESIS_STREAM_NAME \
  --data  ${base64_content} --partition-key id

SHARD_ITERATOR=$(aws --endpoint-url 'https://kinesis-mock:4567/' --no-verify-ssl kinesis get-shard-iterator --shard-id shardId-000000000000 --shard-iterator-type TRIM_HORIZON --stream-name $KINESIS_STREAM_NAME --query 'ShardIterator');

aws --endpoint-url 'https://kinesis-mock:4567/' --no-verify-ssl kinesis get-records --shard-iterator $SHARD_ITERATOR

aws --endpoint-url 'https://kinesis-mock:4567/' --no-verify-ssl kinesis delete-stream --stream-name $KINESIS_STREAM_NAME
