import React, { useState, useRef } from 'react';
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
    penThickness, eraserThickness, eraserMode,
    penColor, presetColors,
    setTool, setCanUndo: _cu, setCanRedo: _cr,
    setPenThickness, setEraserThickness, setEraserMode,
    setPenColor, setPresetColor,
  } = useToolStore();

  const [penExpanded, setPenExpanded] = useState(false);
  const [showColorPicker, setShowColorPicker] = useState(false);
  const [pickerKey, setPickerKey] = useState(0);
  const [popupPos, setPopupPos] = useState({ top: 60, left: 0 });

  const [eraserExpanded, setEraserExpanded] = useState(false);
  const [eraserPopupPos, setEraserPopupPos] = useState({ top: 60, left: 0 });

  const penBtnRef    = useRef<TouchableOpacity>(null);
  const eraserBtnRef = useRef<TouchableOpacity>(null);

  const isPen    = activeTool === 'pen';
  const isEraser = activeTool === 'eraser';

  const closePenPopup = () => {
    setPenExpanded(false);
    setShowColorPicker(false);
  };

  const closeEraserPopup = () => setEraserExpanded(false);

  const handleEraserPress = () => {
    if (isEraser) {
      if (!eraserExpanded) {
        eraserBtnRef.current?.measureInWindow((x, y, _w, h) => {
          setEraserPopupPos({ top: y + h + 6, left: x });
        });
      }
      setEraserExpanded(v => !v);
    } else {
      setTool('eraser');
      closeEraserPopup();
      closePenPopup();
    }
  };

  const handlePenPress = () => {
    if (isPen) {
      if (!penExpanded) {
        penBtnRef.current?.measureInWindow((x, y, _w, h) => {
          setPopupPos({ top: y + h + 6, left: x });
        });
      }
      setPenExpanded(v => !v);
      setShowColorPicker(false);
    } else {
      setTool('pen');
      closePenPopup();
      closeEraserPopup();
    }
  };

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
          onPress={() => { setTool('select'); closePenPopup(); closeEraserPopup(); }}
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
        ref={penBtnRef}
        style={[styles.button, isPen && styles.buttonActive]}
        onPress={handlePenPress}
      >
        <Text style={styles.buttonIcon}>✏️</Text>
        <Text style={[styles.buttonLabel, isPen && styles.buttonLabelActive]}>Pen</Text>
      </TouchableOpacity>

      <TouchableOpacity
        ref={eraserBtnRef}
        style={[styles.button, isEraser && styles.buttonActive]}
        onPress={handleEraserPress}
      >
        <Text style={styles.buttonIcon}>⬜</Text>
        <Text style={[styles.buttonLabel, isEraser && styles.buttonLabelActive]}>Eraser</Text>
      </TouchableOpacity>

      {(isPen || isEraser) && (
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

      {/* ── Pen options popup ── */}
      <Modal
        visible={isPen && penExpanded}
        transparent
        animationType="fade"
        onRequestClose={closePenPopup}
      >
        <TouchableOpacity style={StyleSheet.absoluteFill} onPress={closePenPopup} activeOpacity={1} />

        {/* Color swatch + thickness card */}
        <View style={[styles.penPopup, { top: popupPos.top, left: popupPos.left }]}>
          <TouchableOpacity
            style={[styles.colorBtn, { backgroundColor: penColor }]}
            onPress={() => { setPickerKey(k => k + 1); setShowColorPicker(v => !v); }}
          />
          <View style={styles.popupDivider} />
          <ThicknessSlider
            value={penThickness}
            min={1} max={30}
            color="#1A1A1A"
            onChange={setPenThickness}
          />
        </View>

        {/* Color picker — shown below the card when color swatch is tapped */}
        {showColorPicker && (
          <View style={[styles.pickerAnchor, { top: popupPos.top + 52, left: popupPos.left }]}>
            <ColorPickerPanel
              key={pickerKey}
              color={penColor}
              presetColors={presetColors}
              onColorChange={setPenColor}
              onPresetSave={(i, c) => setPresetColor(i, c)}
            />
          </View>
        )}
      </Modal>

      {/* ── Eraser options popup ── */}
      <Modal
        visible={isEraser && eraserExpanded}
        transparent
        animationType="fade"
        onRequestClose={closeEraserPopup}
      >
        <TouchableOpacity style={StyleSheet.absoluteFill} onPress={closeEraserPopup} activeOpacity={1} />

        <View style={[styles.penPopup, { top: eraserPopupPos.top, left: eraserPopupPos.left }]}>
          {/* Mode selector */}
          <TouchableOpacity
            style={[styles.eraserModeBtn, eraserMode === 'pixel' && styles.eraserModeBtnActive]}
            onPress={() => setEraserMode('pixel')}
          >
            <Text style={[styles.eraserModeText, eraserMode === 'pixel' && styles.eraserModeTextActive]}>
              픽셀
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.eraserModeBtn, eraserMode === 'stroke' && styles.eraserModeBtnActive]}
            onPress={() => setEraserMode('stroke')}
          >
            <Text style={[styles.eraserModeText, eraserMode === 'stroke' && styles.eraserModeTextActive]}>
              획
            </Text>
          </TouchableOpacity>
          <View style={styles.popupDivider} />
          <ThicknessSlider
            value={eraserThickness}
            min={10} max={100}
            color="eraser"
            onChange={setEraserThickness}
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
  // Pen popup
  penPopup: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 8,
    elevation: 6,
    borderWidth: 1,
    borderColor: '#E0E0D8',
  },
  popupDivider: {
    width: 1,
    height: 20,
    backgroundColor: '#E0E0D8',
  },
  pickerAnchor: {
    position: 'absolute',
  },
  eraserModeBtn: {
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 7,
    borderWidth: 1,
    borderColor: '#D0D0C8',
  },
  eraserModeBtnActive: {
    backgroundColor: '#1A1A1A',
    borderColor: '#1A1A1A',
  },
  eraserModeText: {
    fontSize: 12,
    fontWeight: '500',
    color: '#444',
  },
  eraserModeTextActive: {
    color: '#FFFFFF',
  },
});
