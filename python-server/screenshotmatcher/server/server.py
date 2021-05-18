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
import platform

from cv2 import imwrite
from flask import Flask, request, redirect, url_for, Response, send_from_directory, send_file
from werkzeug.utils import secure_filename

import common.log

from common.config import Config
from matching.matcher import Matcher
from common.utils import allowed_file, get_main_dir, get_current_ms

MAX_LOGS = 3
MAX_SCREENSHOTS = 3

class Server():
    def __init__(self):
        logging.basicConfig(filename='./match.log', level=logging.DEBUG)

        if Config.IS_DIST:
            static_path = 'www'
        else:
            static_path = '../www'

        self.results_dir = 'www/results'

        self.last_logs = []
        self.last_screenshots = []

        self.app = Flask(__name__, static_url_path='/',
                         static_folder=static_path)

        self.app.add_url_rule('/', 'index', self.index_route)
        self.app.add_url_rule('/heartbeat', 'heartbeat', self.heartbeat_route)
        self.app.add_url_rule('/get-url', 'get-url', self.get_url_route)

        self.app.add_url_rule('/feedback', 'feedback',
                              self.feedback_route, methods=['POST'])
        self.app.add_url_rule(
            '/match', 'match', self.match_route, methods=['POST'])
        self.app.add_url_rule('/logs', 'logs', self.log_route, methods=['POST'])
        self.app.add_url_rule('/screenshot', 'screenshot', self.screenshot_route, methods=['POST'])

    def start(self):
        self.app.run(host=Config.HOST, port=Config.PORT, threaded=True)

    def stop(self):
        _shutdown = request.environ.get('werkzeug.server.shutdown')
        if _shutdown is None:
            raise RuntimeError('Not running with the Werkzeug Server')
        _shutdown()


    # Routes
    def index_route(self):
        return redirect('/index.html', code=301)

    def heartbeat_route(self):
        return 'ok'

    def get_url_route(self):
        return Config.SERVICE_URL

    def log_route(self):
        phone_log = request.json
        for log in self.last_logs:
            if phone_log.get('match_id') == log.value_pairs.get('match_uid'):
                for key,value in phone_log.items():
                    log.value_pairs[key] = value
                log.value_pairs.pop('match_uid', None)  # remove duplicate match_id entry
                log.send_log()
                self.last_logs.remove(log)
                return {'response': 'ok'}
        return {'response' : 'log does not match any match_id'}

    def feedback_route(self):  
        return {'feedbackPosted' : 'true'}

    def match_route(self):
        log = common.log.Logger()
        self.last_logs.insert(0, log)
        if len(self.last_logs) > MAX_LOGS:
            self.last_logs.pop()

        t_start = time.perf_counter()
        print('{}:\t request get'.format(int(time.time()* 1000)))
        log.value_pairs['ts_request_received'] = get_current_ms()
        r_json = request.json
        b64String = r_json.get('b64')
        log.value_pairs['ts_photo_received'] = get_current_ms()
        print('{}:\t b64 string with size {} get'.format(time.time(), sys.getsizeof(b64String)))
        
        if b64String is None:
            return {'error' : 'no base64 string attached.'}

        # Create match uid
        uid = uuid.uuid4().hex
        log.value_pairs['match_uid'] = uid
        print('{}:\t request get'.format(int(time.time()* 1000)))

        # Create Match dir
        match_dir = self.results_dir + '/result-' + uid
        os.mkdir(match_dir)

        # Create Matcher instance
        start_time = time.perf_counter()
        matcher = Matcher(uid, b64String, log)
        # Override default values if options are given
        if r_json.get('algorithm') :
            matcher.algorithm = r_json.get('algorithm')
        if r_json.get('ORB_nfeatures'):
            matcher.THRESHOLDS['ORB'] =  r_json.get('ORB_nfeatures')
        if r_json.get('SURF_hessian_threshold'):
            matcher.THRESHOLDS['SURF'] = r_json.get('SURF_hessian_threshold')

        # Start matcher
        match_result = matcher.match()

        # save screenshot temp. for later requests
        if match_result.screenshot_encoded:
            self.last_screenshots.append((uid, match_result.screenshot_encoded))
            if len(self.last_screenshots) > MAX_SCREENSHOTS:
                self.last_screenshots.pop(0)
    
        print('Matching took {} ms'.format(time.perf_counter()-t_start))
        end_time = time.perf_counter()

        urllib3.disable_warnings()
        print('{}:\t Generating response.'.format(time.time()))
        print('Time until response: {}\n'.format(time.perf_counter() - t_start))

        response = {'uid': uid}
        log.value_pairs['ts_response_sent'] = get_current_ms()
        if not match_result.success:
            log.value_pairs['match_success'] = False

            response['hasResult'] = False
            response['uid'] = uid
            return Response(json.dumps(response), mimetype='application/json')
        else:
            log.value_pairs['match_success'] = True

            response['hasResult'] = True
            response['b64'] = match_result.img_encoded
            return Response(json.dumps(response), mimetype='application/json')

    def screenshot_route(self):
        match_id = request.json.get("match_id")
        if not match_id:
            return 'No match-id given.'

        response = {}
        for entry in self.last_screenshots:
            if entry[0] == match_id:
                response["result"] = entry[1]
                break
        if not response.get("result"):
            return 'match-id not found among last matches'

        return Response(json.dumps(response), mimetype='application/json')
