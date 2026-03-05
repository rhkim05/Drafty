import { create } from 'zustand';
import { ToolMode } from '../types/canvasTypes';

interface ToolState {
  activeTool: ToolMode;
  canUndo: boolean;
  canRedo: boolean;
  setTool: (tool: ToolMode) => void;
  setCanUndo: (value: boolean) => void;
  setCanRedo: (value: boolean) => void;
}

export const useToolStore = create<ToolState>(set => ({
  activeTool: 'pen',
  canUndo: false,
  canRedo: false,
  setTool: (tool) => set({ activeTool: tool }),
  setCanUndo: (value) => set({ canUndo: value }),
  setCanRedo: (value) => set({ canRedo: value }),
}));
