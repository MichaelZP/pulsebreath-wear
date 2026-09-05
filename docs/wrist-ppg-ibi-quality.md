# Wrist PPG / IBI quality: plain-language limits

This note is intentional and blunt. It is not a clinical paper and not legal advice.
It exists so users and contributors do **not** confuse wellness watch marketing with
beat-to-beat measurement quality.

## The uncomfortable fact

Consumer wrist watches mostly use **photoplethysmography (PPG)** on the skin.
They report heart rate and often “HRV” or inter-beat style intervals derived from
the **pulse wave**, not from an electrocardiogram (ECG) R–R series.

**Manufacturers rarely say this in plain language in marketing.** Packaging and
store copy talk about heart rate, stress, energy, and HRV as if the wrist were a
clinical ECG. In developer docs and papers the caveats appear: motion, weak PPG,
batching, and the fact that pulse intervals are **not** interchangeable with ECG
NN intervals for short-term HRV — especially during paced or controlled breathing.

PulseBreath will **not** launder that into soft wording. If calibration fails with
`TOO_FEW_INTERVALS`, `NO_CLEAR_PEAK`, or falls back to a default pace, that is often
the **signal and the sensor pipeline**, not a “user did breathing wrong” story.

## Not only Samsung

Weak or discontinuous wrist IBI is a **platform class problem**, not a one-brand
meme:

- Wrist PPG / PRV is a known imperfect surrogate for ECG HRV in the literature.
- Controlled breathing and motion make agreement worse, not better.
- Closed apps (e.g. brand “relax” features) hide their estimators; that does not
  prove their raw beat series is research-grade — you simply cannot audit it.

Samsung is **not uniquely broken**, but the Samsung Health Sensor SDK is **unusually
explicit about awkward delivery**:

- Only **0–4 IBI** values per heart-rate event.
- With the display off, data may be **batched**; IBI lists live on the first point,
  others null ([Samsung data specifications](https://developer.samsung.com/health/sensor/guide/data-specifications.html)).
- Heart-rate status codes document weak PPG and motion (`-10`, `-8`, `-2`, etc.)
  ([HeartRateSet](https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/data/ValueKey.HeartRateSet.html)).

Those are engineering facts. Marketing slides omit them.

## What we refuse to claim

- Wrist IBI from this app is **not** validated ECG NN data.
- Pace estimates and alignment scores are **experimental wellness/research UX**,
  not diagnosis, not therapy proof, not “resonance frequency” certification.
- A green UI or a completed session does **not** mean clinical-grade coverage.
- Comparing two consumer watches does **not** validate either against ECG.

If you need ground truth, use a **chest ECG strap** (e.g. Polar H10 class) or a
medical-grade ECG protocol — not another wrist gadget.

## Product stance in this repo

- Keep quality gates honest (including ≥12 eligible IBI where documented).
- Prefer explicit fallback and clear reasons over silent “success”.
- Do not invent missing beats to look smoother on demos.
- Do not turn PRV into “percent stress reduction” marketing copy.

If a vendor’s advertising implies clinical beat quality without saying PPG ≠ ECG
for beat-to-beat work, treat that as **marketing**, not as a measurement contract.
This project documents the gap instead of repeating the slogan.
