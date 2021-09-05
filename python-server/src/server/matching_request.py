import uuid
import json
import threading
from flask import Response
import common.log
from matching.matcher import Matcher
from common.utility import get_current_ms

class MatchingRequest():
    def __init__(self, uid, data, time):
        self.uid = uid
        self.time_created = get_current_ms()
        self.data = data
        self.log = common.log.Logger()
        self.log.value_pairs["ts_request_received"] = time
        self.response = None
        self.matcher = None
        self.match_result = None
        self.error = None
        self.b64_string = ""

        self.run_matching()

    def get_photo_from_request(self):
        # Get the base64 string encoded photo
        b64String = self.data.get('b64')
        self.log.value_pairs['ts_photo_received'] = get_current_ms()
        if b64String is None:
            error = {"error" : "missing_image_error"}
            self.response = Response(json.dumps(error), mimetype='application/json')
            raise ValueError("No base64 string attached to request.")   # stop the thread prematurely
        else:
            self.b64_string = b64String

    def determine_matching_algo(self):
        if self.data.get('algorithm') :
            self.matcher.algorithm = self.data.get('algorithm')
        if self.data.get('ORB_nfeatures'):
            self.matcher.THRESHOLDS['ORB'] =  self.data.get('ORB_nfeatures')
        if self.data.get('SURF_hessian_threshold'):
            self.matcher.THRESHOLDS['SURF'] = self.data.get('SURF_hessian_threshold')

    def run_matching(self):
        t_b64 = threading.Thread(target=self.get_photo_from_request, args=(), daemon=True)
        t_b64.start()

        self.matcher = Matcher(self.uid, self.log)

        t_screen_grab = threading.Thread(target=self.matcher.grab_full_screenshot, args=(), daemon=True)
        t_screen_grab.start()

        # Wait for data from other threads being available
        t_b64.join()
        self.matcher.img_encoded = self.b64_string
        t_screen_grab.join()

        # Override default algo, if options given in the request
        self.determine_matching_algo()

        # Start matcher
        self.match_result = self.matcher.match()
        if self.match_result:
            self.response = self.create_response()

        return

    def create_response(self):
        # Generate response
        response = {'uid': self.uid}
        self.log.value_pairs['ts_response_sent'] = get_current_ms()
        if not self.match_result.success:
            self.log.value_pairs['match_success'] = False
            response['hasResult'] = False
            return Response(json.dumps(response), mimetype='application/json')
        else:
            self.log.value_pairs['match_success'] = True
            response['hasResult'] = True
            response['b64'] = self.match_result.img_encoded
            return Response(json.dumps(response), mimetype='application/json')