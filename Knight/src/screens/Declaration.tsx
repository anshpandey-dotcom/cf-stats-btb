import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Keyboard, KeyboardAvoidingView, Pressable, ScrollView, type ScrollViewInstance, StyleSheet, Text, TextInput, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { ExpoSpeechRecognitionModule, useSpeechRecognitionEvent } from 'expo-speech-recognition';
import { useKeepAwake } from 'expo-keep-awake';
import * as Haptics from 'expo-haptics';
import { Button, Card } from '../components/ui';
import { DECLARATION_TEXT, VOICE_PASS_RATIO } from '../lib/ritual';
import { alignWords, coverage, spokenTokens, tokenize, typedComplete, typedProgress } from '../lib/speechMatch';
import { useApp, Reward } from '../store/AppStore';
import { colors } from '../theme';

type Mode = 'voice' | 'type';

const DISPLAY_WORDS = DECLARATION_TEXT.split(/\s+/).map((word) => ({ word, count: tokenize(word).length }));
const TARGET = tokenize(DECLARATION_TEXT);

type Props = { onComplete: (reward: Reward | null) => void };

export function Declaration({ onComplete }: Props) {
  useKeepAwake();
  const { data, today, completeSection } = useApp();
  const alreadyDone = !!data.days[today]?.sections.declaration;

  const [mode, setMode] = useState<Mode>('voice');
  const [listening, setListening] = useState(false);
  const [finals, setFinals] = useState('');
  const [interim, setInterim] = useState('');
  const [typed, setTyped] = useState('');
  const [notice, setNotice] = useState<string | null>(null);

  const scrollRef = useRef<ScrollViewInstance>(null);
  const wantListening = useRef(false);
  const failures = useRef(0);
  const finishedRef = useRef(false);
  const finalsRef = useRef('');

  const finish = useCallback(
    (method: Mode) => {
      if (finishedRef.current) return;
      finishedRef.current = true;
      wantListening.current = false;
      try {
        ExpoSpeechRecognitionModule.abort();
      } catch {}
      setListening(false);
      const reward = completeSection('declaration', method);
      onComplete(reward);
    },
    [completeSection, onComplete],
  );

  const matched = useMemo(() => {
    if (mode !== 'voice') return new Array(TARGET.length).fill(false) as boolean[];
    return alignWords(spokenTokens(`${finals} ${interim}`), TARGET);
  }, [finals, interim, mode]);

  const typedState = useMemo(() => typedProgress(typed, TARGET), [typed]);

  const ratio = mode === 'voice' ? coverage(matched) : typedState.correct / TARGET.length;

  useEffect(() => {
    if (alreadyDone || finishedRef.current) return;
    if (mode === 'voice' && ratio >= VOICE_PASS_RATIO) finish('voice');
  }, [ratio, mode, alreadyDone, finish]);

  useEffect(() => {
    if (alreadyDone || finishedRef.current) return;
    if (mode === 'type' && typedComplete(typed, TARGET)) finish('type');
  }, [typed, mode, alreadyDone, finish]);

  useEffect(() => {
    const sub = Keyboard.addListener('keyboardDidShow', () => {
      setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 80);
    });
    return () => sub.remove();
  }, []);

  const begin = useCallback(() => {
    try {
      ExpoSpeechRecognitionModule.start({
        lang: 'en-US',
        interimResults: true,
        continuous: true,
        maxAlternatives: 1,
        contextualStrings: ['one percent', 'ninety nine percent', 'warrior mentality', 'businessman', 'number one leader', 'number one athlete'],
      });
    } catch (e: any) {
      setNotice(String(e?.message ?? e));
      wantListening.current = false;
      setListening(false);
    }
  }, []);

  useSpeechRecognitionEvent('start', () => {
    setListening(true);
    setNotice(null);
  });

  useSpeechRecognitionEvent('result', (event) => {
    const text = event.results[0]?.transcript ?? '';
    failures.current = 0;
    if (event.isFinal) {
      finalsRef.current = `${finalsRef.current} ${text}`.trim();
      setFinals(finalsRef.current);
      setInterim('');
    } else {
      setInterim(text);
    }
  });

  useSpeechRecognitionEvent('end', () => {
    setListening(false);
    if (wantListening.current && !finishedRef.current) {
      setTimeout(() => {
        if (wantListening.current && !finishedRef.current) begin();
      }, 250);
    }
  });

  useSpeechRecognitionEvent('error', (event) => {
    if (event.error === 'aborted') return;
    if (event.error === 'no-speech' || event.error === 'speech-timeout') {
      return;
    }
    failures.current += 1;
    if (event.error === 'not-allowed' || event.error === 'service-not-allowed' || failures.current > 4) {
      wantListening.current = false;
      setListening(false);
      setNotice(
        event.error === 'network'
          ? 'Speech recognition needs an internet connection. You can type instead.'
          : event.message || 'Speech recognition stopped. You can type instead.',
      );
    }
  });

  useEffect(() => {
    return () => {
      wantListening.current = false;
      try {
        ExpoSpeechRecognitionModule.abort();
      } catch {}
    };
  }, []);

  const toggleMic = async () => {
    if (listening || wantListening.current) {
      wantListening.current = false;
      try {
        ExpoSpeechRecognitionModule.stop();
      } catch {}
      setListening(false);
      return;
    }
    const permission = await ExpoSpeechRecognitionModule.requestPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Microphone needed', 'Allow the microphone to speak your declaration, or switch to typing.');
      return;
    }
    if (!ExpoSpeechRecognitionModule.isRecognitionAvailable()) {
      setNotice('Speech recognition is not available on this phone. Please type the declaration.');
      return;
    }
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    failures.current = 0;
    wantListening.current = true;
    begin();
  };

  const restart = () => {
    finalsRef.current = '';
    setFinals('');
    setInterim('');
    setNotice(null);
  };

  const switchMode = (next: Mode) => {
    if (next === mode) return;
    wantListening.current = false;
    try {
      ExpoSpeechRecognitionModule.abort();
    } catch {}
    setListening(false);
    setMode(next);
  };

  if (alreadyDone) {
    const method = data.days[today]?.sections.declaration?.method;
    return (
      <View style={styles.doneWrap}>
        <MaterialCommunityIcons name="check-decagram" size={96} color={colors.gold} />
        <Text style={styles.doneTitle}>Declaration done</Text>
        <Text style={styles.doneSub}>
          You {method === 'type' ? 'wrote' : 'spoke'} it today. Come back tomorrow to keep the chain alive.
        </Text>
      </View>
    );
  }

  let cursor = 0;
  const words = DISPLAY_WORDS.map(({ word, count }, i) => {
    const start = cursor;
    cursor += count;
    let state: 'idle' | 'ok' | 'bad' = 'idle';
    if (mode === 'voice') {
      const slice = matched.slice(start, start + count);
      if (count > 0 && slice.every(Boolean)) state = 'ok';
    } else if (start + count <= typedState.correct) {
      state = 'ok';
    } else if (typedState.mistake && start <= typedState.correct && typedState.correct < start + count) {
      state = 'bad';
    }
    return (
      <Text
        key={i}
        style={[styles.word, mode === 'type' && styles.wordSmall, state === 'ok' && styles.wordOk, state === 'bad' && styles.wordBad]}
      >
        {word}{' '}
      </Text>
    );
  });

  const percent = Math.round(Math.min(1, ratio / (mode === 'voice' ? VOICE_PASS_RATIO : 1)) * 100);

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior="padding">
      <ScrollView ref={scrollRef} contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
        <Text style={styles.kicker}>WARRIOR DECLARATION</Text>
        <Text style={styles.heading}>Say it like you mean it.</Text>

        <View style={styles.switcher}>
          <Pressable style={[styles.switchItem, mode === 'voice' && styles.switchOn]} onPress={() => switchMode('voice')}>
            <MaterialCommunityIcons name="microphone" size={18} color={mode === 'voice' ? '#04140A' : colors.muted} />
            <Text style={[styles.switchText, mode === 'voice' && { color: '#04140A' }]}>Speak</Text>
          </Pressable>
          <Pressable style={[styles.switchItem, mode === 'type' && styles.switchOn]} onPress={() => switchMode('type')}>
            <MaterialCommunityIcons name="keyboard-outline" size={18} color={mode === 'type' ? '#04140A' : colors.muted} />
            <Text style={[styles.switchText, mode === 'type' && { color: '#04140A' }]}>Type</Text>
          </Pressable>
        </View>

        <Card style={{ marginTop: 16 }}>
          <Text style={styles.textBlock}>{words}</Text>
        </Card>

        <View style={styles.progressRow}>
          <View style={styles.track}>
            <View style={[styles.fill, { width: `${percent}%` }]} />
          </View>
          <Text style={styles.percent}>{percent}%</Text>
        </View>

        {mode === 'voice' ? (
          <View style={styles.voiceBox}>
            <Pressable onPress={toggleMic} style={[styles.mic, listening && styles.micLive]}>
              <MaterialCommunityIcons name={listening ? 'stop' : 'microphone'} size={44} color={listening ? '#fff' : '#04140A'} />
            </Pressable>
            <Text style={styles.micHint}>
              {listening ? 'Listening... speak the full declaration' : 'Tap the mic and read it out loud'}
            </Text>
            <Text style={styles.small}>
              Words turn green as you say them. It completes automatically when you are {Math.round(VOICE_PASS_RATIO * 100)}% there.
            </Text>
            {finals.length > 0 && !listening ? <Button compact variant="ghost" label="Start over" icon="refresh" onPress={restart} style={{ marginTop: 12 }} /> : null}
          </View>
        ) : (
          <View style={{ marginTop: 6 }}>
            <TextInput
              value={typed}
              onChangeText={setTyped}
              multiline
              autoCapitalize="none"
              autoCorrect={false}
              spellCheck={false}
              autoComplete="off"
              contextMenuHidden
              placeholder="Type the declaration here..."
              onFocus={() => setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 250)}
              placeholderTextColor={colors.muted}
              style={[styles.input, typedState.mistake && { borderColor: colors.danger }]}
            />
            <Text style={[styles.small, typedState.mistake && { color: colors.danger }]}>
              {typedState.mistake
                ? 'Check the highlighted word. It must match exactly.'
                : 'Type it word for word. Capital letters and punctuation do not matter.'}
            </Text>
          </View>
        )}

        {notice ? <Text style={styles.notice}>{notice}</Text> : null}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  scroll: { padding: 16, paddingBottom: 60 },
  kicker: { color: colors.gold, fontSize: 12, fontWeight: '900', letterSpacing: 2.4 },
  heading: { color: colors.text, fontSize: 28, fontWeight: '900', marginTop: 4 },
  switcher: { flexDirection: 'row', backgroundColor: colors.card, borderRadius: 14, padding: 4, marginTop: 16, borderWidth: 1, borderColor: colors.border },
  switchItem: { flex: 1, flexDirection: 'row', gap: 8, alignItems: 'center', justifyContent: 'center', paddingVertical: 11, borderRadius: 11 },
  switchOn: { backgroundColor: colors.green },
  switchText: { color: colors.muted, fontWeight: '800', fontSize: 15 },
  textBlock: { lineHeight: 32 },
  word: { color: colors.muted, fontSize: 21, fontWeight: '700' },
  wordSmall: { fontSize: 16 },
  wordOk: { color: colors.green },
  wordBad: { color: colors.danger, textDecorationLine: 'underline' },
  progressRow: { flexDirection: 'row', alignItems: 'center', gap: 12, marginTop: 16 },
  track: { flex: 1, height: 10, borderRadius: 5, backgroundColor: colors.card, overflow: 'hidden' },
  fill: { height: 10, borderRadius: 5, backgroundColor: colors.gold },
  percent: { color: colors.gold, fontWeight: '900', width: 44, textAlign: 'right' },
  voiceBox: { alignItems: 'center', marginTop: 26 },
  mic: { width: 108, height: 108, borderRadius: 54, backgroundColor: colors.green, alignItems: 'center', justifyContent: 'center', elevation: 8 },
  micLive: { backgroundColor: colors.danger },
  micHint: { color: colors.text, fontSize: 16, fontWeight: '700', marginTop: 16, textAlign: 'center' },
  small: { color: colors.muted, fontSize: 12, marginTop: 8, textAlign: 'center', lineHeight: 18 },
  input: {
    minHeight: 130,
    textAlignVertical: 'top',
    color: colors.text,
    fontSize: 18,
    lineHeight: 26,
    backgroundColor: colors.card,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: colors.border,
    padding: 14,
    marginTop: 8,
  },
  notice: { color: colors.gold, textAlign: 'center', marginTop: 16, fontSize: 13 },
  doneWrap: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  doneTitle: { color: colors.text, fontSize: 26, fontWeight: '900', marginTop: 16 },
  doneSub: { color: colors.muted, fontSize: 15, textAlign: 'center', marginTop: 8, lineHeight: 22 },
});
