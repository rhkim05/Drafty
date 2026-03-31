import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  SafeAreaView,
  ActivityIndicator,
  DeviceEventEmitter,
  findNodeHandle,
  Modal,
  TextInput,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import RNFS from 'react-native-fs';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { RootStackParamList } from '../navigation';
import Toolbar from '../components/Toolbar';
import CanvasView from '../native/CanvasView';
import CanvasModule from '../native/CanvasModule';
import { useToolStore } from '../store/useToolStore';
import { useNotebookStore } from '../store/useNotebookStore';
import { useTheme } from '../styles/theme';
import ThumbnailStrip from '../components/ThumbnailStrip';

type Props = NativeStackScreenProps<RootStackParamList, 'Editor'>;

const DRAWINGS_DIR = `${RNFS.DocumentDirectoryPath}/drawings`;

export default function EditorScreen({ route, navigation }: Props) {
  const { note } = route.params;
  const isPdf = note.type === 'pdf';
  const canvasRef = useRef<any>(null);
  const [totalPages, setTotalPages] = useState(isPdf ? 0 : 1);
  const [currentPage, setCurrentPage] = useState(note.lastPage ?? 1);
  const [loading, setLoading] = useState(isPdf);
  const [showStrip, setShowStrip] = useState(false);
  const [showPageInput, setShowPageInput] = useState(false);
  const [pageInputText, setPageInputText] = useState('');
  const updateNote = useNotebookStore(s => s.updateNote);
  const activeTool      = useToolStore(s => s.activeTool);
  const penThickness    = useToolStore(s => s.penThickness);
  const eraserThickness = useToolStore(s => s.eraserThickness);
  const eraserMode      = useToolStore(s => s.eraserMode);
  const penColor        = useToolStore(s => s.penColor);
  const theme = useTheme();

  useEffect(() => {
    if (isPdf) {
      useToolStore.getState().setTool('select');
    }
  }, [isPdf]);

  useEffect(() => {
    const pageSub = DeviceEventEmitter.addListener(
      'canvasPageChanged',
      ({ page }: { page: number }) => setCurrentPage(page),
    );
    const loadSub = DeviceEventEmitter.addListener(
      'canvasLoadComplete',
      ({ totalPages: tp }: { totalPages: number }) => {
        setTotalPages(tp);
        setLoading(false);
        if (!note.totalPages || note.totalPages !== tp) {
          updateNote(note.id, { totalPages: tp });
        }
        if (note.lastPage && note.lastPage > 1) {
          const tag = findNodeHandle(canvasRef.current);
          if (tag) CanvasModule.scrollToPage(tag, note.lastPage);
        }
      },
    );
    return () => { pageSub.remove(); loadSub.remove(); };
  }, [note.lastPage, note.totalPages, note.id, updateNote]);

  const handleLayout = useCallback(async () => {
    if (!note.drawingUri) return;
    const exists = await RNFS.exists(note.drawingUri);
    if (!exists) return;
    const json = await RNFS.readFile(note.drawingUri, 'utf8');
    const tag = findNodeHandle(canvasRef.current);
    if (tag) {
      CanvasModule.loadStrokes(tag, json);
      if (!isPdf) {
        const pageCount = await CanvasModule.getPageCount(tag);
        setTotalPages(pageCount);
      }
    }
  }, [note.drawingUri, isPdf]);

  const saveStrokes = useCallback(async () => {
    updateNote(note.id, { lastPage: currentPage, updatedAt: Date.now() });
    const tag = findNodeHandle(canvasRef.current);
    if (!tag) return;
    const json = await CanvasModule.getStrokes(tag);
    if (json === '[]') return;
    await RNFS.mkdir(DRAWINGS_DIR);
    const filePath = `${DRAWINGS_DIR}/${note.id}.json`;
    await RNFS.writeFile(filePath, json, 'utf8');
    updateNote(note.id, { drawingUri: filePath });
  }, [note.id, currentPage, updateNote]);

  useEffect(() => {
    const unsub = navigation.addListener('beforeRemove', saveStrokes);
    return unsub;
  }, [navigation, saveStrokes]);

  const handleUndo = useCallback(() => {
    const tag = findNodeHandle(canvasRef.current);
    if (tag) CanvasModule.undo(tag);
  }, []);

  const handleRedo = useCallback(() => {
    const tag = findNodeHandle(canvasRef.current);
    if (tag) CanvasModule.redo(tag);
  }, []);

  const handleAddPage = useCallback(() => {
    const tag = findNodeHandle(canvasRef.current);
    if (tag) {
      CanvasModule.addPage(tag);
      setTotalPages(p => p + 1);
    }
  }, []);

  const handleGoToPage = useCallback(() => {
    const page = parseInt(pageInputText, 10);
    if (!isNaN(page) && page >= 1 && page <= totalPages) {
      const tag = findNodeHandle(canvasRef.current);
      if (tag) CanvasModule.scrollToPage(tag, page);
    }
    setShowPageInput(false);
    setPageInputText('');
  }, [pageInputText, totalPages]);

  const handlePageSelect = useCallback((page: number) => {
    const tag = findNodeHandle(canvasRef.current);
    if (tag) CanvasModule.scrollToPage(tag, page);
  }, []);

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.bg }]}>
      <View style={[styles.header, { backgroundColor: theme.surface, borderBottomColor: theme.border }]}>
        <TouchableOpacity style={styles.backButton} onPress={() => navigation.goBack()}>
          <Text style={[styles.backButtonText, { color: theme.text }]}>← Back</Text>
        </TouchableOpacity>
        <Text style={[styles.title, { color: theme.text }]} numberOfLines={1}>{note.title}</Text>
        <TouchableOpacity style={styles.addPageBtn} onPress={handleAddPage}>
          <Text style={[styles.addPageText, { color: theme.text }]}>+ Page</Text>
        </TouchableOpacity>
      </View>

      <Toolbar
        showHandTool
        onUndo={handleUndo}
        onRedo={handleRedo}
        onToggleStrip={totalPages > 0 ? () => setShowStrip(s => !s) : undefined}
        showStrip={showStrip}
        currentPage={currentPage}
        totalPages={totalPages}
      />

      <View style={styles.canvasContainer}>
        {loading && (
          <View style={[styles.loadingOverlay, { backgroundColor: theme.bg }]}>
            <ActivityIndicator size="large" color={theme.text} />
            <Text style={[styles.loadingText, { color: theme.textSub }]}>Loading...</Text>
          </View>
        )}

        <CanvasView
          ref={canvasRef}
          pdfUri={note.pdfUri}
          tool={activeTool}
          penColor={penColor}
          penThickness={penThickness}
          eraserThickness={eraserThickness}
          eraserMode={eraserMode}
          style={StyleSheet.absoluteFill}
          onLayout={handleLayout}
        />

        {totalPages > 0 && (
          <TouchableOpacity
            style={styles.pageIndex}
            onPress={() => { setPageInputText(String(currentPage)); setShowPageInput(true); }}
            activeOpacity={0.8}
          >
            <Text style={styles.pageIndexText}>{currentPage} / {totalPages}</Text>
          </TouchableOpacity>
        )}
      </View>

      <Modal visible={showPageInput} transparent animationType="fade" onRequestClose={() => setShowPageInput(false)}>
        <KeyboardAvoidingView style={styles.modalOverlay} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <TouchableOpacity style={StyleSheet.absoluteFill} onPress={() => setShowPageInput(false)} />
          <View style={[styles.modalBox, { backgroundColor: theme.surface }]}>
            <Text style={[styles.modalTitle, { color: theme.text }]}>Go to page</Text>
            <TextInput
              style={[styles.modalInput, { backgroundColor: theme.bg, color: theme.text }]}
              keyboardType="number-pad"
              value={pageInputText}
              onChangeText={setPageInputText}
              onSubmitEditing={handleGoToPage}
              selectTextOnFocus
              autoFocus
              maxLength={6}
            />
            <Text style={[styles.modalHint, { color: theme.textSub }]}>1 – {totalPages}</Text>
            <View style={styles.modalButtons}>
              <TouchableOpacity style={[styles.modalCancel, { backgroundColor: theme.surfaceAlt }]} onPress={() => setShowPageInput(false)}>
                <Text style={[styles.modalCancelText, { color: theme.textSub }]}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.modalGo} onPress={handleGoToPage}>
                <Text style={styles.modalGoText}>Go</Text>
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>

      {showStrip && isPdf && note.pdfUri && totalPages > 0 && (
        <ThumbnailStrip
          pdfUri={note.pdfUri}
          noteId={note.id}
          totalPages={totalPages}
          currentPage={currentPage}
          onPageSelect={handlePageSelect}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  backButton: { paddingVertical: 6, paddingRight: 16 },
  backButtonText: { fontSize: 16 },
  title: { flex: 1, fontSize: 16, fontWeight: '600', textAlign: 'center' },
  addPageBtn: { paddingVertical: 6, paddingLeft: 16 },
  addPageText: { fontSize: 14, fontWeight: '500' },
  canvasContainer: { flex: 1 },
  pageIndex: {
    position: 'absolute',
    bottom: 16,
    right: 16,
    backgroundColor: 'rgba(0,0,0,0.5)',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  pageIndexText: { color: '#FFFFFF', fontSize: 13 },
  loadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1,
  },
  loadingText: { marginTop: 12, fontSize: 14 },
  modalOverlay: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0,0,0,0.55)',
  },
  modalBox: {
    borderRadius: 14,
    padding: 24,
    width: 260,
    alignItems: 'center',
  },
  modalTitle: { fontSize: 16, fontWeight: '600', marginBottom: 16 },
  modalInput: {
    fontSize: 28,
    fontWeight: '700',
    textAlign: 'center',
    borderRadius: 8,
    width: '100%',
    paddingVertical: 10,
    marginBottom: 6,
  },
  modalHint: { fontSize: 12, marginBottom: 20 },
  modalButtons: { flexDirection: 'row', gap: 12 },
  modalCancel: {
    flex: 1, paddingVertical: 10, borderRadius: 8,
    alignItems: 'center',
  },
  modalCancelText: { fontSize: 15 },
  modalGo: {
    flex: 1, paddingVertical: 10, borderRadius: 8,
    backgroundColor: '#4A90E2', alignItems: 'center',
  },
  modalGoText: { color: '#FFF', fontSize: 15, fontWeight: '600' },
});
