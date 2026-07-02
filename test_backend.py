import sys
import os
import json

# Add backend/src to path
sys.path.append(os.path.join(os.path.dirname(__file__), 'backend', 'src'))

# Mock boto3 and urllib
class MockTable:
    def __init__(self):
        self.items = {}
        
    def put_item(self, Item):
        print("[MOCK DYNAMODB] put_item:", Item)
        key = (Item['userId'], Item['date'])
        self.items[key] = Item
        
    def query(self, KeyConditionExpression):
        print("[MOCK DYNAMODB] query Partition Key")
        return {"Items": list(self.items.values())}

class MockDynamoResource:
    def Table(self, name):
        return MockTable()

class MockSSM:
    def get_parameter(self, Name, WithDecryption):
        return {"Parameter": {"Value": "mock-api-key"}}

import boto3
boto3.resource = lambda service: MockDynamoResource()
boto3.client = lambda service: MockSSM()

# Mock urllib.request
import urllib.request
class MockUrlResponse:
    def read(self):
        return json.dumps({
            "candidates": [
                {
                    "content": {
                        "parts": [
                            {"text": "This is a simulated workout and rest recommendation based on your steps, heart rate, and sleep quality."}
                        ]
                    }
                }
            ]
        }).encode('utf-8')
        
    def decode(self, codec):
        return self
        
    def __enter__(self):
        return self
        
    def __exit__(self, exc_type, exc_val, exc_tb):
        pass

urllib.request.urlopen = lambda req: MockUrlResponse()

# Import index handler
import index

print("--- Testing POST /sync ---")
sync_event = {
    "rawPath": "/sync",
    "requestContext": {"http": {"method": "POST"}},
    "body": json.dumps({
        "userId": "harsh_test",
        "date": "2026-06-30",
        "steps": 8450,
        "avgHeartRate": 72,
        "latestHeartRate": 68,
        "sleepMinutes": 480,
        "timestamp": 1782349823
    })
}
res = index.handler(sync_event, None)
print("Response:", json.dumps(res, indent=2))

print("\n--- Testing GET /metrics ---")
get_event = {
    "rawPath": "/metrics",
    "requestContext": {"http": {"method": "GET"}},
    "queryStringParameters": {"userId": "harsh_test"}
}
res = index.handler(get_event, None)
print("Response:", json.dumps(res, indent=2))

print("\n--- Testing POST /advice ---")
advice_event = {
    "rawPath": "/advice",
    "requestContext": {"http": {"method": "POST"}},
    "body": json.dumps({
        "steps": 8450,
        "avgHeartRate": 72,
        "latestHeartRate": 68,
        "sleepMinutes": 480,
        "topic": "workout"
    })
}
res = index.handler(advice_event, None)
print("Response:", json.dumps(res, indent=2))
