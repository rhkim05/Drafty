// Shared types between React Native and Kotlin bridge

export type PenColor = string; // hex color e.g. '#000000'

export type ToolMode = 'pen' | 'eraser' | 'select'; // 'select' = hand/scroll mode in PDF viewer

export type EraserMode = 'pixel' | 'stroke'; // 'pixel' = PorterDuff clear, 'stroke' = removes whole stroke

export interface StrokeStyle {
  color: PenColor;
  thickness: number;
}
