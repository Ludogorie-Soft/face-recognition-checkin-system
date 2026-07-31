'use client'

import { useEffect, useState } from 'react'

// ── Ring geometry ──────────────────────────────────────────────────────────────

const SEGS = 12
const CX = 50
const CY = 50
const R = 36
const C = 2 * Math.PI * R        // full circumference ≈ 226.2
const SLOT = C / SEGS             // arc length per segment slot
const DASH = SLOT * 0.72          // visible dash (28 % gap between segments)

// ── Component ──────────────────────────────────────────────────────────────────

interface Props {
  /** 0–1 for determinate; omit for indeterminate sweep animation */
  progress?: number
  label?: string
  sublabel?: string
}

export function SyncLoader({ progress, label, sublabel }: Props) {
  const [pulse, setPulse] = useState(0)

  // Indeterminate: segments fill clockwise, then all clear, then repeat
  useEffect(() => {
    if (progress !== undefined) return
    let tick = 0
    const id = setInterval(() => {
      tick = (tick + 1) % (SEGS + 6) // 6-tick pause after full fill
      setPulse(Math.min(tick, SEGS))
    }, 110)
    return () => clearInterval(id)
  }, [progress])

  const filled = progress !== undefined
    ? Math.round(Math.min(progress, 1) * SEGS)
    : pulse

  const pct = progress !== undefined ? Math.round(progress * 100) : null

  return (
    <div className="flex flex-col items-center gap-5">

      {/* Ring */}
      <div className="relative w-36 h-36">
        <svg viewBox="0 0 100 100" className="w-full h-full">
          <g transform={`rotate(-90 ${CX} ${CY})`}>
            {Array.from({ length: SEGS }, (_, i) => {
              const isFilled = i < filled
              return (
                <circle
                  key={i}
                  cx={CX}
                  cy={CY}
                  r={R}
                  fill="none"
                  strokeWidth="10"
                  strokeLinecap="round"
                  strokeDasharray={`${DASH} ${C - DASH}`}
                  strokeDashoffset={-(i * SLOT)}
                  className={isFilled ? 'stroke-primary' : 'stroke-border'}
                  style={{
                    opacity: isFilled ? 1 : 0.35,
                    transition: 'stroke 0.15s ease, opacity 0.15s ease',
                  }}
                />
              )
            })}
          </g>
        </svg>

        {/* Center content */}
        <div className="absolute inset-0 flex items-center justify-center">
          {pct !== null ? (
            <span className="text-lg font-bold text-foreground tabular-nums leading-none">
              {pct}%
            </span>
          ) : (
            <span className="block w-2.5 h-2.5 rounded-full bg-primary animate-pulse" />
          )}
        </div>
      </div>

      {/* Labels */}
      {(label || sublabel) && (
        <div className="flex flex-col items-center gap-1 text-center">
          {label && (
            <p className="text-sm font-medium text-foreground">{label}</p>
          )}
          {sublabel && (
            <p className="text-xs text-muted-foreground">{sublabel}</p>
          )}
        </div>
      )}
    </div>
  )
}
