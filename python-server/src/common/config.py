import common.utility

class Config():
    APP_NAME = 'Screenshot Matcher'
    APP_VERSION = "1.0"
    HOST = common.utility.get_current_ip_address()
    LOG_HOST = 'https://132.199.132.34:49051/'
    PORT = 49049
    PORT_DISCOVERY = 49050
    SERVICE_URL = 'http://{}:{}'.format(HOST, PORT)
    DEFAULT_ALGORITHM = 'ORB'
    CURRENT_ALGORITHM = DEFAULT_ALGORITHM
    ID = ""
    FULL_SCREENSHOTS_ENABLED = True
    UNKNOWN_DEVICE_HANDLING = 0 # 0: ask, 1: allow all, 2: block all

    HOMEPAGE = "https://github.com/PDA-UR/Screenshotmatcher-2.0"