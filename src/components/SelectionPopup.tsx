import React from 'react';
import { View, TouchableOpacity, Text, StyleSheet, Share } from 'react-native';
import { useTheme } from '../styles/theme';
import CanvasModule from '../native/CanvasModule';
import { SelectionInfo } from '../native/CanvasView';

interface Props {
  info: SelectionInfo;
  viewTag: number;
}

export default function SelectionPopup({ info, viewTag }: Props) {
  const theme = useTheme();
  if (!info.hasSelection) return null;

  // Position popup below the selection bounding box, clamped to screen
  const popupLeft = info.bounds.x;
  const popupTop  = info.bounds.y + info.bounds.height + 12;

  const handleDelete = () => {
    CanvasModule.deleteSelected(viewTag);
  };

  const handleCut = async () => {
    await CanvasModule.cutSelected(viewTag);
    // JSON of cut strokes available here for future paste support
  };

  const handleCapture = async () => {
    const filePath = await CanvasModule.captureSelected(viewTag);
    if (filePath) {
      await Share.share({ url: `file://${filePath}`, title: 'Captured selection' });
    }
  };

  return (
    <View
      pointerEvents="box-none"
      style={[styles.popup, {
        top: popupTop,
        left: popupLeft,
        backgroundColor: theme.surface,
        borderColor: theme.border,
      }]}
    >
      <ActionButton icon="✂️" label="Cut"     onPress={handleCut}     theme={theme} />
      <Separator theme={theme} />
      <ActionButton icon="🗑️" label="Delete"  onPress={handleDelete}  theme={theme} />
      <Separator theme={theme} />
      <ActionButton icon="📷" label="Capture" onPress={handleCapture} theme={theme} />
      <Separator theme={theme} />
      <ActionButton icon="⤢"  label="Resize"  onPress={() => {/* resize via Kotlin handles */}} theme={theme} hint="Drag corner handles" />
    </View>
  );
}

function ActionButton({ icon, label, onPress, theme, hint }: {
  icon: string; label: string; onPress: () => void; theme: any; hint?: string;
}) {
  return (
    <TouchableOpacity style={styles.action} onPress={onPress} activeOpacity={0.7}>
      <Text style={styles.actionIcon}>{icon}</Text>
      <View>
        <Text style={[styles.actionLabel, { color: theme.text }]}>{label}</Text>
        {hint && <Text style={[styles.actionHint, { color: theme.textHint }]}>{hint}</Text>}
      </View>
    </TouchableOpacity>
  );
}

function Separator({ theme }: { theme: any }) {
  return <View style={[styles.sep, { backgroundColor: theme.border }]} />;
}

const styles = StyleSheet.create({
  popup: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 8,
    paddingVertical: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.18,
    shadowRadius: 10,
    elevation: 8,
  },
  action: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 4,
    gap: 6,
  },
  actionIcon:  { fontSize: 18 },
  actionLabel: { fontSize: 13, fontWeight: '500' },
  actionHint:  { fontSize: 10, marginTop: 1 },
  sep: {
    width: 1,
    height: 28,
  },
});
