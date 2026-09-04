# PulseBreath validation protocol v1

Status: proposed formal protocol; preliminary self-test performed separately
Scope: engineering feasibility of watch IBI-derived summary metrics, not medical validation

Implementation readiness: the existing real sensor screen uses a rolling
60-second window, a manual stop button, and instantaneous BPM. It does not yet
support exact fixed-window recording with a displayed mean BPM or simultaneous
breathing guidance. The preliminary self-test checks start/stop and summary
retention only. Do not report it as completion of the formal procedure below.

## Purpose and decision boundary

This protocol tests whether the app can produce reproducible, quality-gated
summary values during quiet, fixed-rate breathing. It does **not** establish
clinical accuracy, diagnose rhythm disorders, validate a resonance frequency,
or establish equivalence to an ECG.

The first run is a self-test only. Adding any other participant pauses the work
until privacy, consent, data-retention, and applicable ethics requirements have
been reviewed. A Polar H10 is optional and may be used only after its own data
export and time-alignment procedure is confirmed.

## Sources and synchronization

| Role | Source | Data used |
| --- | --- | --- |
| Device under test | PulseBreath Samsung build on Galaxy Watch | quality state, valid IBI count, IBI-event coverage, mean BPM, RMSSD |
| Optional comparator | Polar H10 recording application | matching per-window mean BPM and RMSSD, with its documented export method |

Do not put a device serial number, IP address, account identifier, raw IBI
stream, screenshot with personal data, or Polar export in this repository.
Store any authorized study material outside Git in a location selected by the
participant. Use only a local anonymous trial label such as `S01-T03` in a
separate study sheet.

Start both recordings from a visible synchronized countdown. Record the planned
start time and each 60-second window number, not a persistent device ID. If the
two systems cannot be aligned to the same planned window, exclude that window;
do not force a beat-to-beat match or shift data after seeing the result.

## Controlled self-test procedure

1. Confirm the watch has a secure fit, sufficient battery, and that the
   Samsung Health Sensor Service developer mode is enabled for local testing.
2. Sit quietly, keep the watch arm supported, avoid talking, and wait two
   minutes before the first recorded window.
3. Start the watch recording and the optional comparator from the synchronized
   countdown. Use the app's fixed guided pace; do not run multi-rate
   calibration in this protocol.
4. Record three planned 60-second windows while seated and still. Mark any
   interruption, adjustment, speech, cough, movement, disconnect, or restart.
5. Stop both sources. Copy only the predeclared summary fields below into an
   external, access-controlled study sheet. Do not copy raw IBI values into Git.

Stop the session immediately for discomfort, dizziness, chest pain, palpitations
that concern the participant, or any other health concern. The app must not
interpret the event; seek appropriate medical assistance when needed.

## Per-window inclusion and exclusion rules

A watch window is eligible only when all conditions are true:

- the planned duration is exactly 60 seconds;
- its `quality_v1` result is `ADEQUATE`;
- valid IBI count is at least 10 and IBI-event coverage is at least 80%;
- no zero or negative IBI was counted;
- no motion, signal loss, recovery, or application/service interruption occurred
  in that window;
- an optional comparator has a complete, matching planned window.

Exclude, retain the reason, and do not repair a window that fails any rule.
There is no interpolation, resampling, smoothing, outlier deletion, manual
beat editing, or carry-forward of a previous metric. Do not replace a failed
window to achieve a desired sample size; report the number planned, accepted,
and excluded with each reason.

## Predeclared comparison fields and formulas

For every accepted paired window `j`, record:

```text
watch_mean_bpm_j, reference_mean_bpm_j
watch_rmssd_ms_j, reference_rmssd_ms_j
watch_valid_ibi_count_j, watch_ibi_coverage_percent_j
```

Calculate signed device-under-test differences:

```text
delta_bpm_j      = watch_mean_bpm_j - reference_mean_bpm_j
delta_rmssd_j_ms = watch_rmssd_ms_j - reference_rmssd_ms_j
```

For each metric over `n` accepted paired windows, calculate descriptive
agreement:

```text
bias = mean(delta_j)
sample_sd = sqrt(sum((delta_j - bias)^2) / (n - 1))
limits_of_agreement = [bias - 1.96 * sample_sd, bias + 1.96 * sample_sd]
mean_absolute_difference = mean(abs(delta_j))
```

Units: BPM for the BPM comparison, milliseconds for the RMSSD comparison.
Report valid IBI count and coverage separately; they are quality evidence, not
accuracy measures. Correlation may be reported only as descriptive context and
must never be the sole agreement or pass/fail criterion.

No acceptable error limit is set in v1. A numerical result is a feasibility
observation, not a claim that the watch and comparator are interchangeable.
Predeclaring an acceptable limit requires a separate use case, reference-device
specification, sample-size rationale, and review.

## External study-sheet schema

Create this outside the repository, with no names, dates of birth, contact data,
device IDs, locations, or raw streams:

```text
trial_label,window_number,planned_window_seconds,
watch_quality,watch_valid_ibi_count,watch_ibi_coverage_percent,
watch_mean_bpm,watch_rmssd_ms,
reference_present,reference_mean_bpm,reference_rmssd_ms,
included,exclusion_reason,notes
```

`notes` must contain only procedural facts, for example `watch repositioned`.
It must not contain symptoms, diagnoses, or other health narratives.

## Reporting and limitations

Report the app build variant and algorithm versions (`quality_v1`, `alignment_v1`),
the exact posture and cue schedule, number of planned/accepted/excluded windows,
each exclusion reason, all formulas above, and whether a comparator was used.
Report the result as an engineering feasibility observation only.

Wrist PPG-derived intervals can differ from ECG-derived intervals, particularly
with motion; HR and HRV agreement need separate evaluation. This protocol uses
window-level summaries because PulseBreath currently receives batched watch IBI
events and does not claim exact beat timestamps. It therefore cannot validate
beat-level agreement, arrhythmia detection, frequency-domain HRV, clinical
normality, or an individual's resonance frequency.

References: [wearable IBI comparison](https://pubmed.ncbi.nlm.nih.gov/31156103/),
[HRV rigor and reproducibility guidance](https://pubmed.ncbi.nlm.nih.gov/42495990/),
[ultra-short HRV validation review](https://pubmed.ncbi.nlm.nih.gov/33328866/).
