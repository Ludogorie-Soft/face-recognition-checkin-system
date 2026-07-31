'use client'

import { ClipboardCheck } from 'lucide-react'

// ── Ring geometry ──────────────────────────────────────────────────────────────

const R  = 38
const CX = 50
const CY = 50
const C  = 2 * Math.PI * R   // full circumference ≈ 238.76

// ── Component ──────────────────────────────────────────────────────────────────

interface Props {
  /** 0–1 for determinate; omit for indeterminate comet animation */
  progress?: number
  label?: string
  sublabel?: string
}

export function SyncLoader({ progress, label, sublabel }: Props) {
  const isDeterminate = progress !== undefined
  const pct = isDeterminate ? Math.round(Math.min(progress!, 1) * 100) : null
  const dashOffset = isDeterminate ? C * (1 - Math.min(progress!, 1)) : 0

  return (
    <div className="flex flex-col items-center gap-6">

      {/* Ring */}
      <div className="relative w-40 h-40">
        <svg viewBox="0 0 100 100" className="w-full h-full" aria-hidden>
          <defs>
            <filter id="sl-glow" x="-50%" y="-50%" width="200%" height="200%">
              <feGaussianBlur in="SourceGraphic" stdDeviation="1.6" result="blur" />
              <feMerge>
                <feMergeNode in="blur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>

          {/* Faint track */}
          <circle
            cx={CX} cy={CY} r={R}
            fill="none" strokeWidth="8"
            className="stroke-primary"
            opacity="0.12"
          />

          {isDeterminate ? (
            /* Smooth arc fill — grows from top */
            <circle
              cx={CX} cy={CY} r={R}
              fill="none" strokeWidth="8"
              strokeLinecap="round"
              className="stroke-primary"
              strokeDasharray={C}
              strokeDashoffset={dashOffset}
              transform={`rotate(-90 ${CX} ${CY})`}
              filter="url(#sl-glow)"
              style={{ transition: 'stroke-dashoffset 0.45s cubic-bezier(0.4,0,0.2,1)' }}
            />
          ) : (
            /* Comet arc — pure CSS spin, no JS state */
            <circle
              cx={CX} cy={CY} r={R}
              fill="none" strokeWidth="8"
              strokeLinecap="round"
              className="stroke-primary"
              strokeDasharray={`${C * 0.28} ${C * 0.72}`}
              filter="url(#sl-glow)"
              style={{
                animation: 'spin 1.4s linear infinite',
                transformBox: 'fill-box',
                transformOrigin: 'center',
              }}
            />
          )}
        </svg>

        {/* Center */}
        <div className="absolute inset-0 flex items-center justify-center">
          {pct !== null ? (
            <span className="text-xl font-bold text-foreground tabular-nums leading-none">
              {pct}%
            </span>
          ) : (
            <ClipboardCheck size={26} className="text-primary" style={{ opacity: 0.6 }} />
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
