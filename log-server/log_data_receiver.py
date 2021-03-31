import csv
import json
import os
from flask import Flask, request

app = Flask(__name__)

HOST = "0.0.0.0"
PORT = 49051
FILENAME = "study_log.csv"
FIELDS = [
    'ORB_nfeatures',
    'SURF_hessian_threshold',
    'algorithm',
    'client_id',
    'feedback_sent',
    'long_side',
    'match_id',
    'match_success',
    'preview_height',
    'preview_width',
    'save_full',
    'save_match',
    'share_full',
    'share_match',
    'tc_button_pressed',
    'tc_http_request',
    'tc_http_response',
    'tc_image_captured',
    'tc_result_shown',
    'ts_img_convert_end',
    'ts_img_convert_start',
    'ts_matching_end',
    'ts_matching_start',
    'ts_photo_received',
    'ts_request_received',
    'ts_response_sent',
    'ts_save_screenshot_end',
    'ts_save_screenshot_start',
    'ts_screenshot_finished',
    'ts_screenshot_start'
]

@app.route('/', methods=['POST'])
def receive_log():
    json = request.json

    write_header()
    entry = create_entry(json)

    with open(FILENAME, "a", newline='') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=FIELDS, delimiter=",", quoting=csv.QUOTE_ALL)
        writer.writerow(entry)
    return "ok"
        
def write_header():
    if not os.path.exists(FILENAME):
        with open(FILENAME, "a", newline='') as csvfile:
            writer = csv.DictWriter(csvfile, fieldnames=FIELDS, delimiter=",", quoting=csv.QUOTE_ALL)
            writer.writeheader()

# create a dict with None for every parameter that was not given
def create_entry(json):
    entry = {}
    for s in FIELDS:
        entry[s] = None
    for key, value in json.items():
        entry[key] = value
    return entry

if __name__ == "__main__":
    app.run(host=HOST, port=PORT, threaded=True, ssl_context="adhoc")