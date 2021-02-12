import React from 'react';
import { Header, Title, Button, Left, Right, Body, Icon, Spinner } from 'native-base';
import { MaterialCommunityIcons } from '@expo/vector-icons';

export default class Head extends React.Component {

  constructor(props) {
    super(props);
  }

  renderStatusText(){

    if(this.props.hasResult || this.props.hasScreenshot){
      return (
        <Left>
          <Button transparent onPress={() => this.props.resetResult()}>
            <Icon name='ios-arrow-back' />
          </Button>
        </Left>
      )
    }
  
    if(this.props.isUpdating){
      return (
        <Body style={{ paddingLeft: 5 }}>
          <Spinner color="white" />
        </Body>
      )
    }
    else if(this.props.paired){
      return (
        <Body style={{ paddingLeft: 5 }}>
          <Title>Connected</Title>
        </Body>
      )
    }
    else{
      return (
        <Body style={{ paddingLeft: 5 }}>
          <Title>Not connected!</Title>
        </Body>
      )
    }

  }

  render(){
    return (
      <Header>
  
        { this.renderStatusText() }
  
        <Right>
          <Button transparent onPress={this.props.startPairing}>
            <MaterialCommunityIcons name="qrcode-scan" size={24} color="white" />
          </Button>
          <Button transparent onPress={this.props.updateStatus}>
            <Icon name='md-refresh' />
          </Button>
        </Right>
  
      </Header>
    );
  }
}