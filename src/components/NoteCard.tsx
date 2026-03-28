import React from 'react';
import { View, Text, Image, TouchableOpacity, StyleSheet } from 'react-native';
import { Note } from '../types/noteTypes';
import { Theme } from '../styles/theme';

const formatDate = (ts: number) =>
  new Date(ts).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });

interface Props {
  note: Note;
  theme: Theme;
  onPress: () => void;
  onToggleFavorite: () => void;
  onOptionsPress: (ref: any) => void;
}

export default function NoteCard({ note, theme, onPress, onToggleFavorite, onOptionsPress }: Props) {
  let optRef: any = null;

  return (
    <TouchableOpacity
      style={[styles.card, { backgroundColor: theme.surface }]}
      onPress={onPress}
      activeOpacity={0.7}
    >
      <View style={[styles.cardThumbnail, { backgroundColor: theme.surfaceAlt }]}>
        {note.thumbnailUri && (
          <Image
            source={{ uri: `file://${note.thumbnailUri}` }}
            style={styles.cardThumbnailImage}
            resizeMode="cover"
          />
        )}
        {note.type === 'pdf' && (
          <View style={styles.pdfBadge}>
            <Text style={styles.pdfBadgeText}>PDF</Text>
          </View>
        )}
      </View>
      <Text style={[styles.cardTitle, { color: theme.text }]} numberOfLines={2}>{note.title}</Text>
      <Text style={[styles.cardDate, { color: theme.textHint }]}>{formatDate(note.updatedAt)}</Text>

      <TouchableOpacity
        style={styles.favoriteBtn}
        onPress={onToggleFavorite}
        hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
      >
        <Text style={styles.favoriteBtnText}>{note.isFavorite ? '★' : '☆'}</Text>
      </TouchableOpacity>

      <TouchableOpacity
        ref={(r) => { optRef = r; }}
        style={styles.optionsBtn}
        onPress={() => onOptionsPress(optRef)}
        hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
      >
        <Text style={[styles.optionsBtnText, { color: theme.textSub }]}>⋮</Text>
      </TouchableOpacity>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    flex: 1,
    margin: 8,
    borderRadius: 12,
    padding: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.08,
    shadowRadius: 4,
    elevation: 2,
  },
  cardThumbnail: {
    height: 140,
    borderRadius: 8,
    marginBottom: 10,
    position: 'relative',
    overflow: 'hidden',
  },
  cardThumbnailImage: {
    ...StyleSheet.absoluteFillObject,
    borderRadius: 8,
  },
  pdfBadge: {
    position: 'absolute',
    top: 8,
    right: 8,
    backgroundColor: '#E8402A',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  pdfBadgeText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '700',
  },
  cardTitle: {
    fontSize: 15,
    fontWeight: '600',
    marginBottom: 4,
  },
  cardDate: {
    fontSize: 12,
  },
  favoriteBtn: {
    position: 'absolute',
    top: 6,
    left: 6,
    width: 24,
    height: 24,
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1,
  },
  favoriteBtnText: {
    fontSize: 16,
    color: '#F5A623',
  },
  optionsBtn: {
    position: 'absolute',
    top: 6,
    right: 6,
    width: 24,
    height: 24,
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1,
  },
  optionsBtnText: {
    fontSize: 16,
    fontWeight: '700',
    lineHeight: 18,
  },
});
