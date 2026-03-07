import React, { useEffect, useRef } from 'react';
import { DeviceEventEmitter, requireNativeComponent, StyleProp, ViewStyle } from 'react-native';

interface NativeProps {
  hue: number;
  sat: number;
  brightness: number;
  style?: StyleProp<ViewStyle>;
}

interface Props extends NativeProps {
  onSVChange:  (sat: number, val: number) => void;
  onHueChange: (hue: number) => void;
}

const NativeColorGradientView = requireNativeComponent<NativeProps>('ColorGradientView');

export default function ColorGradientView({ onSVChange, onHueChange, ...rest }: Props) {
  const svRef  = useRef(onSVChange);
  const hueRef = useRef(onHueChange);
  svRef.current  = onSVChange;
  hueRef.current = onHueChange;

  useEffect(() => {
    const s1 = DeviceEventEmitter.addListener(
      'colorPickerSVChange',
      ({ sat, val }: { sat: number; val: number }) => svRef.current(sat, val),
    );
    const s2 = DeviceEventEmitter.addListener(
      'colorPickerHueChange',
      ({ hue }: { hue: number }) => hueRef.current(hue),
    );
    return () => { s1.remove(); s2.remove(); };
  }, []);

  return <NativeColorGradientView {...rest} />;
}
