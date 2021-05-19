import common.utils

class Config():
    APP_NAME = 'Screenshot Matcher'
    HOST = common.utils.get_current_ip_address()
    LOG_HOST = 'https://132.199.132.34:49051/'
    PORT = 49049
    PORT_DISCOVERY = 49050
    SERVICE_URL = 'http://{}:{}'.format(HOST, PORT)
    DEFAULT_ALGORITHM = 'ORB'
    CURRENT_ALGORITHM = DEFAULT_ALGORITHM
