import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  Modal,
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Dimensions,
} from 'react-native';
import { Note } from '../types/noteTypes';
import { Theme } from '../styles/theme';

const screenWidth = Dimensions.get('window').width;
const screenHeight = Dimensions.get('window').height;

const PICKER_ITEM_H = 36;
const PICKER_VISIBLE = 3;

function PagePicker({ value, min = 1, max, onChange, theme }: {
  value: number;
  min?: number;
  max: number;
  onChange: (v: number) => void;
  theme: Theme;
}) {
  const listRef = useRef<FlatList>(null);
  const pages = useMemo(
    () => Array.from({ length: max - min + 1 }, (_, i) => min + i),
    [min, max],
  );

  useEffect(() => {
    const offset = (value - min) * PICKER_ITEM_H;
    listRef.current?.scrollToOffset({ offset, animated: false });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [min, max]);

  return (
    <View style={{ height: PICKER_ITEM_H * PICKER_VISIBLE, width: 72, overflow: 'hidden' }}>
      <View
        pointerEvents="none"
        style={{
          position: 'absolute',
          top: PICKER_ITEM_H,
          left: 0, right: 0,
          height: PICKER_ITEM_H,
          backgroundColor: theme.surfaceAlt,
          borderRadius: 8,
        }}
      />
      <FlatList
        ref={listRef}
        data={pages}
        keyExtractor={i => String(i)}
        renderItem={({ item }) => (
          <View style={{ height: PICKER_ITEM_H, justifyContent: 'center', alignItems: 'center' }}>
            <Text style={{ color: theme.text, fontSize: 15, fontWeight: '500' }}>{item}</Text>
          </View>
        )}
        snapToInterval={PICKER_ITEM_H}
        decelerationRate="fast"
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingVertical: PICKER_ITEM_H }}
        onMomentumScrollEnd={e => {
          const idx = Math.round(e.nativeEvent.contentOffset.y / PICKER_ITEM_H);
          onChange(Math.min(Math.max(min + idx, min), max));
        }}
      />
    </View>
  );
}

interface Props {
  note: Note | null;
  theme: Theme;
  onClose: () => void;
}

export default function ExportModal({ note, theme, onClose }: Props) {
  const [format, setFormat] = useState<'pdf' | 'png' | 'note'>('pdf');
  const [pageFrom, setPageFrom] = useState(1);
  const [pageTo, setPageTo] = useState(1);
  const [maxPage, setMaxPage] = useState(1);

  useEffect(() => {
    if (!note) return;
    const max = note.type === 'pdf' ? (note.totalPages ?? note.lastPage ?? 1) : 1;
    setMaxPage(max);
    setPageFrom(1);
    setPageTo(1);
  }, [note]);

  return (
    <Modal visible={!!note} transparent animationType="fade" onRequestClose={onClose}>
      <View style={[styles.overlay, { backgroundColor: theme.overlay }]}>
        <TouchableOpacity style={StyleSheet.absoluteFill} activeOpacity={1} onPress={onClose} />
        <View style={[styles.box, { backgroundColor: theme.surface }]}>
          <Text style={[styles.title, { color: theme.text }]}>Export</Text>

          {/* Format selector */}
          <View style={[styles.section, { borderTopColor: theme.border }]}>
            <Text style={[styles.sectionLabel, { color: theme.textSub }]}>File Format</Text>
            <View style={styles.formatRow}>
              {(['pdf', 'png', 'note'] as const).map(fmt => {
                const active = format === fmt;
                return (
                  <TouchableOpacity
                    key={fmt}
                    style={[styles.formatBtn, { borderColor: theme.border, backgroundColor: active ? theme.text : theme.surfaceAlt }]}
                    onPress={() => setFormat(fmt)}
                  >
                    <Text style={[styles.formatBtnText, { color: active ? theme.surface : theme.textSub }]}>
                      .{fmt}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          </View>

          {/* Page range */}
          <View style={[styles.section, { borderTopColor: theme.border }]}>
            <Text style={[styles.sectionLabel, { color: theme.textSub }]}>Page Range</Text>
            <View style={styles.pageRangeRow}>
              <View style={styles.pageField}>
                <Text style={[styles.pageLabel, { color: theme.textSub }]}>From</Text>
                <PagePicker value={pageFrom} max={pageTo} onChange={setPageFrom} theme={theme} />
              </View>
              <Text style={[styles.pageDash, { color: theme.textSub }]}>–</Text>
              <View style={styles.pageField}>
                <Text style={[styles.pageLabel, { color: theme.textSub }]}>To</Text>
                <PagePicker value={pageTo} min={pageFrom} max={maxPage} onChange={setPageTo} theme={theme} />
              </View>
            </View>
          </View>

          {/* Bottom buttons */}
          <View style={[styles.bottomRow, { borderTopColor: theme.border }]}>
            <TouchableOpacity style={[styles.bottomBtn, { backgroundColor: theme.surfaceAlt }]} onPress={onClose}>
              <Text style={[styles.bottomBtnText, { color: theme.textSub }]}>Cancel</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.bottomBtn, { backgroundColor: theme.text }]} onPress={() => { /* TODO */ }}>
              <Text style={[styles.bottomBtnText, { color: theme.surface }]}>Export</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay:      { flex: 1, justifyContent: 'center', alignItems: 'center' },
  box:          { borderRadius: 14, width: screenWidth * 0.7, height: screenHeight * 0.7, overflow: 'hidden' },
  title:        { fontSize: 17, fontWeight: '700', padding: 20, paddingBottom: 20 },
  section:      { borderTopWidth: 1, paddingHorizontal: 20, paddingTop: 16, paddingBottom: 4 },
  sectionLabel: { fontSize: 12, fontWeight: '600', textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 12 },
  formatRow:    { flexDirection: 'row', gap: 10 },
  formatBtn:    { flex: 1, paddingVertical: 12, borderRadius: 10, borderWidth: 1, alignItems: 'center' },
  formatBtnText:{ fontSize: 15, fontWeight: '600' },
  pageRangeRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  pageField:    { alignItems: 'center', gap: 6 },
  pageLabel:    { fontSize: 12, fontWeight: '500' },
  pageDash:     { fontSize: 18, fontWeight: '300' },
  bottomRow:    { position: 'absolute', bottom: 0, left: 0, right: 0, flexDirection: 'row', gap: 10, padding: 16, borderTopWidth: 1 },
  bottomBtn:    { flex: 1, paddingVertical: 13, borderRadius: 10, alignItems: 'center' },
  bottomBtnText:{ fontSize: 15, fontWeight: '600' },
});
