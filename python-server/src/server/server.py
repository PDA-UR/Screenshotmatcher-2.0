import uuid
import json
import platform
from flask import Flask, request, Response
import common.log
from common.config import Config
from matching.matcher import Matcher
from common.utility import get_current_ms, is_device_allowed, request_permission_for_device, create_single_match_token

class Server():
    def __init__(self):
        self.last_logs = []
        self.last_screenshots = []
        self.MAX_LOGS = 3
        self.MAX_SCREENSHOTS = 3
        self.app = Flask(__name__)

        self.app.add_url_rule('/heartbeat', 'heartbeat', self.heartbeat_route)
        self.app.add_url_rule('/feedback', 'feedback',
                              self.feedback_route, methods=['POST'])
        self.app.add_url_rule(
            '/match', 'match', self.match_route, methods=['POST'])
        self.app.add_url_rule('/logs', 'logs', self.log_route, methods=['POST'])
        self.app.add_url_rule('/screenshot', 'screenshot', self.screenshot_route, methods=['POST'])
        self.app.add_url_rule('/permission', 'permission', self.permission_route, methods=['POST'])

    def start(self):
        self.app.run(host=Config.HOST, port=Config.PORT, threaded=True)

    def stop(self):
        _shutdown = request.environ.get('werkzeug.server.shutdown')
        if _shutdown is None:
            raise RuntimeError('Not running with the Werkzeug Server')
        _shutdown()

    # Routes
    def heartbeat_route(self):
        return 'ok'

    def log_route(self):
        phone_log = request.json
        for log in self.last_logs:
            if phone_log.get('match_id') == log.value_pairs.get('match_uid'):
                for key,value in phone_log.items():
                    log.value_pairs[key] = value

                log.value_pairs.pop('match_uid', None)  # remove duplicate match_id entry
                log.value_pairs["participant_id"] = Config.PARTICIPANT_ID
                log.value_pairs["operating_system"] = platform.platform()
                log.send_log()
                self.last_logs.remove(log)
                return {'response': 'ok'}
        return {'response' : 'log does not match any match_id'}

    # Dummy implementation
    def feedback_route(self):  
        return {'feedbackPosted' : 'true'}

    def match_route(self):
        # Create a logger for this match
        log = common.log.Logger()
        self.last_logs.insert(0, log)
        if len(self.last_logs) > self.MAX_LOGS:
            self.last_logs.pop()
        log.value_pairs['ts_request_received'] = get_current_ms()

        # convert the json data
        r_json = request.json
        
        # Check if this device is permitted to request a match
        # let the client send a request to /permission if it's not black- or whitelisted
        device_id = r_json.get("device_id")
        device_name = r_json.get("device_name")
        permission_token = r_json.get("permission_token")
        if is_device_allowed(Config.UNKNOWN_DEVICE_HANDLING, device_id, device_name, permission_token) == 1:
            pass
        elif is_device_allowed(Config.UNKNOWN_DEVICE_HANDLING, device_id, device_name, permission_token) == -1:
            error = {"error" : "permission_denied"}
            return Response(json.dumps(error), mimetype='application/json')
        elif is_device_allowed(Config.UNKNOWN_DEVICE_HANDLING, device_id, device_name, permission_token) == 0:
            error = {"error" : "permission_required"}
            return Response(json.dumps(error), mimetype='application/json')

        # Get the base64 string encoded photo
        b64String = r_json.get('b64')
        log.value_pairs['ts_photo_received'] = get_current_ms()
        if b64String is None:
            return {'error' : 'missing_image_error'}

        # Create match uid
        uid = uuid.uuid4().hex
        log.value_pairs['match_uid'] = uid

        # Create Matcher instance
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

        # Save screenshot temporarily.
        if match_result.screenshot_encoded:
            self.last_screenshots.append((uid, match_result.screenshot_encoded))
            if len(self.last_screenshots) > self.MAX_SCREENSHOTS:
                self.last_screenshots.pop(0)
    
        # Generate response
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

    # Return a screenshot with the sae match_id as in the http POST request
    def screenshot_route(self):
        response = {}
        if not Config.FULL_SCREENSHOTS_ENABLED:
            response["error"] = "disabled_by_host_error"
        else:
            match_id = request.json.get("match_id")
            if not match_id:
                response["error"] = 'No match-id given.'
            else:
                for entry in self.last_screenshots:
                    if entry[0] == match_id:
                        response["result"] = entry[1]
                        break
                if not response.get("result"):
                    response["error"] = 'match-id not found among last matches.'

        return Response(json.dumps(response), mimetype='application/json')

    def permission_route(self):
        device_id = request.json.get("device_id")
        device_name = request.json.get("device_name")
        if not device_id or not device_name:
            error = {"error" : "data_error"}
            return Response(json.dumps(error), mimetype='application/json')

        # user_response = request_permission_for_device(device_id, device_name)
        user_response = "allow once"
        response = {}
        if user_response == "allow once":
            permission_token = create_single_match_token()
            response["response"] = "permission_granted"
            response["permission_token"] = permission_token
        elif user_response == "allow":
            response["response"] = "permission_granted"
        else:
            response["response"] = "permission_denied"

        return Response(json.dumps(response), mimetype='application/json')
