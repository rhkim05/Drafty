import React, { forwardRef, useEffect } from 'react';
import { DeviceEventEmitter, requireNativeComponent, StyleProp, ViewStyle } from 'react-native';
import { useToolStore } from '../store/useToolStore';

interface PdfCanvasViewProps {
  pdfUri?: string;
  tool: string;
  penColor: string;
  penThickness: number;
  eraserThickness: number;
  eraserMode: string;
  style?: StyleProp<ViewStyle>;
  onLayout?: () => void;
}

const NativePdfCanvasView = requireNativeComponent<PdfCanvasViewProps>('PdfCanvasView');

const PdfCanvasView = forwardRef<any, PdfCanvasViewProps>((props, ref) => {
  const { setCanUndo, setCanRedo } = useToolStore();

  useEffect(() => {
    const sub = DeviceEventEmitter.addListener(
      'canvasUndoRedoState',
      ({ canUndo, canRedo }: { canUndo: boolean; canRedo: boolean }) => {
        setCanUndo(canUndo);
        setCanRedo(canRedo);
      },
    );
    return () => sub.remove();
  }, [setCanUndo, setCanRedo]);

  return <NativePdfCanvasView ref={ref} {...props} />;
});

export default PdfCanvasView;
