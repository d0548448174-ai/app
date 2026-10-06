import random
import subprocess
from pathlib import Path

import gradio as gr
import soundfile as sf
import torch
from transformers import AutoProcessor, MusicgenForConditionalGeneration

MODEL_ID = "facebook/musicgen-small"
OUT_DIR = Path("generated")
OUT_DIR.mkdir(exist_ok=True)

GENRES = {
    "Pop": "modern pop, melodic hooks, polished production",
    "Rock": "energetic rock, electric guitar, live drums, strong melody",
    "Jazz": "jazz ensemble, expressive harmony, acoustic instruments",
    "Classical": "orchestral classical music, strings, piano, rich harmony",
    "Electronic": "creative electronic music, synthesizers, layered textures",
    "Hip-Hop": "instrumental hip-hop, punchy drums, bass, melodic samples",
    "Ambient": "atmospheric ambient music, evolving pads, spacious soundscape",
    "Cinematic": "cinematic soundtrack, dramatic orchestration, evolving sections",
    "Reggae": "reggae groove, bass guitar, guitar skank, warm instrumentation",
    "Latin": "latin music, percussion, melodic guitar, lively rhythm",
}

MOODS = {
    "שמח": "uplifting and joyful",
    "רגוע": "calm and peaceful",
    "אפי": "epic and powerful",
    "מסתורי": "mysterious and atmospheric",
    "אנרגטי": "high energy and exciting",
    "מרגש": "emotional and expressive",
}

print("Loading MusicGen model...")
device = "cuda" if torch.cuda.is_available() else "cpu"
dtype = torch.float16 if device == "cuda" else torch.float32
processor = AutoProcessor.from_pretrained(MODEL_ID)
model = MusicgenForConditionalGeneration.from_pretrained(MODEL_ID, torch_dtype=dtype)
model.to(device)
model.eval()
print("MusicGen ready on", device)


def wav_to_mp3(wav_path, mp3_path):
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error",
        "-i", str(wav_path),
        "-codec:a", "libmp3lame", "-b:a", "192k",
        str(mp3_path)
    ], check=True)


def generate_music(description, genre, mood, duration, seed):
    description = (description or "").strip()
    if not description:
        description = "a completely original instrumental melody"

    seed = int(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)

    prompt = (
        f"{GENRES[genre]}, {MOODS[mood]}. {description}. "
        "Original instrumental composition with changing musical phrases, "
        "melody, harmony, dynamics and natural variation; not a simple repeated loop."
    )

    inputs = processor(text=[prompt], padding=True, return_tensors="pt")
    inputs = {k: v.to(device) for k, v in inputs.items()}

    with torch.inference_mode():
        audio_values = model.generate(
            **inputs,
            do_sample=True,
            guidance_scale=3.0,
            max_new_tokens=int(max(1, duration)) * 50,
        )

    audio = audio_values[0].detach().float().cpu().numpy()
    if audio.ndim == 2:
        audio = audio[0]

    sr = model.config.audio_encoder.sampling_rate
    wav_path = OUT_DIR / f"music_{seed}.wav"
    mp3_path = OUT_DIR / f"music_{seed}.mp3"

    sf.write(wav_path, audio, sr)
    wav_to_mp3(wav_path, mp3_path)
    return str(mp3_path), f"נוצר שיר חדש | ז'אנר: {genre} | Seed: {seed}"


with gr.Blocks(title="Music AI Offline") as demo:
    gr.Markdown("# Music AI Offline")
    gr.Markdown("יוצר מוזיקה חדשה באמצעות AI מקומי ושומר MP3.")

    with gr.Row():
        with gr.Column():
            description = gr.Textbox(
                label="מה ליצור?",
                placeholder="לדוגמה: מנגינה עם פסנתר וגיטרה שמתחילה רגוע ומתפתחת לסיום גדול",
                lines=3,
            )
            genre = gr.Dropdown(list(GENRES.keys()), value="Pop", label="ז'אנר")
            mood = gr.Dropdown(list(MOODS.keys()), value="מרגש", label="אווירה")
            duration = gr.Slider(4, 30, value=15, step=1, label="אורך בשניות")
            seed = gr.Number(value=random.randint(1, 999999), precision=0, label="Seed")
            generate = gr.Button("צור שיר חדש", variant="primary")

        with gr.Column():
            audio = gr.Audio(label="השיר שנוצר", type="filepath")
            status = gr.Textbox(label="סטטוס")
            gr.Markdown("ה-MP3 נשמר אוטומטית בתיקייה generated.")

    generate.click(generate_music, [description, genre, mood, duration, seed], [audio, status])

if __name__ == "__main__":
    demo.launch(inbrowser=True)
