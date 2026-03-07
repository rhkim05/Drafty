import React, { useEffect, useRef, useState } from 'react';
import {
  FlatList,
  Image,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import RNFS from 'react-native-fs';
import PdfThumbnail from 'react-native-pdf-thumbnail';

const THUMBS_DIR = `${RNFS.DocumentDirectoryPath}/thumbnails`;
const THUMB_W = 72;
const THUMB_H = 96;

interface Props {
  pdfUri: string;
  noteId: string;
  totalPages: number;
  currentPage: number;
  onPageSelect: (page: number) => void;
}

interface ItemProps {
  page: number;
  pdfUri: string;
  noteId: string;
  isActive: boolean;
  onPress: () => void;
}

const ThumbnailItem = React.memo(({ page, pdfUri, noteId, isActive, onPress }: ItemProps) => {
  const [thumbUri, setThumbUri] = useState<string | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        await RNFS.mkdir(THUMBS_DIR);
        const thumbPath = `${THUMBS_DIR}/${noteId}_p${page}.jpg`;
        if (!(await RNFS.exists(thumbPath))) {
          const { uri } = await PdfThumbnail.generate(pdfUri, page - 1);
          await RNFS.copyFile(uri, thumbPath);
        }
        if (!cancelled) setThumbUri(`file://${thumbPath}`);
      } catch {
        if (!cancelled) setError(true);
      }
    })();
    return () => { cancelled = true; };
  }, [page, noteId, pdfUri]);

  return (
    <TouchableOpacity
      style={[styles.item, isActive && styles.itemActive]}
      onPress={onPress}
      activeOpacity={0.7}
    >
      {thumbUri ? (
        <Image source={{ uri: thumbUri }} style={styles.thumb} resizeMode="contain" />
      ) : (
        <View style={styles.thumbPlaceholder}>
          <Text style={styles.placeholderText}>{error ? '!' : page}</Text>
        </View>
      )}
      <Text style={[styles.pageNum, isActive && styles.pageNumActive]}>{page}</Text>
    </TouchableOpacity>
  );
});

export default function ThumbnailStrip({ pdfUri, noteId, totalPages, currentPage, onPageSelect }: Props) {
  const listRef = useRef<FlatList>(null);
  const pages = useRef(Array.from({ length: totalPages }, (_, i) => i + 1)).current;

  useEffect(() => {
    if (totalPages === 0) return;
    listRef.current?.scrollToIndex({
      index: currentPage - 1,
      animated: true,
      viewPosition: 0.5,
    });
  }, [currentPage, totalPages]);

  return (
    <View style={styles.container}>
      <FlatList
        ref={listRef}
        horizontal
        data={pages}
        keyExtractor={String}
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.list}
        onScrollToIndexFailed={() => {}}
        renderItem={({ item: page }) => (
          <ThumbnailItem
            key={page}
            page={page}
            pdfUri={pdfUri}
            noteId={noteId}
            isActive={page === currentPage}
            onPress={() => onPageSelect(page)}
          />
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    height: 140,
    backgroundColor: '#111',
    borderTopWidth: 1,
    borderTopColor: '#333',
  },
  list: { paddingHorizontal: 8, paddingVertical: 8, gap: 6 },
  item: {
    width: THUMB_W,
    alignItems: 'center',
    borderRadius: 6,
    padding: 3,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  itemActive: { borderColor: '#4A90E2' },
  thumb: { width: THUMB_W - 6, height: THUMB_H - 20, borderRadius: 3 },
  thumbPlaceholder: {
    width: THUMB_W - 6,
    height: THUMB_H - 20,
    backgroundColor: '#2A2A2A',
    borderRadius: 3,
    alignItems: 'center',
    justifyContent: 'center',
  },
  placeholderText: { color: '#555', fontSize: 11 },
  pageNum: { color: '#777', fontSize: 10, marginTop: 3 },
  pageNumActive: { color: '#4A90E2', fontWeight: '700' },
});
