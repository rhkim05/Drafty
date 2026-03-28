import React, { useState, useEffect } from 'react';
import {
  Modal,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
} from 'react-native';
import { Theme } from '../styles/theme';

interface Props {
  visible: boolean;
  initialTitle: string;
  theme: Theme;
  onConfirm: (newTitle: string) => void;
  onCancel: () => void;
}

export default function RenameNoteModal({ visible, initialTitle, theme, onConfirm, onCancel }: Props) {
  const [text, setText] = useState(initialTitle);

  useEffect(() => {
    if (visible) setText(initialTitle);
  }, [visible, initialTitle]);

  const handleConfirm = () => {
    const trimmed = text.trim();
    if (trimmed && trimmed !== initialTitle) onConfirm(trimmed);
    else onCancel();
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <KeyboardAvoidingView style={[styles.overlay, { backgroundColor: theme.overlay }]} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <TouchableOpacity style={StyleSheet.absoluteFill} onPress={onCancel} />
        <View style={[styles.box, { backgroundColor: theme.surface }]}>
          <Text style={[styles.title, { color: theme.text }]}>Rename</Text>
          <TextInput
            style={[styles.input, { borderColor: theme.border, color: theme.text, backgroundColor: theme.surfaceAlt }]}
            value={text}
            onChangeText={setText}
            onSubmitEditing={handleConfirm}
            autoFocus
            selectTextOnFocus
            autoCorrect={false}
            autoCapitalize="none"
            keyboardType="default"
            placeholderTextColor={theme.textHint}
          />
          <View style={styles.buttons}>
            <TouchableOpacity style={[styles.cancel, { backgroundColor: theme.surfaceAlt }]} onPress={onCancel}>
              <Text style={[styles.cancelText, { color: theme.textSub }]}>Cancel</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.confirm, { backgroundColor: theme.text }]} onPress={handleConfirm}>
              <Text style={[styles.confirmText, { color: theme.surface }]}>Confirm</Text>
            </TouchableOpacity>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  box:     { borderRadius: 14, padding: 24, width: 300 },
  title:   { fontSize: 16, fontWeight: '600', marginBottom: 14 },
  input:   { borderWidth: 1, borderRadius: 8, padding: 10, fontSize: 15, marginBottom: 18 },
  buttons: { flexDirection: 'row', gap: 10 },
  cancel:  { flex: 1, paddingVertical: 10, borderRadius: 8, alignItems: 'center' },
  cancelText:  { fontSize: 15 },
  confirm: { flex: 1, paddingVertical: 10, borderRadius: 8, alignItems: 'center' },
  confirmText: { fontSize: 15, fontWeight: '600' },
});
