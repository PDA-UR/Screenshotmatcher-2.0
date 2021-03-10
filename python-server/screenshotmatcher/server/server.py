import os
import sys
import uuid
import json
import requests
import urllib3
import logging
import time
import timeit
import threading

from cv2 import imwrite
from flask import Flask, request, redirect, url_for, Response, send_from_directory
from werkzeug.utils import secure_filename

import common.log

from common.config import Config
from matching.matcher import Matcher
from common.utils import allowed_file

LOGS_TO_KEEP = 3

class Server():
    def __init__(self):
        logging.basicConfig(filename='./match.log', level=logging.DEBUG)

        if Config.IS_DIST:
            static_path = 'www'
        else:
            static_path = '../www'

        self.results_dir = './www/results'

        self.last_logs = []

        self.app = Flask(__name__, static_url_path='/',
                         static_folder=static_path)

        self.app.add_url_rule('/', 'index', self.index_route)
        self.app.add_url_rule('/heartbeat', 'heartbeat', self.heartbeat_route)
        self.app.add_url_rule('/get-url', 'get-url', self.get_url_route)

        self.app.add_url_rule('/feedback', 'feedback',
                              self.feedback_route, methods=['POST'])
        self.app.add_url_rule(
            '/match', 'match', self.match_route, methods=['POST'])
        self.app.add_url_rule('/match-b64', 'match-b64',
                              self.match_route_b64, methods=['POST'])
        self.app.add_url_rule("/logs", "logs", self.log_route, methods=["POST"])

    def start(self):
        self.app.run(host=Config.HOST, port=Config.PORT, threaded=True)

    def stop(self):
        _shutdown = request.environ.get('werkzeug.server.shutdown')
        if _shutdown is None:
            raise RuntimeError('Not running with the Werkzeug Server')
        _shutdown()

    # Routes

    def index_route(self):
        return redirect("/index.html", code=301)

    def heartbeat_route(self):
        return "ok"

    def get_url_route(self):
        return Config.SERVICE_URL

    def log_route(self):
        phone_log = request.json
        for log in self.last_logs:
            if phone_log.get("match_id") == log.value_pairs.get("match_uid"):
                for key,value in phone_log.items():
                    log.value_pairs[key] = value
                self.last_logs.remove(log)
        # TODO: send log to central server
        return "ok"

    def feedback_route(self):
        uid = request.values.get('uid')
        has_result = request.values.get('hasResult')
        has_screenshot = request.values.get('hasScreenshot')
        comment = request.values.get('comment')
        device = request.values.get('device')

        payload = {
            'secret': Config.API_SECRET,
            'identifier': Config.IDENTIFIER,
            'comment': comment,
            'hasScreenshot': has_screenshot,
            'algorithm': Config.CURRENT_ALGORITHM,
            'device': device
        }

        file_payload = [
            ('photo', ('photo', open(self.results_dir +
                                     '/result-' + uid + '/photo.jpg', 'rb'), 'image/jpeg')),
            ('screenshot', ('screenshot', open(self.results_dir +
                                               '/result-' + uid + '/screenshot.png', 'rb'), 'image/png')),
        ]

        if has_result and has_result != 'false':
            file_payload.append(
                ('result', ('result', open(self.results_dir +
                                           '/result-' + uid + '/result.png', 'rb'), 'image/png'))
            )

        logging.info('sending feedback {}'.format(uid))

        urllib3.disable_warnings()

        return "ok"

    def match_route(self):
        # Check if there is an image in the request
        t_start = time.time()
        if 'file' not in request.files:
            return 'No file part'

        # Get uploaded file
        uploaded_file = request.files['file']

        # Check if file has data
        if not uploaded_file or uploaded_file.filename == '':
            return 'No selected file'

        # Check if filetype is allowed
        if not allowed_file(uploaded_file.filename):
            return 'Invalid filetype'

        # Create match uid
        uid = uuid.uuid4().hex

        # Create Match dir
        match_dir = self.results_dir + '/result-' + uid
        os.mkdir(match_dir)

        # Save uploaded image to match dir
        photo_extension = uploaded_file.filename.rsplit('.', 1)[1].lower()
        filename = 'photo.' + photo_extension
        uploaded_file.save(match_dir + '/' + filename)

        # Start matcher
        start_time = time.perf_counter()
        matcher = Matcher(uid, filename)
        t = time.time()
        match_result = matcher.match(algorithm=Config.CURRENT_ALGORITHM)
        print("Matching took {} ms".format(time.time()-t))
        end_time = time.perf_counter()

        # Send data to server for logging
        payload = {
            'secret': Config.API_SECRET,
            'identifier': Config.IDENTIFIER,
            'hasResult': bool(match_result),
            'algorithm': Config.CURRENT_ALGORITHM,
            'device': request.values.get('device'),
            'speed': round(end_time - start_time, 5)
        }

        urllib3.disable_warnings()

        response = {'uid': uid}
        print("Time spent on server: {}".format(time.time() - t_start))
        if not match_result:
            response['hasResult'] = False
            response['screenshot'] = '/results/result-' + \
                uid + '/screenshot.png'
            return Response(json.dumps(response), mimetype='application/json')
        else:
            response['hasResult'] = True
            response['filename'] = '/results/result-' + uid + '/result.png'
            return Response(json.dumps(response), mimetype='application/json')

    def match_route_b64(self):
        log = common.log.Logger()
        self.last_logs.insert(0, log)
        if len(self.last_logs) > LOGS_TO_KEEP:
            self.last_logs.pop()

        t_start = time.perf_counter()
        print("{}:\t request get".format(int(time.time()* 1000)))
        log.value_pairs["st_request_received"] = round(time.time() * 1000)
        r_json = request.json
        b64String = r_json.get("b64")
        log.value_pairs["st_photo_received"] = round(time.time() * 1000)
        print("{}:\t b64 string with size {} get".format(time.time(), sys.getsizeof(b64String)))

        if b64String is None:
            return {"error" : "no base64 string attached."}

        # Create match uid
        uid = uuid.uuid4().hex
        log.value_pairs["match_uid"] = uid
        print("{}:\t request get".format(int(time.time()* 1000)))

        # Create Match dir
        match_dir = self.results_dir + '/result-' + uid
        os.mkdir(match_dir)

        # Start matcher
        start_time = time.perf_counter()
        print("{}:\t Creating matcher...".format(time.time()))
        matcher = Matcher(uid, b64String, log)
        print("{}:\t Matcher created. Starting algo...".format(time.time()))
        t = time.time()
        match_result = matcher.match_b64(algorithm=Config.CURRENT_ALGORITHM)
        print("{}:\t Matching algo finished".format(time.time()))

        print("Matching took {} ms".format(time.time()-t))
        end_time = time.perf_counter()

        # Send data to server for logging
        payload = {
            'identifier': Config.IDENTIFIER,
            'hasResult': bool(match_result),
            'algorithm': Config.CURRENT_ALGORITHM,
            'device': request.values.get('device'),
            'speed': round(end_time - start_time, 5)
        }

        urllib3.disable_warnings()
        print("{}:\t Generating response.".format(time.time()))
        print("Time until response: {}\n".format(time.perf_counter() - t_start))

        response = {'uid': uid}
        log.value_pairs["st_response_sent"] = round(time.time() * 1000)
        if not match_result:
            log.value_pairs["match_success"] = False
            print(log.value_pairs)

            response['hasResult'] = False
            response['uid'] = uid
            return Response(json.dumps(response), mimetype='application/json')
        else:
            log.value_pairs["match_success"] = True
            print(log.value_pairs)

            response['hasResult'] = True
            response['b64'] = match_result
            return Response(json.dumps(response), mimetype='application/json')
