import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Note } from '../types/noteTypes';
import HomeScreen from '../screens/HomeScreen';
import PdfViewerScreen from '../screens/PdfViewerScreen';

export type RootStackParamList = {
  Home: undefined;
  PdfViewer: { note: Note };
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function Navigation() {
  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        <Stack.Screen name="Home" component={HomeScreen} />
        <Stack.Screen name="PdfViewer" component={PdfViewerScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
