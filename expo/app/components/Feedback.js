import React from 'react';
import { ToastAndroid } from 'react-native';
import { Button, Text, View, Item, Input } from 'native-base';

import Modal from 'react-native-modal';
import Api from '../services/Api';

export default class Feedback extends React.Component {

  constructor(props) {
    super(props);

    this.state = {
      isSubmitting: false,
      isModalVisible: false,
      comment: '',
      feedbackSent: false,
    }

    this.handleChange = this.handleChange.bind(this);
    this.handleSubmit = this.handleSubmit.bind(this);
  }
 
  toggleModal = () => {
    this.setState({isModalVisible: !this.state.isModalVisible});
  };

  handleChange(val) {
    this.setState({comment: val});
  }

  async handleSubmit() {
    this.setState({isSubmitting: true});

    const fbResult = await Api.sendFeedback(this.props.uid, this.props.hasResult, this.props.hasScreenshot, this.state.comment)

    if(fbResult){
      this.setState({isSubmitting: false, feedbackSent: true});
      console.log('Feedback sent!')
      this.toggleModal()
    }
    else{
      ToastAndroid.showWithGravity('Error while posting feedback', ToastAndroid.LONG, ToastAndroid.CENTER);
      this.setState({isSubmitting: false});
    }
  }

  render(){
    return (
      <View>
        <Button style={{marginHorizontal: 20, marginVertical: 10}} block onPress={this.toggleModal}>
          <Text>Send Feedback</Text>
        </Button>
        <Modal isVisible={this.state.isModalVisible} onBackButtonPress={this.toggleModal} style={{height:"auto"}}>
          <View style={{flex: 1, justifyContent: "center", padding: 10, textAlign: "center", backgroundColor: "white"}}>
            <Text style={{textAlign:"center", fontWeight:"bold", fontSize: 22}}>Send Feedback</Text>
            <Text style={{textAlign:"center", marginBottom: 10}}>Match UID: {this.props.uid}</Text>
            <Text style={{textAlign:"center", marginBottom: 10}}>{this.props.hasResult ? 'Has result' : 'No result'}</Text>
            {
              this.props.hasScreenshot ? (
                <Text style={{textAlign:"center", marginBottom: 10}}>Used screenshot as alternative</Text>
              ) : (
                <></>
              )
            }
            <Item style={{marginVertical: 10}}>
              <Input 
                style={{textAlign:"center"}} 
                placeholder="Comment (optional)" 
                value={this.state.comment} 
                onChangeText={(val) => this.handleChange(val)} />
            </Item>
            { 
              this.state.feedbackSent ? (
                <Button style={{marginHorizontal: 20, marginVertical: 10}} block disabled>
                  <Text>Feedback sent!</Text>
                </Button>
              ) : (
                this.state.isSubmitting ? (
                  <Button style={{marginHorizontal: 20, marginVertical: 10}} block disabled>
                    <Text>Submitting..</Text>
                  </Button>
                ) : (
                  <Button style={{marginHorizontal: 20, marginVertical: 10}} block onPress={this.handleSubmit}>
                    <Text>Submit Feedback</Text>
                  </Button>
                )
              )
            }
            <Button style={{marginHorizontal: 20, marginVertical: 10}} block  onPress={this.toggleModal} >
              <Text>Close</Text>
            </Button>
          </View>
        </Modal>
      </View>
    );
  }
}