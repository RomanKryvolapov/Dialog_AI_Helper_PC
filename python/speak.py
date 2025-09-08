import sys
import torch
from TTS.api import TTS
from TTS.tts.configs.xtts_config import XttsConfig
from TTS.tts.models.xtts import XttsAudioConfig, XttsArgs
from TTS.config.shared_configs import BaseDatasetConfig
from scipy.io.wavfile import read
import sounddevice as sd

torch.serialization.add_safe_globals([
    XttsConfig,
    XttsAudioConfig,
    XttsArgs,
    BaseDatasetConfig
])

def main():
    print("Synthesizing response")
    text = sys.argv[1]
    speaker_wav = sys.argv[2]
    output_path="output.wav"
    tts = TTS(
        model_name="tts_models/multilingual/multi-dataset/xtts_v2",
        gpu=torch.cuda.is_available()
    )
    tts.tts_to_file(
        text=text,
        file_path=output_path,
        language="ru",
        speaker_wav=speaker_wav
    )
    samplerate, data = read(output_path)
    sd.play(data, samplerate=samplerate)
    sd.wait()
    print("Speak:", output_path)
