import React from 'react';
import { DarkTheme, DefaultTheme, NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Note } from '../types/noteTypes';
import HomeScreen from '../screens/HomeScreen';
import EditorScreen from '../screens/EditorScreen';
import { useSettingsStore } from '../store/useSettingsStore';

export type RootStackParamList = {
  Home: undefined;
  Editor: { note: Note };
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function Navigation() {
  const isDarkMode = useSettingsStore(s => s.isDarkMode);

  return (
    <NavigationContainer theme={isDarkMode ? DarkTheme : DefaultTheme}>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        <Stack.Screen name="Home" component={HomeScreen} />
        <Stack.Screen name="Editor" component={EditorScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
