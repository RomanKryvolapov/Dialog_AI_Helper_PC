import sys
import os
import glob
import sysconfig
import traceback
from faster_whisper import WhisperModel


def _enable_cuda_dlls():
    """Make CUDA DLLs discoverable so the GPU can be used on Windows.

    torch ships a complete, self-consistent set of CUDA libraries (cudart/cublas/cudnn)
    in torch/lib; we add that folder (and any standalone nvidia/* packages) to the DLL
    search path so CTranslate2 can load them.
    """
    try:
        site = sysconfig.get_paths()["purelib"]
        candidates = [os.path.join(site, "torch", "lib")]
        candidates += glob.glob(os.path.join(site, "nvidia", "*", "bin"))
        for dll_dir in candidates:
            if os.path.isdir(dll_dir):
                os.add_dll_directory(dll_dir)
    except Exception as e:
        print(f"Whisper: could not register CUDA DLL dirs: {e}", file=sys.stderr)

def main():

    if len(sys.argv) < 4:
        print("Whisper: arguments not found (need audio_path, model_dir, language)", file=sys.stderr)
        sys.exit(1)

    audio_path = sys.argv[1]
    model_dir = sys.argv[2]
    language = sys.argv[3]

    if not os.path.isfile(audio_path):
        print(f"Whisper: audio file not found: {audio_path}", file=sys.stderr)
        sys.exit(1)

    if not os.path.isdir(model_dir):
        print(f"Whisper: model directory not found: {model_dir}", file=sys.stderr)
        sys.exit(1)

    if not language:
        print("Whisper: language is empty", file=sys.stderr)
        sys.exit(1)

    _enable_cuda_dlls()

    text = None
    last_error = None
    for dev in ("cuda", "cpu"):
        try:
            model = WhisperModel(model_size_or_path=model_dir, compute_type="int8", device=dev)
            segments, _ = model.transcribe(audio_path, language=language)
            text = " ".join(seg.text for seg in segments)
            break
        except Exception as e:
            last_error = e
            print(f"Whisper: {dev} failed: {e}", file=sys.stderr)
    if text is None:
        print(f"Whisper: transcription failed on all devices: {last_error}", file=sys.stderr)
        traceback.print_exc()
        sys.exit(1)

    print(text)

if __name__ == "__main__":
    main()
