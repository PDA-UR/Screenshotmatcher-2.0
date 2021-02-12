import React from 'react';
import { Image, ToastAndroid } from 'react-native';
import { Button, Content, Text, Grid, Col } from 'native-base';

import { Linking } from "expo";

import * as Sharing from 'expo-sharing';

import Feedback from './Feedback';

import Api from '../services/Api';

export default class Result extends React.Component {

  constructor(props) {
    super(props);

    this.state = {
      isSharing: false,
      isSaving: false,
    }

  }

  async download(){

    this.setState({ isSaving: true })

    await Api.saveFile(this.props.resultUri)

    this.setState({ isSaving: false })

    ToastAndroid.showWithGravity('Saved to album "ScreenshotMatcher"', ToastAndroid.LONG, ToastAndroid.CENTER);
  }

  async openGallery(){
    Linking.openURL("content://media/internal/images/media");
  }

  async share(){

    this.setState({ isSharing: true })

    // check if sharing is available
    if (!(await Sharing.isAvailableAsync())) {
      alert(`Uh oh, sharing isn't available on your platform`);
      return
    }
     
     // share picture
     await Sharing.shareAsync(this.props.resultUri);

     this.setState({ isSharing: false })
  }

  render(){
    return (
      <Content padded contentContainerStyle={{ flex: 1, justifyContent: 'center' }}>

        <Feedback uid={this.props.uid} hasResult={this.props.hasResult} hasScreenshot={this.props.hasScreenshot}  />

        {
          this.props.resultUri ? (
            <Image
              resizeMode={'contain'}
              style={{ width: 'auto', height: '45%', margin: 20 }}
              source={{isStatic: true, uri: this.props.resultUri}}
            />
          ) : (
            <Text style={{ width: 'auto', height: '45%', margin: 20, textAlign: 'center' }}>No result available</Text>
          )
        }

        { 
          this.state.isSharing ? (
            <Button style={{marginHorizontal: 20, marginVertical: 10}} block disabled >
              <Text>Sharing...</Text>
            </Button>
          ) : (
            <Button style={{marginHorizontal: 20, marginVertical: 10}} block onPress={() => this.share()} >
              <Text>Share</Text>
            </Button>
          )
        }

        <Grid style={{marginHorizontal: 20, marginTop: 10, marginBottom: 0}}>
          <Col>
            { 
              this.state.isSaving ? (
                <Button style={{marginRight: 10}} block disabled >
                  <Text style={{ textAlign:'center' }}>Saving...</Text>
                </Button>
              ) : (
                <Button style={{marginRight: 10 }} block onPress={() => this.download()} >
                  <Text style={{ textAlign:'center' }}>Save to Gallery</Text>
                </Button>
              )
            }
          </Col>
          <Col>
            <Button style={{marginLeft: 10}} block onPress={() => this.openGallery()} >
              <Text style={{ textAlign:'center' }}>Open Gallery</Text>
            </Button>
          </Col>
        </Grid>



      </Content>
    );
  }
}