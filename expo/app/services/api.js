import { AsyncStorage } from 'react-native';
import * as MediaLibrary from 'expo-media-library';
import * as FileSystem from 'expo-file-system';

import { modelName, osName, osVersion } from 'expo-device';

// https://stackoverflow.com/a/54208009
async function fetchWithTimeout(url, options, timeout = 5000) {
  return Promise.race([
      fetch(url, options),
      new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout')), timeout))
  ]);
}

class Api {
  
  constructor() {
    if (Api.instance) {
      return Api.instance;
    }
    Api.instance = this;
  }

  async init(){

    this.deviceStr =  `${modelName} with ${osName} ${osVersion}`

    try {
      const addr = await AsyncStorage.getItem('apiAddress');
      if (addr !== null) { 
        await this.setUrl(addr)
        return addr
      }
    } catch (error) {
      console.error(error)
    }
    return false
  }
  
  async setUrl(url){
    this.baseUrl = url;
    try {
      await AsyncStorage.setItem('apiAddress', this.baseUrl);
    } catch (error) {
      console.error(error)
    }
  }

  handleResponse(response) {
    if (response.ok) {
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.indexOf('application/json') !== -1)
        return response.json();
      return response.text();
    }
    throw response;
  }
  
  handleError(response) {
    console.log(
      'API error:',
      response.status,
      response.statusText,
      response.url
    );
    throw new Error(response.status);
  }

  call(endpoint){
    return fetchWithTimeout(this.baseUrl + endpoint)
      .then(this.handleResponse)
      .catch(this.handleError);
  }

  callPOST(endpoint, data){
    return fetchWithTimeout(this.baseUrl + endpoint, {
      method: 'POST',
      body: data
    }, 15000) // increased timeout of 15 seconds for POST
      .then(this.handleResponse)
      .catch(this.handleError);
  }

  heartbeat(){
    if(!this.baseUrl) return false;

    return this.call('/heartbeat')
      .then(response => { return (response === 'ok'); })
      .catch(error => { console.log('Error with Heartbeat. Is the server online?'); });
  }

  postImage(picture){
    if(!this.baseUrl) return false;

    const body = new FormData()

    body.append('device', this.deviceStr)

    body.append('image_file.jpg', {
      uri: picture.uri,
      type: 'image/jpeg',
      name: 'image_file.jpg'
    })

    return this.callPOST('/match', body)
      .then(response => response)
      .catch(error => { console.log('Error with Posting. Is the server online?'); });
  }

  async downloadFile(uid, url){
    if(!this.baseUrl) return false;

    const uri = this.baseUrl + url;

    var filename = url.split("/").pop();

    let fileUri = FileSystem.cacheDirectory + uid + filename;

    const result = await FileSystem.downloadAsync(uri, fileUri)

    return result && result.uri
  }

  async saveFile(fileUri){
    const asset = await MediaLibrary.createAssetAsync(fileUri)
    const album = await MediaLibrary.createAlbumAsync("ScreenshotMatcher", asset, false)
    return asset.uri
  }

  async sendFeedback(uid, hasResult, hasScreenshot, comment = ''){
    console.log('Sending feedback', uid)
    const body = new FormData()

    body.append('uid', uid)
    body.append('hasResult', hasResult)
    body.append('hasScreenshot', hasScreenshot)
    body.append('comment', comment)
    body.append('device', this.deviceStr)

    return this.callPOST('/feedback', body)
      .then(response => response)
      .catch(error => { console.log('Error with Feedback. Is the server online?'); });
  }

}

export default new Api();