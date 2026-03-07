import React, { useState } from 'react';
import { View, TouchableOpacity, Text, StyleSheet, Modal } from 'react-native';
import { useToolStore } from '../store/useToolStore';
import ThicknessSlider from './ThicknessSlider';
import ColorPickerPanel from './ColorPickerPanel';

interface ToolbarProps {
  onUndo?: () => void;
  onRedo?: () => void;
  onToggleStrip?: () => void;
  showHandTool?: boolean;
  currentPage?: number;
  totalPages?: number;
  showStrip?: boolean;
}

export default function Toolbar({ onUndo, onRedo, onToggleStrip, showHandTool, currentPage, totalPages, showStrip }: ToolbarProps) {
  const {
    activeTool, canUndo, canRedo,
    penThickness, eraserThickness,
    penColor, presetColors,
    setTool, setCanUndo: _cu, setCanRedo: _cr,
    setPenThickness, setEraserThickness,
    setPenColor, setPresetColor,
  } = useToolStore();

  const [showColorPicker, setShowColorPicker] = useState(false);
  const [pickerKey, setPickerKey] = useState(0);

  const isPen    = activeTool === 'pen';
  const isEraser = activeTool === 'eraser';
  const isDrawMode = isPen || isEraser;

  return (
    <View style={styles.container}>
      {/* ── Pages strip toggle (leftmost) ── */}
      {onToggleStrip != null && totalPages != null && totalPages > 0 && (
        <>
          <TouchableOpacity
            style={[styles.iconBtn, showStrip && styles.iconBtnActive]}
            onPress={onToggleStrip}
          >
            <View style={[styles.hamburgerLine, showStrip && styles.hamburgerLineActive]} />
            <View style={[styles.hamburgerLine, showStrip && styles.hamburgerLineActive]} />
            <View style={[styles.hamburgerLine, showStrip && styles.hamburgerLineActive]} />
          </TouchableOpacity>
          <View style={styles.divider} />
        </>
      )}

      {/* ── Navigate ── */}
      {showHandTool && (
        <TouchableOpacity
          style={[styles.button, activeTool === 'select' && styles.buttonActive]}
          onPress={() => setTool('select')}
        >
          <Text style={styles.buttonIcon}>✋</Text>
          <Text style={[styles.buttonLabel, activeTool === 'select' && styles.buttonLabelActive]}>
            Navigate
          </Text>
        </TouchableOpacity>
      )}

      <View style={styles.divider} />

      {/* ── Draw tools ── */}
      <TouchableOpacity
        style={[styles.button, isPen && styles.buttonActive]}
        onPress={() => setTool('pen')}
      >
        <Text style={styles.buttonIcon}>✏️</Text>
        <Text style={[styles.buttonLabel, isPen && styles.buttonLabelActive]}>Pen</Text>
      </TouchableOpacity>

      {/* Color swatch — only visible when pen is active */}
      {isPen && (
        <TouchableOpacity
          style={[styles.colorBtn, { backgroundColor: penColor }]}
          onPress={() => { setPickerKey(k => k + 1); setShowColorPicker(v => !v); }}
        />
      )}

      <TouchableOpacity
        style={[styles.button, isEraser && styles.buttonActive]}
        onPress={() => setTool('eraser')}
      >
        <Text style={styles.buttonIcon}>⬜</Text>
        <Text style={[styles.buttonLabel, isEraser && styles.buttonLabelActive]}>Eraser</Text>
      </TouchableOpacity>

      {/* ── Thickness slider (shown when a draw tool is active) ── */}
      {isDrawMode && (
        <>
          <View style={styles.divider} />
          {isPen
            ? <ThicknessSlider
                value={penThickness}
                min={1} max={30}
                color="#1A1A1A"
                onChange={setPenThickness}
              />
            : <ThicknessSlider
                value={eraserThickness}
                min={10} max={100}
                color="eraser"
                onChange={setEraserThickness}
              />
          }
        </>
      )}

      {isDrawMode && (
        <View style={styles.fingerHint}>
          <Text style={styles.fingerHintText}>👆 finger scrolls</Text>
        </View>
      )}

      <View style={styles.spacer} />

      {/* ── Undo / Redo ── */}
      <TouchableOpacity
        style={[styles.button, !canUndo && styles.buttonDisabled]}
        disabled={!canUndo}
        onPress={onUndo}
      >
        <Text style={styles.buttonIcon}>↩️</Text>
        <Text style={[styles.buttonLabel, !canUndo && styles.buttonLabelDisabled]}>Undo</Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.button, !canRedo && styles.buttonDisabled]}
        disabled={!canRedo}
        onPress={onRedo}
      >
        <Text style={styles.buttonIcon}>↪️</Text>
        <Text style={[styles.buttonLabel, !canRedo && styles.buttonLabelDisabled]}>Redo</Text>
      </TouchableOpacity>

      {totalPages != null && totalPages > 0 && (
        <>
          <View style={styles.divider} />
          <Text style={styles.pageIndex}>{currentPage} / {totalPages}</Text>
        </>
      )}

      {/* ── Color picker modal ── */}
      <Modal visible={showColorPicker} transparent animationType="fade" onRequestClose={() => setShowColorPicker(false)}>
        <TouchableOpacity style={StyleSheet.absoluteFill} onPress={() => setShowColorPicker(false)} activeOpacity={1} />
        <View style={styles.pickerAnchor}>
          <ColorPickerPanel
            key={pickerKey}
            color={penColor}
            presetColors={presetColors}
            onColorChange={setPenColor}
            onPresetSave={(i, c) => setPresetColor(i, c)}
          />
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0D8',
    paddingHorizontal: 12,
    paddingVertical: 6,
    gap: 4,
  },
  spacer: { flex: 1 },
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 8,
    gap: 6,
  },
  buttonActive:  { backgroundColor: '#1A1A1A' },
  buttonDisabled: { opacity: 0.3 },
  buttonIcon:  { fontSize: 16 },
  buttonLabel: { fontSize: 13, fontWeight: '500', color: '#1A1A1A' },
  buttonLabelActive:   { color: '#FFFFFF' },
  buttonLabelDisabled: { color: '#1A1A1A' },
  divider: {
    width: 1, height: 24,
    backgroundColor: '#E0E0D8',
    marginHorizontal: 6,
  },
  iconBtn: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    borderRadius: 8,
  },
  iconBtnActive: {
    backgroundColor: '#1A1A1A',
  },
  hamburgerLine: {
    width: 18,
    height: 2,
    borderRadius: 1,
    backgroundColor: '#1A1A1A',
  },
  hamburgerLineActive: {
    backgroundColor: '#FFFFFF',
  },
  colorBtn: {
    width: 28, height: 28,
    borderRadius: 14,
    borderWidth: 2,
    borderColor: '#CCCCCC',
    marginLeft: 2,
  },
  pickerAnchor: {
    position: 'absolute',
    top: 56,   // below header + toolbar
    left: 140, // roughly under the pen/color area
  },
  fingerHint: {
    marginLeft: 4,
    paddingHorizontal: 8,
    paddingVertical: 4,
    backgroundColor: '#F0F0EA',
    borderRadius: 6,
  },
  fingerHintText: { fontSize: 11, color: '#888' },
  pageIndex: {
    fontSize: 13,
    fontWeight: '600',
    color: '#444',
    paddingHorizontal: 8,
  },
});
