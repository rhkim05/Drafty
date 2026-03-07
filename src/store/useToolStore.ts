import { create } from 'zustand';
import { ToolMode } from '../types/canvasTypes';

const DEFAULT_PRESETS = [
  '#000000', '#E53935', '#FB8C00', '#FDD835', '#43A047',
  '#039BE5', '#1E88E5', '#8E24AA', '#D81B60', '#FFFFFF',
];

interface ToolState {
  activeTool: ToolMode;
  canUndo: boolean;
  canRedo: boolean;
  penThickness: number;
  eraserThickness: number;
  penColor: string;
  presetColors: string[];
  setTool: (tool: ToolMode) => void;
  setCanUndo: (value: boolean) => void;
  setCanRedo: (value: boolean) => void;
  setPenThickness: (value: number) => void;
  setEraserThickness: (value: number) => void;
  setPenColor: (color: string) => void;
  setPresetColor: (index: number, color: string) => void;
}

export const useToolStore = create<ToolState>(set => ({
  activeTool: 'pen',
  canUndo: false,
  canRedo: false,
  penThickness: 4,
  eraserThickness: 24,
  penColor: '#000000',
  presetColors: DEFAULT_PRESETS,
  setTool: (tool) => set({ activeTool: tool }),
  setCanUndo: (value) => set({ canUndo: value }),
  setCanRedo: (value) => set({ canRedo: value }),
  setPenThickness: (value) => set({ penThickness: value }),
  setEraserThickness: (value) => set({ eraserThickness: value }),
  setPenColor: (color) => set({ penColor: color }),
  setPresetColor: (index, color) => set(state => {
    const updated = [...state.presetColors];
    updated[index] = color;
    return { presetColors: updated };
  }),
}));
