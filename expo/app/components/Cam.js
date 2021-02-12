import React from 'react';
import { Button, Content, Icon, Text } from 'native-base';

import { Camera } from 'expo-camera';

export default class Cam extends React.Component {

  constructor(props) {
    super(props);
  }

  render(){
    return (
      <Content padded contentContainerStyle={{ flex: 1 }}>
        
        <Camera 
          style={{ flex: 1 }} 
          type={Camera.Constants.Type.back}
          ref={ref => { 
            this.camera = ref;
            this.props.initCamera(this.camera);
          }}
          />

        {this.props.isPhotoLoading ? (
          <Button full large disabled>
            <Text>{this.props.loadingMsg}</Text>
          </Button>
        ) : (
          <Button full large onPress={() => this.props.handlePictureBtnPress()} >
            <Icon name='md-camera' />
          </Button>
        )}
            

      </Content>
    );
  }
}