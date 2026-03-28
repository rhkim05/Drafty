import React, { useEffect, useState, useMemo, useRef } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  SafeAreaView,
  Alert,
} from 'react-native';
import DocumentPicker from 'react-native-document-picker';
import RNFS from 'react-native-fs';
import PdfThumbnail from 'react-native-pdf-thumbnail';
import PdfCanvasModule from '../native/PdfCanvasModule';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useNotebookStore } from '../store/useNotebookStore';
import { Note } from '../types/noteTypes';
import { RootStackParamList } from '../navigation';
import Sidebar from '../components/Sidebar';
import NoteCard from '../components/NoteCard';
import RenameNoteModal from '../components/RenameNoteModal';
import CategoryPickerModal from '../components/CategoryPickerModal';
import ExportModal from '../components/ExportModal';
import NoteOptionsMenu from '../components/NoteOptionsMenu';
import { useTheme } from '../styles/theme';

type HomeNav = NativeStackNavigationProp<RootStackParamList, 'Home'>;

const uniqueTitle = (base: string, existingNotes: Note[]): string => {
  const titles = new Set(existingNotes.map(n => n.title));
  if (!titles.has(base)) return base;
  let i = 1;
  while (titles.has(`${base} (${i})`)) i++;
  return `${base} (${i})`;
};

