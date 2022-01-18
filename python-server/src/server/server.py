import uuid
import json
import platform
import queue
import threading
import time
import logging
from collections import OrderedDict
from flask import Flask, request, Response
import common.log
from common.config import Config
from matching.matcher import Matcher
from common.utility import get_current_ms, RepeatTimer
from common.permission import is_client_allowed, request_permission_for_client, create_single_match_token
from server.matching_request import MatchingRequest

class Server():
    def __init__(self, queue):
        self.CLEANUP_INTERVAL_SEC = 10*60
        self.REQUEST_LIFETIME_MS = 10*60*1000
        self.MAX_REQ_SAVED_PER_CLIENT = 1
        self.past_client_requests = {}
        self.queue = queue
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
        werkzeug_logger = logging.getLogger("werkzeug")
        werkzeug_logger.setLevel(logging.ERROR)
        self.start_cleanup_routine()
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
        client_id = phone_log.get("client_id")

        matching_request = self.past_client_requests.get(client_id).get("requests").get(phone_log.get("match_id"))
        if not matching_request:
            return {'response' : 'log does not match any match_id'}

        # logging disabled. delete corresponding request object
        if phone_log.get("logging_disabled"):
            self.past_client_requests.get(client_id).get("requests").pop(phone_log.get("match_id"), None)
            return {'response': 'ok'}

        server_log = matching_request.log
        if not server_log:
            return {'response' : 'log does not match any match_id'}

        for key,value in phone_log.items():
            server_log.value_pairs[key] = value

        server_log.value_pairs.pop('match_uid', None)  # remove duplicate match_id entry
        server_log.value_pairs["participant_id"] = Config.ID
        server_log.value_pairs["operating_system"] = platform.platform()
        server_log.send_log()
        self.past_client_requests.get(client_id).get("requests").pop(phone_log.get("match_id"))
        return {'response': 'ok'}

    # Dummy implementation
    def feedback_route(self):  
        return {'feedbackPosted' : 'true'}

    def match_route(self):
        t_request_received = get_current_ms()

        # Convert the json data
        r_json = request.json
        client_id = r_json.get("client_id")
        print("client id: {}".format(client_id))

        uid = uuid.uuid4().hex
        
        # Create thread for a new request
        request_thread = threading.Thread(
            target=self.new_matching_request,
            args=[client_id, uid, r_json, t_request_received],
            daemon=True)
        
        # Check if this client is permitted to request a match
        # Let the client send a request to /permission if unknown clients require individual prompts (tray setting)
        is_allowed = is_client_allowed(
            current_setting=Config.UNKNOWN_CLIENT_HANDLING, 
            client_id=r_json.get("client_id"),
            client_name=r_json.get("client_name"),
            token=r_json.get("permission_token")
        )

        if is_allowed == 1:
            request_thread.start()
        elif is_allowed == -1:
            error = {"error" : "permission_denied"}
            print(error)
            return Response(json.dumps(error), mimetype='application/json')
        elif is_allowed == 0:
            request_thread.start()
            error = {"error" : "permission_required"}
            error["uid"] = uid
            print(error)
            return Response(json.dumps(error), mimetype='application/json')
    
        # Client is requesting a previous match, after outstanding permission has been granted
        # TODO: might cause a race condition
        prev_muid = r_json.get("match_id")
        if prev_muid:
            response = self.past_client_requests.get(client_id).get("requests").get(prev_muid).response
            print(response)
            return response

        # wait for the matching result
        request_thread.join()
        matcher = self.past_client_requests.get(client_id).get("requests").get(uid)
        print(matcher.response)
        return matcher.response  

    # Return a screenshot with the same match_id as in the http POST request
    def screenshot_route(self):
        response = {}
        if not Config.FULL_SCREENSHOTS_ENABLED:
            response["error"] = "disabled_by_host_error"
        else:
            match_id = request.json.get("match_id")
            client_id = request.json.get("client_id")
            if match_id:
                try:
                    response["result"] = self.past_client_requests.get(client_id).get("requests").get(match_id).match_result.screenshot_encoded
                    # Delete the image to free up RAM
                    del self.past_client_requests.get(client_id).get("requests").get(match_id).match_result.screenshot_encoded
                except IndexError:
                    response["error"] = "Screenshot for given match_id not found on server."
            else:
                response["error"] = 'No match-id given.'

        return Response(json.dumps(response), mimetype='application/json')

    # Individual client permission needs to be granted by the user.
    # Sends a one-time-token to the client, if permission is only granted for one match.
    def permission_route(self):
        client_id = request.json.get("client_id")
        client_name = request.json.get("client_name")
        if not client_id or not client_name:
            error = {"error" : "data_error"}
            return Response(json.dumps(error), mimetype='application/json')

        user_response = request_permission_for_client(
            client_id=client_id,
            client_name=client_name,
            queue=self.queue)
        response = {}
        if user_response == "allow once":
            response["response"] = "permission_granted"
            response["permission_token"] = create_single_match_token()
        elif user_response == "allow":
            response["response"] = "permission_granted"
        else:
            response["response"] = "permission_denied"

        return Response(json.dumps(response), mimetype='application/json')

    def new_matching_request(self, client_id, uid, data, time):
        # Create a dict for this client, if not present
        if not self.past_client_requests.get(client_id):
            self.past_client_requests[client_id] = {}
            self.past_client_requests[client_id]["requests"] = OrderedDict()

        # insert new request
        self.past_client_requests.get(client_id).get("requests")[uid] = MatchingRequest(uid=uid, data=data, time=time)

        # delete oldest request, if dict is too big
        if len(self.past_client_requests.get(client_id).get("requests")) > self.MAX_REQ_SAVED_PER_CLIENT:
            self.past_client_requests.get(client_id).get("requests").popitem(last = False)

        # update timestamp (used for cleanup)
        self.past_client_requests.get(client_id)["last_conn_time"] = get_current_ms()

    def start_cleanup_routine(self):
        cleanup_thread = RepeatTimer(self.CLEANUP_INTERVAL_SEC, self.clean_old_requests)
        cleanup_thread.start()
        
    # delete requests from old clients from memory
    def clean_old_requests(self):
        cur_time = get_current_ms()
        for client_id, entry in self.past_client_requests.items():
            if entry["last_conn_time"] + self.REQUEST_LIFETIME_MS < cur_time:
                entry["requests"].clear()
