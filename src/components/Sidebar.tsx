import React, { useRef, useState } from 'react';
import {
  Animated,
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  Modal,
  TextInput,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { Category, BUILT_IN_CATEGORIES } from '../types/categoryTypes';

const COLLAPSED_W = 44;
const EXPANDED_W = 240;

interface SidebarProps {
  categories: Category[];
  selectedCategoryId: string;
  onSelectCategory: (id: string) => void;
  onAddCategory: (name: string) => void;
}

export default function Sidebar({ categories, selectedCategoryId, onSelectCategory, onAddCategory }: SidebarProps) {
  const [expanded, setExpanded] = useState(false);
  const [addModalVisible, setAddModalVisible] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');
  const widthAnim = useRef(new Animated.Value(COLLAPSED_W)).current;

  const toggle = () => {
    const toValue = expanded ? COLLAPSED_W : EXPANDED_W;
    Animated.timing(widthAnim, {
      toValue,
      duration: 220,
      useNativeDriver: false,
    }).start();
    setExpanded(prev => !prev);
  };

  const collapse = () => {
    if (!expanded) return;
    Animated.timing(widthAnim, {
      toValue: COLLAPSED_W,
      duration: 220,
      useNativeDriver: false,
    }).start();
    setExpanded(false);
  };

  const selectCategory = (id: string) => {
    onSelectCategory(id);
    collapse();
  };

  const confirmAddCategory = () => {
    const trimmed = newCategoryName.trim();
    if (trimmed) {
      onAddCategory(trimmed);
    }
    setNewCategoryName('');
    setAddModalVisible(false);
  };

  const allCategories: Category[] = [...BUILT_IN_CATEGORIES, ...categories];

  return (
    <>
      {/* Backdrop */}
      {expanded && (
        <TouchableOpacity
          style={styles.backdrop}
          activeOpacity={1}
          onPress={collapse}
        />
      )}

      {/* Sidebar panel */}
      <Animated.View style={[styles.sidebar, { width: widthAnim }, expanded && styles.sidebarExpanded]}>
        {/* Toggle button */}
        <TouchableOpacity style={styles.toggleBtn} onPress={toggle}>
          <Text style={styles.toggleIcon}>{expanded ? '✕' : '≡'}</Text>
        </TouchableOpacity>

        {expanded && (
          <>
            {/* Category list */}
            <ScrollView style={styles.categoryList} showsVerticalScrollIndicator={false}>
              {allCategories.map(cat => (
                <TouchableOpacity
                  key={cat.id}
                  style={[styles.categoryRow, selectedCategoryId === cat.id && styles.categoryRowSelected]}
                  onPress={() => selectCategory(cat.id)}
                >
                  <Text style={styles.categoryIcon}>
                    {cat.id === 'all' ? '📋' : cat.id === 'pdfs' ? '📄' : cat.id === 'notes' ? '📝' : '📁'}
                  </Text>
                  <Text
                    style={[styles.categoryLabel, selectedCategoryId === cat.id && styles.categoryLabelSelected]}
                    numberOfLines={1}
                  >
                    {cat.name}
                  </Text>
                </TouchableOpacity>
              ))}
            </ScrollView>

            {/* Divider */}
            <View style={styles.divider} />

            {/* Add category button */}
            <TouchableOpacity style={styles.addCategoryBtn} onPress={() => setAddModalVisible(true)}>
              <Text style={styles.addCategoryText}>+ Add Category</Text>
            </TouchableOpacity>

            {/* Settings and Account icons */}
            <View style={styles.iconRow}>
              <TouchableOpacity style={styles.iconBtn}>
                <Text style={styles.iconText}>⚙</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.iconBtn}>
                <Text style={styles.iconText}>👤</Text>
              </TouchableOpacity>
            </View>
          </>
        )}
      </Animated.View>

      {/* Add Category Modal */}
      <Modal visible={addModalVisible} transparent animationType="fade" onRequestClose={() => setAddModalVisible(false)}>
        <KeyboardAvoidingView style={modalStyles.overlay} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <TouchableOpacity style={StyleSheet.absoluteFill} onPress={() => setAddModalVisible(false)} />
          <View style={modalStyles.box}>
            <Text style={modalStyles.title}>New Category</Text>
            <TextInput
              style={modalStyles.input}
              value={newCategoryName}
              onChangeText={setNewCategoryName}
              onSubmitEditing={confirmAddCategory}
              placeholder="Category name"
              placeholderTextColor="#AAA"
              autoFocus
              autoCorrect={false}
              autoCapitalize="words"
            />
            <View style={modalStyles.buttons}>
              <TouchableOpacity style={modalStyles.cancel} onPress={() => setAddModalVisible(false)}>
                <Text style={modalStyles.cancelText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={modalStyles.confirm} onPress={confirmAddCategory}>
                <Text style={modalStyles.confirmText}>Add</Text>
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 9,
    backgroundColor: 'rgba(0,0,0,0.2)',
  },
  sidebar: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    zIndex: 10,
    backgroundColor: '#FFFFFF',
    borderRightWidth: 1,
    borderRightColor: '#E0E0D8',
    overflow: 'hidden',
  },
  sidebarExpanded: {
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 2, height: 0 },
    shadowOpacity: 0.15,
    shadowRadius: 8,
  },
  toggleBtn: {
    width: COLLAPSED_W,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  toggleIcon: {
    fontSize: 20,
    color: '#1A1A1A',
  },
  categoryList: {
    flex: 1,
    paddingTop: 8,
  },
  categoryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 8,
    marginHorizontal: 8,
    marginVertical: 2,
  },
  categoryRowSelected: {
    backgroundColor: '#1A1A1A',
  },
  categoryIcon: {
    fontSize: 16,
    marginRight: 10,
  },
  categoryLabel: {
    flex: 1,
    fontSize: 14,
    color: '#1A1A1A',
    fontWeight: '500',
  },
  categoryLabelSelected: {
    color: '#FFFFFF',
  },
  divider: {
    height: 1,
    backgroundColor: '#E0E0D8',
    marginHorizontal: 12,
    marginVertical: 8,
  },
  addCategoryBtn: {
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  addCategoryText: {
    fontSize: 13,
    color: '#555',
    fontWeight: '500',
  },
  iconRow: {
    flexDirection: 'row',
    paddingHorizontal: 8,
    paddingBottom: 16,
    gap: 4,
  },
  iconBtn: {
    width: 32,
    height: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconText: {
    fontSize: 18,
    color: '#555',
  },
});

const modalStyles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.45)' },
  box:     { backgroundColor: '#FFF', borderRadius: 14, padding: 24, width: 300 },
  title:   { fontSize: 16, fontWeight: '600', color: '#1A1A1A', marginBottom: 14 },
  input:   { borderWidth: 1, borderColor: '#DDD', borderRadius: 8, padding: 10, fontSize: 15, color: '#1A1A1A', marginBottom: 18 },
  buttons: { flexDirection: 'row', gap: 10 },
  cancel:  { flex: 1, paddingVertical: 10, borderRadius: 8, backgroundColor: '#F0F0EA', alignItems: 'center' },
  cancelText:  { color: '#555', fontSize: 15 },
  confirm: { flex: 1, paddingVertical: 10, borderRadius: 8, backgroundColor: '#1A1A1A', alignItems: 'center' },
  confirmText: { color: '#FFF', fontSize: 15, fontWeight: '600' },
});
