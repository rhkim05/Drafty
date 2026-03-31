import { NativeModules } from 'react-native';

const { UnifiedCanvasModule } = NativeModules;

export default {
  undo: (viewTag: number) => UnifiedCanvasModule.undo(viewTag),
  redo: (viewTag: number) => UnifiedCanvasModule.redo(viewTag),
  clear: (viewTag: number) => UnifiedCanvasModule.clear(viewTag),
  getStrokes: (viewTag: number): Promise<string> => UnifiedCanvasModule.getStrokes(viewTag),
  loadStrokes: (viewTag: number, json: string) => UnifiedCanvasModule.loadStrokes(viewTag, json),
  scrollToPage: (viewTag: number, page: number) => UnifiedCanvasModule.scrollToPage(viewTag, page),
  getPageCount: (viewTag: number): Promise<number> => UnifiedCanvasModule.getPageCount(viewTag),
  getPageCountFromFile: (filePath: string): Promise<number> => UnifiedCanvasModule.getPageCountFromFile(filePath),
  addPage: (viewTag: number) => UnifiedCanvasModule.addPage(viewTag),
  prependPage: (viewTag: number) => UnifiedCanvasModule.prependPage(viewTag),
};
