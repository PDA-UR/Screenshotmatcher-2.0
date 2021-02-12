import os
import sys
import uuid
import json
import requests
import urllib3
import logging
import time

from flask import Flask, request, redirect, url_for, Response, send_from_directory
from werkzeug.utils import secure_filename

from common.config import Config

from matching.matcher import Matcher
from common.utils import allowed_file


class Server():
  def __init__(self):

    logging.basicConfig(filename='./match.log',level=logging.DEBUG)

    if Config.IS_DIST:
      static_path = 'www'
    else:
      static_path = '../www'

    self.results_dir = './www/results'

    self.app = Flask(__name__, static_url_path='/', static_folder=static_path)


    self.app.add_url_rule('/', 'index', self.index_route)
    self.app.add_url_rule('/heartbeat', 'heartbeat', self.heartbeat_route)
    self.app.add_url_rule('/get-url', 'get-url', self.get_url_route)
    
    self.app.add_url_rule('/feedback', 'feedback', self.feedback_route, methods=['POST'])
    self.app.add_url_rule('/match', 'match', self.match_route, methods=['POST'])


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
     ('photo', ('photo', open(self.results_dir + '/result-' + uid + '/photo.jpg', 'rb'), 'image/jpeg')),
     ('screenshot', ('screenshot', open(self.results_dir + '/result-' + uid + '/screenshot.png', 'rb'), 'image/png')),
    ]

    if has_result and has_result != 'false':
      file_payload.append(
        ('result', ('result', open(self.results_dir + '/result-' + uid + '/result.png', 'rb'), 'image/png'))
      )

    logging.info('sending feedback {}'.format(uid))

    urllib3.disable_warnings()
    requests.post("https://feedback.hartmann-it.de/feedback", data=payload, files=file_payload, verify=False )

    return "ok"

  def match_route(self):

    # Check if there is an image in the request
    if 'image_file.jpg' not in request.files:
      return 'No file part'

    # Get uploaded file
    uploaded_file = request.files['image_file.jpg']

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
    uploaded_file.save( match_dir + '/' + filename )
    
    # Start matcher
    start_time = time.perf_counter()
    matcher = Matcher(uid, filename)
    match_result = matcher.match(algorithm=Config.CURRENT_ALGORITHM)
    end_time = time.perf_counter()

    # Send data to server for logging
    payload = { 
      'secret': Config.API_SECRET,
      'identifier': Config.IDENTIFIER,
      'hasResult': bool(match_result),
      'algorithm': Config.CURRENT_ALGORITHM,
      'device': request.values.get('device'),
      'speed': round( end_time - start_time , 5)
    }

    urllib3.disable_warnings()
    requests.post("https://feedback.hartmann-it.de/log", data=payload, verify=False )

    response = {'uid': uid}

    if not match_result:
      response['hasResult'] = False
      response['screenshot'] = '/results/result-' + uid + '/screenshot.png'
      return Response( json.dumps(response), mimetype='application/json' )
    else:
      response['hasResult'] = True
      response['filename'] = '/results/result-' + uid + '/result.png'
      return Response( json.dumps(response), mimetype='application/json' )