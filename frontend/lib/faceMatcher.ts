/**
 * Cosine-similarity 1:N face matcher.
 *
 * Replaces face-api.js FaceMatcher. Uses cosine similarity instead of
 * Euclidean distance — the correct metric for high-dimensional embeddings
 * (512-dim) produced by ArcFace / MobileFaceNet models.
 *
 * Similarity range: [-1, 1]  →  1 = identical, 0 = unrelated, -1 = opposite
 * Default threshold: 0.45  (empirically good for w600k_mbf on LFW)
 */

export interface LabeledDescriptor {
  id: string
  descriptor: Float32Array
}

export interface MatchResult {
  label: string      // worker id or 'unknown'
  similarity: number // cosine similarity (0–1), higher = better match
  distance: number   // 1 - similarity, for drop-in compatibility with face-api.js API
}

export class FaceMatcher {
  private readonly entries: LabeledDescriptor[]
  private readonly threshold: number

  constructor(entries: LabeledDescriptor[], threshold = 0.45) {
    this.entries = entries.map((e) => ({
      id: e.id,
      descriptor: normalize(e.descriptor),
    }))
    this.threshold = threshold
  }

  findBestMatch(descriptor: Float32Array): MatchResult {
    const norm = normalize(descriptor)
    let bestLabel = 'unknown'
    let bestSim = -Infinity

    for (const entry of this.entries) {
      const sim = dot(norm, entry.descriptor)
      if (sim > bestSim) {
        bestSim = sim
        bestLabel = entry.id
      }
    }

    const clampedSim = Math.max(0, bestSim)

    if (bestSim < this.threshold) {
      return { label: 'unknown', similarity: clampedSim, distance: 1 - clampedSim }
    }

    return { label: bestLabel, similarity: bestSim, distance: 1 - bestSim }
  }
}

function dot(a: Float32Array, b: Float32Array): number {
  if (a.length !== b.length) return 0
  let sum = 0
  for (let i = 0; i < a.length; i++) sum += a[i] * b[i]
  return sum
}

function normalize(v: Float32Array): Float32Array {
  let sum = 0
  for (let i = 0; i < v.length; i++) sum += v[i] * v[i]
  const mag = Math.sqrt(sum)
  const out = new Float32Array(v.length)
  if (mag === 0) return out // zero vector — return zero-filled copy
  for (let i = 0; i < v.length; i++) out[i] = v[i] / mag
  return out
}