export default function HomeScreen() {
  const navigation = useNavigation<HomeNav>();
  const { notes, categories, addNote, deleteNote, updateNote, addCategory } = useNotebookStore();
  const theme = useTheme();

  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [selectedCategoryId, setSelectedCategoryId] = useState('all');

  // Modal targets
  const [renameTarget, setRenameTarget] = useState<{ id: string; title: string } | null>(null);
  const [categoryTarget, setCategoryTarget] = useState<Note | null>(null);
  const [exportTarget, setExportTarget] = useState<Note | null>(null);
  const [popupNote, setPopupNote] = useState<Note | null>(null);
  const [popupPos, setPopupPos] = useState({ x: 0, y: 0 });

  // Retroactively generate thumbnails and totalPages for PDF notes that are missing them
  useEffect(() => {
    const thumbsDir = `${RNFS.DocumentDirectoryPath}/thumbnails`;
    notes.forEach(async (note) => {
      if (note.type !== 'pdf' || !note.pdfUri) return;
      const updates: Partial<Note> = {};
      try {
        if (!note.thumbnailUri) {
          await RNFS.mkdir(thumbsDir);
          const thumbPath = `${thumbsDir}/${note.id}_p1.jpg`;
          const { uri } = await PdfThumbnail.generate(note.pdfUri, 0);
          await RNFS.copyFile(uri, thumbPath);
          updates.thumbnailUri = thumbPath;
        }
        if (!note.totalPages) {
          updates.totalPages = await PdfCanvasModule.getPageCount(note.pdfUri);
        }
        if (Object.keys(updates).length > 0) updateNote(note.id, updates);
      } catch {
        // silently skip — thumbnail and page count are optional
      }
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // run once on mount

  const filteredNotes = useMemo(() => {
    switch (selectedCategoryId) {
      case 'all':       return notes;
      case 'favorites': return notes.filter(n => n.isFavorite);
      case 'pdfs':      return notes.filter(n => n.type === 'pdf');
      case 'notes':     return notes.filter(n => n.type === 'note');
      default:          return notes.filter(n => n.categoryIds?.includes(selectedCategoryId));
    }
  }, [notes, selectedCategoryId]);

  const openNote = (note: Note) => {
    if (note.type === 'pdf') navigation.navigate('PdfViewer', { note });
    else navigation.navigate('NoteEditor', { note });
  };

  const createNote = () => {
    const note: Note = {
      id: Date.now().toString(),
      title: uniqueTitle('New Note', notes),
      createdAt: Date.now(),
      updatedAt: Date.now(),
      type: 'note',
    };
    addNote(note);
    navigation.navigate('NoteEditor', { note });
  };

  const importPdf = async () => {
    try {
      const result = await DocumentPicker.pickSingle({ type: DocumentPicker.types.pdf });
      const destDir = `${RNFS.DocumentDirectoryPath}/pdfs`;
      await RNFS.mkdir(destDir);
      const fileName = `${Date.now()}_${result.name ?? 'imported.pdf'}`;
      const destPath = `${destDir}/${fileName}`;
      await RNFS.copyFile(result.uri, destPath);

      const noteId = Date.now().toString();
      let thumbnailUri: string | undefined;
      let totalPages: number | undefined;
      await Promise.allSettled([
        (async () => {
          const thumbsDir = `${RNFS.DocumentDirectoryPath}/thumbnails`;
          await RNFS.mkdir(thumbsDir);
          const thumbPath = `${thumbsDir}/${noteId}_p1.jpg`;
          const { uri } = await PdfThumbnail.generate(destPath, 0);
          await RNFS.copyFile(uri, thumbPath);
          thumbnailUri = thumbPath;
        })(),
        (async () => {
          totalPages = await PdfCanvasModule.getPageCount(destPath);
        })(),
      ]);

      const note: Note = {
        id: noteId,
        title: uniqueTitle(result.name?.replace(/\.pdf$/i, '') ?? 'Imported PDF', notes),
        createdAt: Date.now(),
        updatedAt: Date.now(),
        type: 'pdf',
        pdfUri: destPath,
        thumbnailUri,
        totalPages,
      };
      addNote(note);
    } catch (err) {
      if (!DocumentPicker.isCancel(err)) {
        Alert.alert('Error', 'Failed to import PDF.');
      }
    }
  };

  const handleRenameConfirm = (newTitle: string) => {
    if (!renameTarget) return;
    updateNote(renameTarget.id, {
      title: uniqueTitle(newTitle, notes.filter(n => n.id !== renameTarget.id)),
    });
    setRenameTarget(null);
  };

  const handleToggleCategory = (noteId: string, categoryId: string, isAdding: boolean) => {
    const note = categoryTarget;
    if (!note) return;
    const prev = note.categoryIds ?? [];
    const next = isAdding ? [...prev, categoryId] : prev.filter(id => id !== categoryId);
    updateNote(noteId, { categoryIds: next });
    setCategoryTarget(n => n ? { ...n, categoryIds: next } : n);
  };

  const isEmptyCategory = selectedCategoryId !== 'all';

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.bg }]}>
      <View style={styles.row}>
        <Sidebar
          open={sidebarOpen}
          categories={categories}
          selectedCategoryId={selectedCategoryId}
          onSelectCategory={setSelectedCategoryId}
          onAddCategory={addCategory}
          onClose={() => setSidebarOpen(false)}
        />

        <View style={{ flex: 1 }}>
          <View style={[styles.header, { backgroundColor: theme.surface, borderBottomColor: theme.border }]}>
            <TouchableOpacity style={styles.menuBtn} onPress={() => setSidebarOpen(o => !o)}>
              <Text style={[styles.menuIcon, { color: theme.text }]}>≡</Text>
            </TouchableOpacity>
            <Text style={[styles.title, { color: theme.text }]}>My Notes</Text>
            <View style={styles.headerButtons}>
              <TouchableOpacity style={[styles.importButton, { backgroundColor: theme.surface, borderColor: theme.text }]} onPress={importPdf}>
                <Text style={[styles.importButtonText, { color: theme.text }]}>Import PDF</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.newButton, { backgroundColor: theme.text }]} onPress={createNote}>
                <Text style={[styles.newButtonText, { color: theme.surface }]}>+ New</Text>
              </TouchableOpacity>
            </View>
          </View>

          {filteredNotes.length === 0 ? (
            <View style={styles.empty}>
              <Text style={[styles.emptyText, { color: theme.textSub }]}>{isEmptyCategory ? 'No notes in this category.' : 'No notes yet.'}</Text>
              {!isEmptyCategory && <Text style={[styles.emptySubText, { color: theme.textHint }]}>Tap "+ New" to create one.</Text>}
            </View>
          ) : (
            <FlatList
              data={filteredNotes}
              keyExtractor={item => item.id}
              contentContainerStyle={styles.list}
              numColumns={3}
              renderItem={({ item }) => (
                <NoteCard
                  note={item}
                  theme={theme}
                  onPress={() => openNote(item)}
                  onToggleFavorite={() => updateNote(item.id, { isFavorite: !item.isFavorite })}
                  onOptionsPress={(ref) => {
                    ref?.measureInWindow((x: number, y: number, w: number, h: number) => {
                      setPopupPos({ x: x + w, y: y + h + 4 });
                      setPopupNote(item);
                    });
                  }}
                />
              )}
            />
          )}
        </View>
      </View>

      <RenameNoteModal
        visible={!!renameTarget}
        initialTitle={renameTarget?.title ?? ''}
        theme={theme}
        onConfirm={handleRenameConfirm}
        onCancel={() => setRenameTarget(null)}
      />

      <CategoryPickerModal
        note={categoryTarget}
        categories={categories}
        theme={theme}
        onToggleCategory={handleToggleCategory}
        onClose={() => setCategoryTarget(null)}
      />

      <ExportModal
        note={exportTarget}
        theme={theme}
        onClose={() => setExportTarget(null)}
      />

      <NoteOptionsMenu
        note={popupNote}
        position={popupPos}
        hasCategories={categories.length > 0}
        theme={theme}
        onRename={(note) => setRenameTarget({ id: note.id, title: note.title })}
        onEditCategory={setCategoryTarget}
        onExport={setExportTarget}
        onDelete={deleteNote}
        onClose={() => setPopupNote(null)}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  row: { flex: 1, flexDirection: 'row' },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 16,
    borderBottomWidth: 1,
  },
  menuBtn: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center', marginRight: 8 },
  menuIcon: { fontSize: 22 },
  title: { fontSize: 28, fontWeight: '700', flex: 1 },
  headerButtons: { flexDirection: 'row', gap: 10 },
  importButton: { paddingHorizontal: 20, paddingVertical: 10, borderRadius: 10, borderWidth: 1.5 },
  importButtonText: { fontSize: 16, fontWeight: '600' },
  newButton: { paddingHorizontal: 20, paddingVertical: 10, borderRadius: 10 },
  newButtonText: { fontSize: 16, fontWeight: '600' },
  list: { padding: 16, gap: 16 },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyText: { fontSize: 20, fontWeight: '600' },
  emptySubText: { fontSize: 15, marginTop: 6 },
});
