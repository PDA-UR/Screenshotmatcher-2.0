# -*- coding: utf-8 -*-

import common.utils

class Config():
    APP_NAME = 'Screenshot Matcher'
    CURRENT_VERSION = 'v1.1.0'
    ICON_PATH = 'gui/icon.png'
    HOST = common.utils.get_current_ip_address()
    LOG_HOST = 'https://132.199.132.34:49051/'
    PORT = 49049
    PORT_DISCOVERY = 49050
    SERVICE_URL = 'http://{}:{}'.format(HOST, PORT)
    ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg'}

    DEFAULT_ALGORITHM = 'ORB'
    CURRENT_ALGORITHM = DEFAULT_ALGORITHM
