import pyscreenshot as ImageGrab
import time
import numpy as np
import logging
import os
import sys
import uuid
import io
import base64
import cv2
import timeit
import threading
from cv2 import ( # pylint: disable=no-name-in-module
  perspectiveTransform,
  findHomography,
  RANSAC,
  FlannBasedMatcher,
  imread,
  imwrite,
  IMREAD_COLOR,
  IMREAD_GRAYSCALE,
  DescriptorMatcher_create,
  ORB_create,
)

from cv2.xfeatures2d import ( # pylint: disable=no-name-in-module,import-error
  SURF_create,
)

from common.config import Config


class Matcher():
  
  def __init__(self, match_uid, photo, create_screenshot = True):

    self.match_uid = match_uid

    self.match_dir = './www/results/result-' + match_uid

    self.photo_file = photo

    if create_screenshot:
      self.screenshot_file = 'screenshot.png'
      self.screenshot = ImageGrab.grab()
      # self.screenshot.save(self.match_dir + '/' + self.screenshot_file)
  
  def setMatchDir(self, new_dir):
    self.match_dir = new_dir
  
  def writeLog(self, msg):
    logging.info('MATCHER - {}'.format(msg))
  
  def formatTimeDiff(self, start, end):
    return round( (end - start) * 1000, 2 )

  def match(self, algorithm='SURF'):

    start_time = time.perf_counter()

    # Load pictures

    photo = imread( '{}/{}'.format(self.match_dir, self.photo_file), IMREAD_GRAYSCALE )
    screen = imread( '{}/{}'.format(self.match_dir, self.screenshot_file), IMREAD_GRAYSCALE )
    screen_colored = imread( '{}/{}'.format(self.match_dir, self.screenshot_file), IMREAD_COLOR )

    # Provisional switch statement
    if algorithm == 'SURF':
      match_result = self.algorithm_SURF(photo, screen, screen_colored)
    elif algorithm == 'ORB':
      match_result = self.algorithm_ORB(photo, screen, screen_colored)
    else:
      match_result = self.algorithm_SURF(photo, screen, screen_colored)

    self.writeLog('FINAL TIME {}ms'.format(round( (time.perf_counter() - start_time) * 1000 )))

    return match_result

  def match_b64(self, algorithm='SURF'):
    start_time = time.perf_counter()

    # Load pictures
    nparr = np.frombuffer(base64.b64decode(self.photo_file), np.uint8)
    photo = cv2.imdecode(nparr, cv2.IMREAD_GRAYSCALE)
    screen = cv2.cvtColor(np.array(self.screenshot), IMREAD_GRAYSCALE)
    screen_colored = cv2.cvtColor(np.array(self.screenshot), cv2.COLOR_BGR2RGB)

    # save screenshot in separate thread
    t = threading.Thread(target=self.save_screenshot, args=(), daemon=True)
    t.start()

    # Provisional switch statement
    if algorithm == 'SURF':
      match_result = self.algorithm_SURF(photo, screen, screen_colored)
    elif algorithm == 'ORB':
      match_result = self.algorithm_ORB(photo, screen, screen_colored)
    else:
      match_result = self.algorithm_SURF(photo, screen, screen_colored)  

    self.writeLog('FINAL TIME {}ms'.format(round( (time.perf_counter() - start_time) * 1000 )))

    return match_result

  def algorithm_SURF(self, photo, screen, screen_colored, hessianThreshold = 3500, descMatcher = 1):

    t1 = time.perf_counter()

    # Init algorithm
    surf = SURF_create(hessianThreshold)
    surf.setUpright(True)

    t2 = time.perf_counter()

    self.writeLog('Created SURF object - {}ms'.format( self.formatTimeDiff(t1, t2) ))

    # Detect and compute
    kp_photo, des_photo = surf.detectAndCompute(photo, None)
    kp_screen, des_screen = surf.detectAndCompute(screen, None)

    t3 = time.perf_counter()
    self.writeLog('Detected keypoints - {}ms'.format( self.formatTimeDiff(t2, t3) ))

    # Descriptor Matcher
    try:
      index_params = dict(algorithm = descMatcher, trees = 5)
      search_params = dict(checks = 50)
      flann = FlannBasedMatcher(index_params, search_params)
    except:
      return False

    t4 = time.perf_counter()
    self.writeLog('Initialized Flann Matcher - {}ms'.format( self.formatTimeDiff(t3, t4) ))

    # Calc knn Matches
    try:
      matches = flann.knnMatch(des_photo, des_screen, k=2)
    except:
      return False

    logging.info('knn {}'.format(len(matches)))
    t5 = time.perf_counter()
    self.writeLog('Calced knn matches - {}ms'.format( self.formatTimeDiff(t4, t5) ))

    if not matches or len(matches) == 0:
      return False

    # store all the good matches as per Lowe's ratio test.
    good = []
    for m,n in matches:
      if m.distance < 0.75*n.distance:
        good.append(m)

    logging.info('good {}'.format(len(good)))
    t6 = time.perf_counter()
    self.writeLog('Filtered good matches - {}ms'.format( self.formatTimeDiff(t5, t6) ))

    if not good or len(good) < 10:
      return False


    photo_pts = np.float32([ kp_photo[m.queryIdx].pt for m in good ]).reshape(-1,1,2) # pylint: disable=too-many-function-args
    screen_pts = np.float32([ kp_screen[m.trainIdx].pt for m in good ]).reshape(-1,1,2) # pylint: disable=too-many-function-args

    M, _ = findHomography(photo_pts, screen_pts, RANSAC, 5.0)

    t7 = time.perf_counter()
    self.writeLog('Found Homography - {}ms'.format( self.formatTimeDiff(t6, t7) ))

    if M is None or not M.any() or len(M) == 0:
      return False

    h, w = photo.shape
    pts = np.float32([ [0,0],[0,h-1],[w-1,h-1],[w-1,0] ]).reshape(-1,1,2) # pylint: disable=too-many-function-args
    dst = perspectiveTransform(pts, M)

    t8 = time.perf_counter()
    self.writeLog('Perspective Transform - {}ms'.format( self.formatTimeDiff(t7, t8) ))

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

    logging.info('minY {}'.format(int(minY)))
    logging.info('minX {}'.format(int(minX)))
    logging.info('maxY {}'.format(int(maxY)))
    logging.info('maxX {}'.format(int(maxX)))

    if maxX - minX <= 0:
      return False
    if maxY - minY <= 0:
      return False

    imwrite(self.match_dir + '/result.png', screen_colored[ minY:maxY, minX:maxX])

    t9 = time.perf_counter()
    self.writeLog('Wrote Image - {}ms'.format( self.formatTimeDiff(t8, t9) ))

    return True


  def algorithm_ORB(self, photo, screen, screen_colored, nfeatures = 2000, descriptor_matcher_name = 'BruteForce-Hamming'):
    
    t1 = time.perf_counter()

    # Init algorithm
    orb = ORB_create(nfeatures)

    t2 = time.perf_counter()

    self.writeLog('Created ORB object - {}ms'.format( self.formatTimeDiff(t1, t2) ))

    # Detect and compute
    kp_photo, des_photo = orb.detectAndCompute(photo, None)
    kp_screen, des_screen = orb.detectAndCompute(screen, None)

    t3 = time.perf_counter()
    self.writeLog('Detected keypoints - {}ms'.format( self.formatTimeDiff(t2, t3) ))

    # Descriptor Matcher
    try:
      descriptor_matcher = DescriptorMatcher_create(descriptor_matcher_name)
    except:
      return False

    t4 = time.perf_counter()
    self.writeLog('Initialized Descriptor Matcher - {}ms'.format( self.formatTimeDiff(t3, t4) ))

    # Calc knn Matches
    try:
      matches = descriptor_matcher.knnMatch(des_photo, des_screen, k=2)
    except:
      return False

    t5 = time.perf_counter()
    self.writeLog('Calced knn matches - {}ms'.format( self.formatTimeDiff(t4, t5) ))

    if not matches or len(matches) == 0:
      return False

    # store all the good matches as per Lowe's ratio test.
    good = []
    for m,n in matches:
        if m.distance < 0.75*n.distance:
            good.append(m)

    t6 = time.perf_counter()
    self.writeLog('Filtered good matches - {}ms'.format( self.formatTimeDiff(t5, t6) ))

    if not good or len(good) < 20:
      return False

    photo_pts = np.float32([ kp_photo[m.queryIdx].pt for m in good ]).reshape(-1,1,2) # pylint: disable=too-many-function-args
    screen_pts = np.float32([ kp_screen[m.trainIdx].pt for m in good ]).reshape(-1,1,2) # pylint: disable=too-many-function-args

    M, _ = findHomography(photo_pts, screen_pts, RANSAC, 5.0)

    t7 = time.perf_counter()
    self.writeLog('Found Homography - {}ms'.format( self.formatTimeDiff(t6, t7) ))

    if M is None or not M.any() or len(M) == 0:
      return False

    h, w = photo.shape
    pts = np.float32([ [0,0],[0,h-1],[w-1,h-1],[w-1,0] ]).reshape(-1,1,2) # pylint: disable=too-many-function-args
    dst = perspectiveTransform(pts, M)

    t8 = time.perf_counter()
    self.writeLog('Perspective Transform - {}ms'.format( self.formatTimeDiff(t7, t8) ))


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

    logging.info('minY {}'.format(int(minY)))
    logging.info('minX {}'.format(int(minX)))
    logging.info('maxY {}'.format(int(maxY)))
    logging.info('maxX {}'.format(int(maxX)))

    if maxX - minX <= 0:
      return False
    if maxY - minY <= 0:
      return False

    # imwrite(self.match_dir + '/result.png', screen_colored[ int(minY):int(maxY), int(minX):int(maxX)])

    t9 = time.perf_counter()
    self.writeLog('Wrote Image - {}ms'.format( self.formatTimeDiff(t8, t9) ))
    retval, buffer = cv2.imencode('.jpg', screen_colored[ int(minY):int(maxY), int(minX):int(maxX)])
    b64_string = base64.b64encode(buffer).decode("ASCII")
    return b64_string
  
  def save_screenshot(self):
    self.screenshot.save(self.match_dir + '/' + self.screenshot_file)
