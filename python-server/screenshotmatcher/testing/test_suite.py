import os
import tempfile
import time
import matplotlib.pyplot as plt
import numpy as np

from cv2 import ( # pylint: disable=no-name-in-module
  imread,
  imwrite,
  IMREAD_COLOR,
  IMREAD_GRAYSCALE,
)

from matching.matcher import Matcher
from common.config import Config

class TestSuite():

  def __init__(self, window):
    print('testsuite initialized')
    self.here = os.path.dirname(os.path.realpath(__file__))
    self.tmp = tempfile.gettempdir()
    self.window = window

  def plot(self, data, xlabel, ylabel, title, with_legend = True):
    fig, ax = plt.subplots()

    for dataset in data:
      ax.plot(dataset['x'], dataset['y'], '-D', label=dataset['name'], markevery=dataset['errors'], markerfacecolor='red', markersize='10')

    plt.ylabel(ylabel)
    plt.xlabel(xlabel)
    plt.title(title)

    if with_legend:
      plt.legend()

    plt.show(block=False)


  def test_SURF_hessian(self, values):

    min_hessian = int(values[10])
    max_hessian = int(values[11])
    loop_step = int(values[12])
    dm_algo = int(values[13])

    now = time.time_ns()
    match_uid = 'screenshotmatcher-test'
    match_dir = f'{self.tmp}/{match_uid}'

    if not os.path.exists(match_dir):
      os.mkdir(match_dir)

    matcher = Matcher(match_uid, '', False)
    matcher.setMatchDir(match_dir)

    plot_data = []
    match_count = 0
    successful_matches = 0

    for j in range(10):

      if not values[j]:
        continue

      img_index = j + 1

      photo = imread( f'{self.here}/images/photo{img_index}.jpg', IMREAD_GRAYSCALE )
      screen = imread( f'{self.here}/images/screenshot{img_index}.png', IMREAD_GRAYSCALE )
      screen_colored = imread( f'{self.here}/images/screenshot{img_index}.png', IMREAD_COLOR )

      threshold_dataset = []
      time_dataset = []
      error_dataset = []

      error_index = 0
      for i in range(min_hessian, max_hessian + 1, loop_step):
        match_count += 1
        start_time = time.perf_counter()
        match_result = matcher.algorithm_SURF(photo, screen, screen_colored, i, dm_algo)
        time_diff = round(time.perf_counter() - start_time, 5)
        print( '{} - {} - Threshold {} - {}s'.format(j, match_result, i, time_diff ) )
        self.window.Refresh()
        
        with open(f'{self.here}/logs/SURF_hessian_{now}.log', 'a') as f:
          f.write('{};{};{};{}\n'.format(j, match_result, i, time_diff ))
        
        threshold_dataset.append(i)
        time_dataset.append(time_diff)

        if match_result:
          successful_matches += 1
        else:
          error_dataset.append(error_index)
        
        error_index += 1
      
      new_plot_data = { 
        'x': threshold_dataset,
        'y': time_dataset,
        'errors': error_dataset,
        'name': f'Photo {img_index}'
      }
      plot_data.append(new_plot_data)

    success_rate = round( (successful_matches / match_count) * 100, 2)
    print(f'Successful: {success_rate}% - {successful_matches}/{match_count} ')
    self.plot(plot_data, 'Threshold', 'Time in seconds', 'Optimal Hessian Threshold Parameter (SURF)')


  def test_SURF_algos(self, values):

    min_algo = int(values[14])
    max_algo = int(values[15])
    threshold = int(values[16])

    now = time.time_ns()

    match_uid = 'screenshotmatcher-test'
    match_dir = f'{self.tmp}/{match_uid}'

    if not os.path.exists(match_dir):
      os.mkdir(match_dir)

    matcher = Matcher(match_uid, '', False)
    matcher.setMatchDir(match_dir)

    plot_data = []
    match_count = 0
    successful_matches = 0

    for j in range(10):

      if not values[j]:
        continue

      img_index = j + 1

      photo = imread( f'{self.here}/images/photo{img_index}.jpg', IMREAD_GRAYSCALE )
      screen = imread( f'{self.here}/images/screenshot{img_index}.png', IMREAD_GRAYSCALE )
      screen_colored = imread( f'{self.here}/images/screenshot{img_index}.png', IMREAD_COLOR )

      time_dataset = []
      algo_dataset = []
      error_dataset = []

      error_index = 0
      for i in range(min_algo, max_algo + 1, 1):
        match_count += 1
        start_time = time.perf_counter()
        match_result = matcher.algorithm_SURF(photo, screen, screen_colored, threshold, i)
        time_diff = round(time.perf_counter() - start_time, 5)
        print( '{} - Algo {} - {}s'.format(match_result, i, time_diff ) )
        self.window.Refresh()
        
        with open(f'{self.here}/logs/SURF_algos_{now}.log', 'a') as f:
          f.write('{};{};{}\n'.format(match_result, i, time_diff ))
        
        algo_dataset.append(i)
        time_dataset.append(time_diff)

        if match_result:
          successful_matches += 1
        else:
          error_dataset.append(error_index)
        
        error_index += 1
      
      new_plot_data = { 
        'x': algo_dataset,
        'y': time_dataset,
        'errors': error_dataset,
        'name': f'Photo {img_index}'
      }
      plot_data.append(new_plot_data)

    success_rate = round( (successful_matches / match_count) * 100, 2)
    print(f'Successful: {success_rate}% - {successful_matches}/{match_count} ')
    self.plot(plot_data, 'Algorithm', 'Time in seconds', 'Optimal Descriptor Matcher Parameter (SURF)', False)

    return



  def test_ORB_nfeatures(self, values):

    min_nfeatures = int(values[17])
    max_nfeatures = int(values[18])
    loop_step = int(values[19])

    now = time.time_ns()
    match_uid = 'screenshotmatcher-test'
    match_dir = f'{self.tmp}/{match_uid}'

    if not os.path.exists(match_dir):
      os.mkdir(match_dir)

    matcher = Matcher(match_uid, '', False)
    matcher.setMatchDir(match_dir)

    plot_data = []
    match_count = 0
    successful_matches = 0

    for j in range(10):

      if not values[j]:
        continue

      img_index = j + 1

      photo = imread( f'{self.here}/images/photo{img_index}.jpg', IMREAD_GRAYSCALE )
      screen = imread( f'{self.here}/images/screenshot{img_index}.png', IMREAD_GRAYSCALE )
      screen_colored = imread( f'{self.here}/images/screenshot{img_index}.png', IMREAD_COLOR )

      nfeatures_dataset = []
      error_dataset = []
      time_dataset = []

      error_index = 0
      for i in range(min_nfeatures, max_nfeatures + 1, loop_step):
        match_count += 1
        start_time = time.perf_counter()
        match_result = matcher.algorithm_ORB(photo, screen, screen_colored, i)
        time_diff = round(time.perf_counter() - start_time, 5)
        print( '{} - {} - nFeatures {} - {}s'.format(j, match_result, i, time_diff ) )
        self.window.Refresh()
        
        with open(f'{self.here}/logs/ORB_nfeatures_{now}.log', 'a') as f:
          f.write('{};{};{};{}\n'.format(j, match_result, i, time_diff ))
        
        nfeatures_dataset.append(i)
        time_dataset.append(time_diff)

        if match_result:
          successful_matches += 1
        else:
          error_dataset.append(error_index)
        
        error_index += 1

      new_plot_data = { 
        'x': nfeatures_dataset,
        'y': time_dataset,
        'errors': error_dataset,
        'name': f'Photo {img_index}'
      }
      plot_data.append(new_plot_data)

    success_rate = round( (successful_matches / match_count) * 100, 2)
    print(f'Successful: {success_rate}% - {successful_matches}/{match_count} ')
    self.plot(plot_data, 'nFeatures', 'Time in seconds', 'Optimal nFeatures Parameter (ORB)')