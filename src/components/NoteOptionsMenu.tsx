import React from 'react';
import {
  Modal,
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Alert,
  Dimensions,
} from 'react-native';
import { Note } from '../types/noteTypes';
import { Theme } from '../styles/theme';

const POPUP_W = 180;
const screenWidth = Dimensions.get('window').width;

interface Props {
  note: Note | null;
  position: { x: number; y: number };
  hasCategories: boolean;
  theme: Theme;
  onRename: (note: Note) => void;
  onEditCategory: (note: Note) => void;
  onExport: (note: Note) => void;
  onDelete: (noteId: string) => void;
  onClose: () => void;
}

export default function NoteOptionsMenu({
  note, position, hasCategories, theme,
  onRename, onEditCategory, onExport, onDelete, onClose,
}: Props) {
  return (
    <Modal visible={!!note} transparent animationType="none" onRequestClose={onClose}>
      <TouchableOpacity style={StyleSheet.absoluteFill} activeOpacity={1} onPress={onClose} />
      <View style={[
        styles.box,
        {
          backgroundColor: theme.surface,
          borderColor: theme.border,
          top: position.y,
          left: Math.min(position.x - POPUP_W, screenWidth - POPUP_W - 8),
        },
      ]}>
        <TouchableOpacity
          style={styles.row}
          onPress={() => { if (note) { onClose(); onRename(note); } }}
        >
          <Text style={[styles.rowText, { color: theme.text }]}>Rename</Text>
        </TouchableOpacity>

        {hasCategories && (
          <>
            <View style={[styles.sep, { backgroundColor: theme.border }]} />
            <TouchableOpacity
              style={styles.row}
              onPress={() => { if (note) { onClose(); onEditCategory(note); } }}
            >
              <Text style={[styles.rowText, { color: theme.text }]}>Edit Category</Text>
            </TouchableOpacity>
          </>
        )}

        <View style={[styles.sep, { backgroundColor: theme.border }]} />
        <TouchableOpacity
          style={styles.row}
          onPress={() => { if (note) { onClose(); onExport(note); } }}
        >
          <Text style={[styles.rowText, { color: theme.text }]}>Export</Text>
        </TouchableOpacity>

        <View style={[styles.sep, { backgroundColor: theme.border }]} />
        <TouchableOpacity
          style={styles.row}
          onPress={() => {
            if (!note) return;
            onClose();
            Alert.alert('Delete', `Delete "${note.title}"?`, [
              { text: 'Cancel', style: 'cancel' },
              { text: 'Delete', style: 'destructive', onPress: () => onDelete(note.id) },
            ]);
          }}
        >
          <Text style={[styles.rowText, { color: theme.destructive }]}>Delete</Text>
        </TouchableOpacity>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  box: {
    position: 'absolute',
    width: POPUP_W,
    borderRadius: 10,
    borderWidth: 1,
    overflow: 'hidden',
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.18,
    shadowRadius: 10,
  },
  row: {
    paddingHorizontal: 16,
    paddingVertical: 13,
  },
  rowText: {
    fontSize: 15,
  },
  sep: {
    height: 1,
  },
});
