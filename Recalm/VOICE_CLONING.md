# Using your own voice in Recalm

Yes, Recalm can read with your voice. The voice has to be trained once on a computer with a GPU, then it runs offline on the phone like the other voices.

## 1. Record

- 30 to 60 minutes of clean speech is enough for a good result. More is better.
- Quiet room, same microphone, same distance, calm reading tone (the tone you want for sleep).
- Read Hindi and English sentences, one sentence per file, saved as WAV.
- Keep a `metadata.csv` with one line per file: `filename|exact text you read`.

## 2. Train a Piper voice

Use the free Piper training notebook on Google Colab (search "Piper TTS training Colab"). Start from the Hindi checkpoint `hi_IN-rohan-medium` or `hi_IN-priyamvada-medium` so training needs far less data and time. Fine-tuning for 1000 to 3000 epochs usually takes a few hours on a free Colab GPU.

Export the result. You get two files:

```
my_voice.onnx
my_voice.onnx.json
```

## 3. Make a Recalm voice pack

```
pip install onnx
python tools/make_voice_pack.py my_voice.onnx --out my_voice.zip
```

## 4. Import

Copy `my_voice.zip` to the phone, open Recalm, go to Voices, Your own voice, Import voice, and pick the zip. Tap the new voice to use it.

## Why not instant cloning from a short clip?

Models that copy a voice from a few seconds of audio exist, but they are several gigabytes and need a strong GPU, so they cannot run offline on a phone. A trained Piper voice is small (about 60 MB) and runs smoothly on any phone.
