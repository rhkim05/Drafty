import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  SafeAreaView,
  findNodeHandle,
  DeviceEventEmitter,
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

type Props = NativeStackScreenProps<RootStackParamList, 'NoteEditor'>;

const DRAWINGS_DIR = `${RNFS.DocumentDirectoryPath}/drawings`;

export default function NoteEditorScreen({ route, navigation }: Props) {
  const { note } = route.params;
  const canvasRef = useRef<any>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const activeTool      = useToolStore(s => s.activeTool);
  const penThickness    = useToolStore(s => s.penThickness);
  const eraserThickness = useToolStore(s => s.eraserThickness);
  const eraserMode      = useToolStore(s => s.eraserMode);
  const penColor        = useToolStore(s => s.penColor);
  const updateNote = useNotebookStore(s => s.updateNote);
  const theme = useTheme();

  useEffect(() => {
    const pageSub = DeviceEventEmitter.addListener(
      'canvasPageChanged',
      ({ page }: { page: number }) => setCurrentPage(page),
    );
    return () => { pageSub.remove(); };
  }, []);

  const saveStrokes = useCallback(async () => {
    const tag = findNodeHandle(canvasRef.current);
    if (!tag) return;
    const json = await CanvasModule.getStrokes(tag);
    if (json === '[]' || json === '{"version":2,"canvasWidth":0,"strokes":[]}') return;
    await RNFS.mkdir(DRAWINGS_DIR);
    const filePath = `${DRAWINGS_DIR}/${note.id}.json`;
    await RNFS.writeFile(filePath, json, 'utf8');
    updateNote(note.id, { drawingUri: filePath, updatedAt: Date.now() });
  }, [note.id, updateNote]);

  useEffect(() => {
    const unsub = navigation.addListener('beforeRemove', saveStrokes);
    return unsub;
  }, [navigation, saveStrokes]);

  const handleLayout = useCallback(async () => {
    if (!note.drawingUri) return;
    const exists = await RNFS.exists(note.drawingUri);
    if (!exists) return;
    const json = await RNFS.readFile(note.drawingUri, 'utf8');
    const tag = findNodeHandle(canvasRef.current);
    if (tag) {
      CanvasModule.loadStrokes(tag, json);
      const pageCount = await CanvasModule.getPageCount(tag);
      setTotalPages(pageCount);
    }
  }, [note.drawingUri]);

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
        currentPage={currentPage}
        totalPages={totalPages}
      />

      <CanvasView
        ref={canvasRef}
        tool={activeTool}
        penColor={penColor}
        penThickness={penThickness}
        eraserThickness={eraserThickness}
        eraserMode={eraserMode}
        style={styles.canvas}
        onLayout={handleLayout}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  backButton: {
    paddingVertical: 6,
    paddingRight: 16,
  },
  backButtonText: {
    fontSize: 16,
  },
  title: {
    flex: 1,
    fontSize: 16,
    fontWeight: '600',
    textAlign: 'center',
  },
  addPageBtn: {
    paddingVertical: 6,
    paddingLeft: 16,
  },
  addPageText: {
    fontSize: 14,
    fontWeight: '500',
  },
  canvas: {
    flex: 1,
    backgroundColor: '#E8E8E8',
  },
});
