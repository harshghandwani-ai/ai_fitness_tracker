import json
import os
import decimal
import boto3
from boto3.dynamodb.conditions import Key
import urllib.request

# Helper to handle decimal objects in JSON dumps (DynamoDB returns Decimals)
class DecimalEncoder(json.JSONEncoder):
    def default(self, o):
        if isinstance(o, decimal.Decimal):
            return int(o) if o % 1 == 0 else float(o)
        return super(DecimalEncoder, self).default(o)

# Initialize DynamoDB client
dynamodb = boto3.resource('dynamodb')
TABLE_NAME = os.environ.get('TABLE_NAME', 'FitnessTrackerTable')
table = dynamodb.Table(TABLE_NAME)

# Get Gemini API key
def get_gemini_api_key():
    # Attempt to read from environment variable first
    api_key = os.environ.get('GEMINI_API_KEY')
    if api_key:
        return api_key
    
    # Fallback to SSM Parameter Store
    ssm = boto3.client('ssm')
    try:
        response = ssm.get_parameter(Name='/fitness_tracker/gemini_api_key', WithDecryption=True)
        return response['Parameter']['Value']
    except Exception as e:
        print(f"Error fetching API key from SSM: {e}")
        return None

def handler(event, context):
    print("Received event:", json.dumps(event))
    
    path = event.get('rawPath', event.get('path', ''))
    method = event.get('requestContext', {}).get('http', {}).get('method', event.get('httpMethod', 'GET'))
    
    # Route requests
    try:
        if path == '/sync' and method == 'POST':
            return handle_sync(event)
        elif path == '/metrics' and method == 'GET':
            return handle_get_metrics(event)
        elif path == '/advice' and method == 'POST':
            return handle_generate_advice(event)
        else:
            return response_json(404, {"error": f"Endpoint not found: {method} {path}"})
    except Exception as e:
        print("Internal error:", str(e))
        return response_json(500, {"error": "Internal Server Error", "details": str(e)})

def handle_sync(event):
    body = json.loads(event.get('body', '{}'))
    user_id = body.get('userId', 'anonymous_user')
    date_str = body.get('date', '') # e.g. "2026-06-30"
    
    if not date_str:
        return response_json(400, {"error": "Missing parameter: date"})
        
    item = {
        'userId': user_id,
        'date': date_str,
        'steps': body.get('steps', 0),
        'avgHeartRate': body.get('avgHeartRate', 0),
        'latestHeartRate': body.get('latestHeartRate', 0),
        'sleepMinutes': body.get('sleepMinutes', 0),
        'caloriesBurned': body.get('caloriesBurned', 0),
        'distanceKm': body.get('distanceKm', 0.0),
        'hydrationMl': body.get('hydrationMl', 0.0),
        'weightKg': body.get('weightKg', 0.0),
        'timestamp': int(body.get('timestamp', 0))
    }
    
    table.put_item(Item=item)
    return response_json(200, {"message": "Metrics synced successfully", "item": item})

def handle_get_metrics(event):
    query_params = event.get('queryStringParameters', {}) or {}
    user_id = query_params.get('userId', 'anonymous_user')
    
    # Query DynamoDB partition key
    result = table.query(
        KeyConditionExpression=Key('userId').eq(user_id)
    )
    
    items = result.get('Items', [])
    return response_json(200, {"metrics": items})

def handle_generate_advice(event):
    body = json.loads(event.get('body', '{}'))
    steps = body.get('steps', 0)
    avg_hr = body.get('avgHeartRate', 0)
    latest_hr = body.get('latestHeartRate', 0)
    sleep_mins = body.get('sleepMinutes', 0)
    calories = body.get('caloriesBurned', 0)
    distance = body.get('distanceKm', 0.0)
    hydration = body.get('hydrationMl', 0.0)
    weight = body.get('weightKg', 0.0)
    topic = body.get('topic', 'steps')
    
    api_key = get_gemini_api_key()
    if not api_key:
        return response_json(500, {"error": "Gemini API key is not configured on AWS backend"})
        
    # Construct prompt
    sleep_hours = sleep_mins // 60
    sleep_remainder = sleep_mins % 60
    
    prompt = f"You are a helpful personal AI Fitness Coach. Today the user has logged: " \
             f"{steps} steps ({distance:.2f} km traveled), active calories burned: {calories} kcal, " \
             f"average heart rate of {avg_hr} BPM (latest reading: {latest_hr} BPM), " \
             f"water intake of {hydration:.0f} ml, body weight: {weight:.1f} kg, " \
             f"and slept for {sleep_hours}h {sleep_remainder}m last night. "
             
    if topic == "steps":
        prompt += "Based on this steps count, provide a brief (2-3 sentences), highly actionable advice tip."
    elif topic == "workout":
        prompt += "Suggest a simple 10-minute home workout routine fitting this activity profile."
    else:
        prompt += "Suggest a suitable post-workout nutrition or recovery snack for this daily log."
        
    # Query Gemini API via direct HTTPS request (no heavy pip dependencies needed)
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={api_key}"
    headers = {"Content-Type": "application/json"}
    payload = {
        "contents": [{"parts": [{"text": prompt}]}]
    }
    
    req = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'), headers=headers, method='POST')
    try:
        with urllib.request.urlopen(req) as res:
            res_body = res.read().decode('utf-8')
            res_data = json.loads(res_body)
            advice = res_data['candidates'][0]['content']['parts'][0]['text']
            return response_json(200, {"advice": advice})
    except Exception as e:
        print(f"Gemini API request failed: {e}")
        return response_json(502, {"error": "Failed to generate advice from Gemini", "details": str(e)})

def response_json(status_code, data):
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*" # CORS enabled
        },
        "body": json.dumps(data, cls=DecimalEncoder)
    }
