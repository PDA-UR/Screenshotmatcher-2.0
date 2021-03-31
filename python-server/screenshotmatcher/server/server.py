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
from common.utils import allowed_file, get_main_dir

LOGS_TO_KEEP = 3

class Server():
    def __init__(self):
        logging.basicConfig(filename='./match.log', level=logging.DEBUG)

        if Config.IS_DIST:
            static_path = 'www'
        else:
            static_path = '../www'

        self.results_dir = 'www/results'

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
        self.app.add_url_rule("/logs", "logs", self.log_route, methods=["POST"])
        self.app.add_url_rule("/screenshot/<result_id>", "screenshot/<result_id>", self.screenshot_route)

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
                log.value_pairs.pop("match_uid", None)  # remove duplicate match_id entry
                log.send_log()
                self.last_logs.remove(log)
                return "ok"
        return "log does not match any match_id"

    def feedback_route(self):
        r_json = request.json

        uid = r_json.get('uid')
        has_result = r_json.get('hasResult')
        has_screenshot = r_json.get('hasScreenshot')
        comment = r_json.get('comment')
        device = r_json.get('device')
        
        payload = {
            'secret': Config.API_SECRET,
            'identifier': Config.IDENTIFIER,
            'comment': comment,
            'hasScreenshot': has_screenshot,
            'algorithm': Config.CURRENT_ALGORITHM,
            'device': device
        }
       
        file_payload = [('screenshot', ('screenshot', open(self.results_dir + '/result-' + uid + '/screenshot.png', 'rb'), 'image/png'))]

#        old_file_payload = [
#            ('photo', ('photo', open(self.results_dir +
#                                     '/result-' + uid + '/photo.jpg', 'rb'), 'image/jpeg')),
#            ('screenshot', ('screenshot', open(self.results_dir +
#                                               '/result-' + uid + '/screenshot.png', 'rb'), 'image/png')),
#        ]

        if has_result and has_result != 'false':
            file_payload.append(
                ('result', ('result', open(self.results_dir +
                                           '/result-' + uid + '/result.png', 'rb'), 'image/png'))
            )

        logging.info('sending feedback {}'.format(uid))

        
        urllib3.disable_warnings()
        
        return {"feedbackPosted" : "true"}


    def match_route(self):
        log = common.log.Logger()
        self.last_logs.insert(0, log)
        if len(self.last_logs) > LOGS_TO_KEEP:
            self.last_logs.pop()

        t_start = time.perf_counter()
        print("{}:\t request get".format(int(time.time()* 1000)))
        log.value_pairs["ts_request_received"] = round(time.time() * 1000)
        r_json = request.json
        b64String = r_json.get("b64")
        log.value_pairs["ts_photo_received"] = round(time.time() * 1000)
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

        # Create Matcher instance
        start_time = time.perf_counter()
        matcher = Matcher(uid, b64String, log)
        # Override default values if options are given
        if r_json.get("algorithm") :
            matcher.algorithm = r_json.get("algorithm")
        if r_json.get("ORB_nfeatures"):
            matcher.ORB_nfeatures =  r_json.get("ORB_nfeatures")
        if r_json.get("SURF_hessian_threshold"):
            matcher.SURF_hessian_threshold = r_json.get("SURF_hessian_threshold")

        # Start matcher
        match_result = matcher.match()
    
        print("Matching took {} ms".format(time.perf_counter()-t_start))
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

    def screenshot_route(self, result_id):
        if not result_id:
            return "No match-id given."

        application_path = get_main_dir()
                
        path = os.path.join(application_path, self.results_dir, "result-{}".format(result_id))
        return send_from_directory(path, "screenshot.png")
