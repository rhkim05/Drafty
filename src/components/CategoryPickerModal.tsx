import React from 'react';
import { Modal, View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Note } from '../types/noteTypes';
import { Category } from '../types/categoryTypes';
import { Theme } from '../styles/theme';

interface Props {
  note: Note | null;
  categories: Category[];
  theme: Theme;
  onToggleCategory: (noteId: string, categoryId: string, isAdding: boolean) => void;
  onClose: () => void;
}

export default function CategoryPickerModal({ note, categories, theme, onToggleCategory, onClose }: Props) {
  return (
    <Modal visible={!!note} transparent animationType="fade" onRequestClose={onClose}>
      <TouchableOpacity style={[styles.overlay, { backgroundColor: theme.overlay }]} activeOpacity={1} onPress={onClose}>
        <View style={[styles.box, { backgroundColor: theme.surface }]}>
          <Text style={[styles.title, { color: theme.text }]}>Add to Category</Text>
          {categories.map(cat => {
            const already = note?.categoryIds?.includes(cat.id) ?? false;
            return (
              <TouchableOpacity
                key={cat.id}
                style={styles.row}
                onPress={() => { if (note) onToggleCategory(note.id, cat.id, !already); }}
              >
                <View style={styles.rowInner}>
                  <Text style={[styles.rowText, { color: theme.text }]}>{cat.name}</Text>
                  {already && <Text style={[styles.check, { color: theme.text }]}>✓</Text>}
                </View>
              </TouchableOpacity>
            );
          })}
          <TouchableOpacity style={[styles.cancelRow, { borderTopColor: theme.border }]} onPress={onClose}>
            <Text style={[styles.cancelText, { color: theme.textSub }]}>Done</Text>
          </TouchableOpacity>
        </View>
      </TouchableOpacity>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay:   { flex: 1, justifyContent: 'center', alignItems: 'center' },
  box:       { borderRadius: 14, padding: 8, width: 280 },
  title:     { fontSize: 15, fontWeight: '600', padding: 16, paddingBottom: 8 },
  row:       { paddingHorizontal: 16, paddingVertical: 14, borderRadius: 8 },
  rowInner:  { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  rowText:   { fontSize: 15 },
  check:     { fontSize: 15, fontWeight: '600' },
  cancelRow: { paddingHorizontal: 16, paddingVertical: 14, borderTopWidth: 1, marginTop: 4 },
  cancelText: { fontSize: 15, textAlign: 'center' },
});
