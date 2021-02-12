import React from 'react';
import { Content, Text } from 'native-base';
import { BarCodeScanner } from 'expo-barcode-scanner';

export default class Barcode extends React.Component {

  constructor(props) {
    super(props);
  }

  render(){
    return (
      <Content padded contentContainerStyle={{ flex: 1 }}>
        
        <BarCodeScanner
          onBarCodeScanned={({ type, data }) => this.props.handleBarCodeScanned({ type, data })}
          style={{
            marginTop: 15,
            width: "auto",
            height: "85%",
          }}
          />

        <Text
          style={{
            textAlign: "center",
            padding: 10
          }}
          >
          Pair your device by scanning the provided QR Code
        </Text>

      </Content>
    );
  }
}