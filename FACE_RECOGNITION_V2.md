# Face Recognition — Вариант 2: MediaPipe + MobileFaceNet ONNX

Заменяме мъртвата библиотека `face-api.js` с модерен pipeline:
- **Детекция + landmarks:** MediaPipe FaceLandmarker (Google, Apache 2.0)
- **Разпознаване:** MobileFaceNet ONNX via onnxruntime-web (MIT)
- **Matching:** Cosine similarity, 512-dim дескриптори

---

## Стъпки

- [x] **Стъпка 1 — Dependencies**
  - Премахване на `face-api.js`
  - Добавяне на `@mediapipe/tasks-vision` и `onnxruntime-web`
  - Конфигурация на ONNX WASM в `next.config.ts`

- [x] **Стъпка 2 — Модели**
  - Изтриване на старите 8 face-api.js модела от `public/models/`
  - Сваляне на `face_landmarker.task` (3.6MB, MediaPipe Apache 2.0)
  - Сваляне на `mobilefacenet.onnx` (13MB, InsightFace w600k_mbf — MobileNet backbone)
  - Копиране на `ort-wasm-simd-threaded.wasm` и variants от node_modules в `public/`

- [x] **Стъпка 3 — `lib/faceAlignment.ts`**
  - Affine transform на face crop от MediaPipe landmarks → 112×112 ImageData
  - Използва iris centre landmarks (468, 473) за изправяне и нормализиране на лицето

- [x] **Стъпка 4 — `lib/faceMatcher.ts`**
  - Cosine similarity 1:N matcher (заменя face-api.js FaceMatcher)
  - Threshold: ≥ 0.45 = match; добавено `distance` поле за съвместимост с VerifyCamera

- [x] **Стъпка 5 — `hooks/useFaceApi.ts` пренаписване**
  - Същият публичен интерфейс: `state`, `detectDescriptor`, `buildMatcher`
  - Вътрешно: MediaPipe FaceLandmarker + ONNX InferenceSession
  - `detectDescriptor` → `Float32Array[512]` вместо `[128]`

- [x] **Стъпка 6 — Backend миграция**
  - `V2__clear_face_descriptors.sql`: изтриване на несъвместими 128-dim дескриптори
  - `FaceDescriptorService.java`: Euclidean → cosine similarity, validation за length 512
  - **Билд: `mvn package -DskipTests` → SUCCESS ✓**

- [x] **Одит 1** (8 проблема открити и отстранени)
  - #1 MediaPipe WASM → копирано в `public/mediapipe/` (offline fix)
  - #2 GPU delegate → try/catch с CPU fallback
  - #3 Canvas per-frame → единичен reusable canvas в `faceAlignment.ts`
  - #4 `FaceLandmarker.close()` → извиква се при unmount
  - #5 Strict Mode race condition → `cancelled` флаг + `loadPromise` singleton
  - #6 `videoWidth === 0` guard → добавен в `alignFace` и `detectDescriptor`
  - #7 Webpack `.wasm` rule → премахнато (ORT ползва `wasmPaths` fetch)
  - #8 `ort.env` при SSR → преместено вътре в `loadModels()` async fn
  - PWA cache → добавени правила за `/mediapipe/` и `/ort-wasm*.wasm`

- [x] **Одит 2** (7 проблема открити и отстранени)
  - #1 `FaceDescriptorRequest.java` `@Size(128→512)` → enrollment вече работи
  - #2 ~60MB бинарни файлове → добавени в `.gitignore`, `postinstall` скрипт копира при `npm install`
  - #3 `mountedRef` dead code → премахнат
  - #4 Cleanup унищожаваше сингълтони → reference counting, `destroyModels()` само при последен unmount
  - #5 WASM header pattern → `/:path*.wasm` → `/(.*)\\.wasm`
  - #6 `_canvas` не се reset → добавен `resetAlignmentCanvas()`, викан от `destroyModels()`
  - #7 WASM копия изостават → `postinstall` script в `package.json` + `scripts/copy-wasm.js`
  - TypeScript: 0 грешки ✓ | Backend build: SUCCESS ✓

- [x] **Одит 3** (5 проблема открити и отстранени)
  - #1 GPU leak при ONNX failure → `newLandmarker.close()` преди rethrow
  - #2 Strict Mode double-init → `destroyModels()` нулира само ако `landmarker !== null`
  - #3 `copy-wasm.js` без error handling → `fs.existsSync` guard + try/catch + `process.exit(1)`
  - #4 `normalize()` връщаше ref вместо копие → `new Float32Array(v.length)` при `mag === 0`
  - #5 `consumerCount` без floor → `Math.max(0, consumerCount - 1)`
  - TypeScript: 0 грешки ✓

- [x] **Одит 4** (4 проблема открити и отстранени)
  - #1 ORT Tensor leak → `inputTensor.dispose()` в finally + `new Float32Array(outputTensor.data)` copy + `outputTensor.dispose()`
  - #2 `dot()` без length guard → `if (a.length !== b.length) return 0`
  - #3 Inconsistent error стратегия в `copy-wasm.js` → per-step try/catch с warnings, без hard exit
  - #4 Stale коментар в `faceAlignment.ts` → премахнат
  - TypeScript: 0 грешки ✓

- [ ] **Стъпка 7 — Тестване и fine-tuning** ← СЛЕДВАЩА СТЪПКА
  - Тест на пълния flow: enrollment → sync → verify
  - Fine-tuning на cosine threshold (0.40–0.50) в `lib/faceMatcher.ts`
  - Проверка на performance на мобилно устройство
  - Всички работници трябва да се пре-регистрират (еднократна операция)

---

## Засегнати файлове

| Файл | Промяна |
|---|---|
| `frontend/package.json` | remove face-api.js, add mediapipe + onnxruntime-web |
| `frontend/next.config.ts` | WASM headers/copy config |
| `frontend/public/models/` | замяна на 8 стари → 2 нови модела |
| `frontend/public/ort-wasm-simd.wasm` | нов |
| `frontend/lib/faceAlignment.ts` | нов файл |
| `frontend/lib/faceMatcher.ts` | нов файл |
| `frontend/hooks/useFaceApi.ts` | пълно пренаписване |
| `frontend/components/workers/FaceRegisterModal.tsx` | вероятно нула промени |
| `frontend/components/verify/VerifyCamera.tsx` | вероятно нула промени |
| `src/.../db/migration/V2__face_descriptor_512dim.sql` | нов |
| `src/.../user/FaceDescriptorService.java` | validation update |

---

## Очаквани резултати

| Метрика | face-api.js | Вариант 2 |
|---|---|---|
| Детекция latency | ~350ms | ~40ms |
| Descriptor dims | 128 | 512 |
| Recognition accuracy | ~87% (LFW) | ~99.5% (LFW) |
| Library maintenance | Мъртва (2020) | Активна |

---

## Бележки

- След завършване на Вариант 2, всички работници трябва да се пре-регистрират (еднократна операция)
- Вариант 3 (YOLOv8-face + ArcFace R50) се изгражда върху същата архитектура — само swap на модели
