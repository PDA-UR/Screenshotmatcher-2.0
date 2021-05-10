import pyscreenshot as ImageGrab
import time
import numpy as np
import logging
import base64
import cv2
import threading

import common.log

from common.config import Config

class Match():
    def __init__(self, success=False, result_img=None, img_encoded=None, dimensions=None, matcher=None, match_count=None, match_count_good=None):
        self.success = success
        self.result_img = result_img
        self.img_encoded = img_encoded
        self.dimensions = dimensions
        self.matcher = matcher
        self.match_count = match_count
        self.match_count_good = match_count_good 

class Matcher():
    SURF = "SURF"
    ORB = "ORB"

    def __init__(self, match_uid, img_encoded, log, create_screenshot = True):

        self.match_uid = match_uid

        self.match_dir = './www/results/result-' + match_uid

        self.img_encoded = img_encoded
        self.algorithm = Config.CURRENT_ALGORITHM
        self.ORB_nfeatures = 2000
        self.SURF_hessian_threshold = 3500

        self.log = log

        if create_screenshot:
            self.screenshot_file = 'screenshot.png'
            log.value_pairs["ts_screenshot_start"] = round(time.time() * 1000)
            self.screenshot = ImageGrab.grab()
            log.value_pairs["ts_screenshot_finished"] = round(time.time() * 1000)

    def setMatchDir(self, new_dir):
        self.match_dir = new_dir

    def writeLog(self, msg):
        logging.info('MATCHER - {}'.format(msg))

    def formatTimeDiff(self, start, end):
        return round( (end - start) * 1000, 2 )

    def match(self):
        start_time = time.perf_counter()

        # Load pictures
        self.log.value_pairs["ts_img_convert_start"] = round(time.time() * 1000)
        nparr = np.frombuffer(base64.b64decode(self.img_encoded), np.uint8)
        photo = cv2.imdecode(nparr, cv2.IMREAD_GRAYSCALE)
        screen = cv2.cvtColor(np.array(self.screenshot), cv2.IMREAD_GRAYSCALE)
        screen_colored = cv2.cvtColor(np.array(self.screenshot), cv2.COLOR_BGR2RGB)
        self.log.value_pairs["ts_img_convert_end"] = round(time.time() * 1000)

        # save screenshot in separate thread
        t = threading.Thread(target=self.save_screenshot, args=(), daemon=True)
        t.start()

        self.log.value_pairs["ts_matching_start"] = round(time.time() * 1000)
        # Provisional switch statement
        if self.algorithm == 'SURF':
            match_result = self.algorithm_SURF(photo, screen, screen_colored)
        elif self.algorithm == 'ORB':
            match_result = self.algorithm_ORB(photo, screen, screen_colored)
        else:
            match_result = self.algorithm_SURF(photo, screen, screen_colored)

        self.log.value_pairs["ts_matching_end"] = round(time.time() * 1000)

        self.writeLog('FINAL TIME {}ms'.format(round( (time.perf_counter() - start_time) * 1000 )))

        return match_result

    def algorithm_SURF(self, photo, screen, screen_colored, descMatcher = 1):
        result = Match(matcher=Matcher.SURF)

        self.log.value_pairs["algorithm"] = "SURF"
        self.log.value_pairs["SURF_hessian_threshold"] = self.SURF_hessian_threshold
    
        # Init algorithm
        surf = cv2.xfeatures2dSURF_create(self.SURF_hessian_threshold)
        surf.setUpright(True)
    
        # Detect and compute
        kp_photo, des_photo = surf.detectAndCompute(photo, None)
        kp_screen, des_screen = surf.detectAndCompute(screen, None)
    
        # Descriptor Matcher
        try:
            index_params = dict(algorithm = descMatcher, trees = 5)
            search_params = dict(checks = 50)
            flann = cv2.FlannBasedMatcher(index_params, search_params)
        except:
            return result
    
        # Calc knn Matches
        try:
            matches = flann.knnMatch(des_photo, des_screen, k=2)
        except:
            return result
    
        match_count = len(matches)
        result.match_count = match_count

        if not matches or match_count == 0:
            return result
    
        # store all the good matches as per Lowe's ratio test.
        good = []
        for m,n in matches:
            if m.distance < 0.75*n.distance:
                good.append(m)
    
        match_count_good = len(good)
        result.match_count_good = match_count_good

        if not good or match_count_good < 10: # AS TODO: magic number as threshold for good matches in SURF algorithm
            return result
    
        photo_pts = np.float32([ kp_photo[m.queryIdx].pt for m in good ]).reshape(-1,1,2) # pylint: disable=too-many-function-args
        screen_pts = np.float32([ kp_screen[m.trainIdx].pt for m in good ]).reshape(-1,1,2) # pylint: disable=too-many-function-args
    
        M, _ = cv2.findHomography(photo_pts, screen_pts, cv2.RANSAC, 5.0)
    
        if M is None or not M.any() or len(M) == 0:
            return result
    
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
    
        if maxX - minX <= 0 or maxY - minY <= 0:
            return result
    
        dimensions = {'width' : maxX - minX, 'height' : maxY - minY}

        result_img = screen_colored[ int(minY):int(maxY), int(minX):int(maxX)]

        retval, buffer = cv2.imencode('.jpg', result_img)
        img_encoded = base64.b64encode(buffer).decode("ASCII")

        result.dimensions = dimensions
        result.result_img = result_img
        result.img_encoded = img_encoded
        result.success = True

        #result = Match(result_img, img_encoded, dimensions, Matcher.SURF, match_count, match_count_good)

        return result
    
    
    def algorithm_ORB(self, photo, screen, screen_colored, descriptor_matcher_name = 'BruteForce-Hamming'):
        result = Match(matcher=Matcher.ORB)

        self.log.value_pairs["algorithm"] = "ORB"
        self.log.value_pairs["ORB_nfeatures"] = self.ORB_nfeatures
    
        # Init algorithm
        orb = cv2.ORB_create(self.ORB_nfeatures)
    
        # Detect and compute
        kp_photo, des_photo = orb.detectAndCompute(photo, None)
        kp_screen, des_screen = orb.detectAndCompute(screen, None)
    
        t3 = time.perf_counter()
    
        # Descriptor Matcher
        try:
            descriptor_matcher = cv2.DescriptorMatcher_create(descriptor_matcher_name)
        except:
            return result
    
        # Calc knn Matches
        try:
            matches = descriptor_matcher.knnMatch(des_photo, des_screen, k=2)
        except:
            return result
    
        match_count = len(matches)
        result.match_count = match_count

        if not matches or match_count == 0:
            return result
    
        # store all the good matches as per Lowe's ratio test.
        good = []
        for m,n in matches:
            if m.distance < 0.75*n.distance:
                good.append(m)
    
        match_count_good = len(good)
        result.match_count_good = match_count_good

        if not good or match_count_good < 20: # AS TODO: magic number as threshold for good matches in ORB algorithm
            return result
    
        photo_pts = np.float32([ kp_photo[m.queryIdx].pt for m in good ]).reshape(-1,1,2) # pylint: disable=too-many-function-args
        screen_pts = np.float32([ kp_screen[m.trainIdx].pt for m in good ]).reshape(-1,1,2) # pylint: disable=too-many-function-args
    
        M, _ = cv2.findHomography(photo_pts, screen_pts, cv2.RANSAC, 5.0)
    
        if M is None or not M.any() or len(M) == 0:
            return result
    
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
    
        if maxX - minX <= 0 or maxY - minY <= 0:
            return False
    
        dimensions = {'width' : maxX - minX, 'height' : maxY - minY}

        result_img = screen_colored[ int(minY):int(maxY), int(minX):int(maxX)]

        retval, buffer = cv2.imencode('.jpg', result_img)
        img_encoded = base64.b64encode(buffer).decode("ASCII")

        result.dimensions = dimensions
        result.result_img = result_img
        result.img_encoded = img_encoded
        result.success = True

        return result
    
    def save_screenshot(self):
        self.log.value_pairs["ts_save_screenshot_start"] = round(time.time() * 1000)
        self.screenshot.save(self.match_dir + '/' + self.screenshot_file)
        self.log.value_pairs["ts_save_screenshot_end"] = round(time.time() * 1000)
