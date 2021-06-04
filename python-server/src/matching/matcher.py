import pyscreenshot as ImageGrab
import numpy as np
import base64
import cv2
import threading
import common.log
from common.config import Config
from common.utility import get_current_ms
from common.log import log_time

# result object for the matcher
# contains the result image as np.array and b64 string
# as well as some meta data
class Match():
    def __init__(self, success=False, result_img=None, img_encoded=None, dimensions=None, matcher=None, match_count=None, match_count_good=None, screenshot_encoded=None):
        self.timestamp_ms = get_current_ms()            # timestamp of the start of the match
        self.success = success                          # true if technically successful
        self.result_img = result_img                    # result image as np.array
        self.img_encoded = img_encoded                  # result image as b64 string
        self.screenshot_encoded = screenshot_encoded    # monitor screenshot image as b64 string
        self.dimensions = dimensions                    # dimensions of the result image as dictionary (x, y, width, height)
        self.matcher = matcher                          # used matcher ('ORB', 'SURF')
        self.match_count = match_count                  # number of found matches
        self.match_count_good = match_count_good        # number of found good matches after Lowe's ratio test

class Matcher():
    SURF = 'SURF'
    ORB = 'ORB'

    def __init__(self, match_uid, img_encoded, log):
        self.match_uid = match_uid

        self.img_encoded = img_encoded

        self.algorithm = Config.CURRENT_ALGORITHM
        self.ORB_descriptor_matcher = 'BruteForce-Hamming'
        self.SURF_descriptor_matcher = 1
        self.THRESHOLDS = {'ORB' : 2000, 'SURF' : 3500}
        self.MIN_GOOD_MATCHES = {'ORB' : 20, 'SURF' : 10}

        self.log = log

        log.value_pairs['ts_screenshot_start'] = get_current_ms()
        self.screenshot = ImageGrab.grab()
        self.screenshot_encoded= ""
        log.value_pairs['pc_screen_width'] = self.screenshot.width
        log.value_pairs['pc_screen_height'] = self.screenshot.height
        log.value_pairs['ts_screenshot_finished'] = get_current_ms()

    def save_screenshot(self, img):
        self.log.value_pairs['ts_save_screenshot_start'] = get_current_ms()

        _, buf = cv2.imencode('.jpg', img)
        self.screenshot_encoded = base64.b64encode(buf).decode('ASCII')

        self.log.value_pairs['ts_save_screenshot_end'] = get_current_ms()

    def match(self):
        # Load pictures
        self.log.value_pairs['ts_img_convert_start'] = get_current_ms()
        nparr = np.frombuffer(base64.b64decode(self.img_encoded), np.uint8)
        photo = cv2.imdecode(nparr, cv2.IMREAD_GRAYSCALE)
        screen = cv2.cvtColor(np.array(self.screenshot), cv2.IMREAD_GRAYSCALE)
        screen_colored = cv2.cvtColor(np.array(self.screenshot), cv2.COLOR_BGR2RGB)

        # save screenshot in separate thread. trailing comma is mandatory
        t = threading.Thread(target=self.save_screenshot, args=(screen_colored,), daemon=True)
        t.start()

        self.log.value_pairs['ts_img_convert_end'] = get_current_ms()

        self.log.value_pairs['ts_matching_start'] = get_current_ms()
        match_result = self.match_screenshot(photo, screen, screen_colored)
        self.log.value_pairs['ts_matching_end'] = get_current_ms()

        t.join() # make sure the screenshot is saved before a result is returned

        match_result.screenshot_encoded = self.screenshot_encoded

        return match_result

    def match_screenshot(self, photo, screen, screen_colored):
        result = Match(matcher=self.algorithm)
        
        self.log.value_pairs['algorithm'] = self.algorithm
        self.log.value_pairs['threshold'] = self.THRESHOLDS[self.algorithm]

        try:
            if self.algorithm == 'SURF':
                matches, kp_photo, kp_screen = self.find_matches_SURF(photo, screen)
            elif self.algorithm == 'ORB':
                matches, kp_photo, kp_screen = self.find_matches_ORB(photo, screen)

            match_count = len(matches)
            result.match_count = match_count

            if not matches or match_count == 0:
                raise Exception('no matches found')
            # store all the good matches as per Lowe's ratio test.
            good_matches = self.calculate_lowes_ratio(matches)    

            match_count_good = len(good_matches)
            result.match_count_good = match_count_good

            if not good_matches or match_count_good < self.MIN_GOOD_MATCHES[self.algorithm]:
                raise Exception('not enough good matches')

            dimensions = self.calculate_homography(photo, good_matches, kp_photo, kp_screen)

            if not dimensions:
                raise Exception('could not calculate homography')

            result_img = screen_colored[dimensions['y'] : dimensions['y'] + dimensions['height'],
                                        dimensions['x'] : dimensions['x'] + dimensions['width']]

            _, buf = cv2.imencode('.jpg', result_img)
            img_encoded = base64.b64encode(buf).decode('ASCII')

            result.dimensions = dimensions
            result.result_img = result_img
            result.img_encoded = img_encoded
            result.success = True
        except Exception as e:
            print(str(e))
        finally:
            return result

    def calculate_lowes_ratio(self, matches):
        good = []
        for m,n in matches:
            if m.distance < 0.75*n.distance:
                good.append(m)
        return good

    def calculate_homography(self, photo, matches, kp_photo, kp_screen):
        photo_pts = np.float32([ kp_photo[m.queryIdx].pt for m in matches ]).reshape(-1,1,2) # pylint: disable=too-many-function-args
        screen_pts = np.float32([ kp_screen[m.trainIdx].pt for m in matches ]).reshape(-1,1,2) # pylint: disable=too-many-function-args
    
        M, _ = cv2.findHomography(photo_pts, screen_pts, cv2.RANSAC, 5.0)
    
        if M is None or not M.any() or len(M) == 0:
            raise Exception('could not find homography')
    
        h, w = photo.shape
        pts = np.float32([ [0,0],[0,h-1],[w-1,h-1],[w-1,0] ]).reshape(-1,1,2) # pylint: disable=too-many-function-args
        dst = cv2.perspectiveTransform(pts, M)
    
        minX = dst[0][0][0]
        minY = dst[0][0][1]
        maxX = dst[0][0][0]
        maxY = dst[0][0][1]
    
        for i in range(4):
            if dst[i][0][0] < minX:
                minX = dst[i][0][0]
            if dst[i][0][0] > maxX:
                maxX = dst[i][0][0]
            if dst[i][0][1] < minY:
                minY = dst[i][0][1]
            if dst[i][0][1] > maxY:
                maxY = dst[i][0][1]
    
        minX = int(minX)
        minY = int(minY)
        maxX = int(maxX)
        maxY = int(maxY)
    
        if minX < 0:
            minX = 0
        if minY < 0:
            minY = 0
    
        if maxX <= minX or maxY <= minY:
            raise Exception('invalid dimensions: max < min')
    
        dimensions = {'x' : minX, 'y' : minY, 'width' : maxX - minX, 'height' : maxY - minY}

        return dimensions

    def find_matches_SURF(self, photo, screen):
        # Init algorithm
        surf = cv2.xfeatures2d.SURF_create(self.THRESHOLDS['SURF'])
        surf.setUpright(True)
    
        # Detect and compute
        kp_photo, des_photo = surf.detectAndCompute(photo, None)
        kp_screen, des_screen = surf.detectAndCompute(screen, None)
    
        # Descriptor Matcher
        index_params = dict(algorithm = self.SURF_descriptor_matcher, trees = 5)
        search_params = dict(checks = 50)
        flann = cv2.FlannBasedMatcher(index_params, search_params)

        # Calc Matches
        matches = flann.knnMatch(des_photo, des_screen, k=2)

        return matches, kp_photo, kp_screen

    def find_matches_ORB(self, photo, screen):
        # Init algorithm
        orb = cv2.ORB_create(self.THRESHOLDS['ORB'])
    
        # Detect and compute
        kp_photo, des_photo = orb.detectAndCompute(photo, None)
        kp_screen, des_screen = orb.detectAndCompute(screen, None)
    
        # Descriptor Matcher
        descriptor_matcher = cv2.DescriptorMatcher_create(self.ORB_descriptor_matcher)
        matches = descriptor_matcher.knnMatch(des_photo, des_screen, k=2)

        return matches, kp_photo, kp_screen
