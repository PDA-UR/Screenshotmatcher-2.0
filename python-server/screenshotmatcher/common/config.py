# -*- coding: utf-8 -*-

import common.utils
import sys
import hashlib
import platform
import random


identifier = ""

try:
  with open('id.txt', 'r') as f:
    identifier = f.read()
except:
  with open('id.txt', 'w') as f:
    data_for_encoding = "{}-{}".format( platform.platform(), random.randrange(1000000, 9999999) )
    identifier = data_for_encoding
    f.write(identifier)

class Config():

  IDENTIFIER = identifier

  APP_NAME = 'Screenshot Matcher'
  CURRENT_VERSION = 'v1.1.0'
  ICON_PATH = 'gui/icon.png'
  HOST = common.utils.getCurrentIPAddress()
  PORT = 49049
  PORT_DISCOVERY = 49050
  SERVICE_URL = 'http://{}:{}'.format(HOST, PORT)
  ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg'}

  DEFAULT_ALGORITHM = 'ORB'
  CURRENT_ALGORITHM = DEFAULT_ALGORITHM

  API_SECRET = 'd45f6g7h8j9§$d5AHF7h8k'

  IS_DIST = getattr(sys, 'frozen', False)