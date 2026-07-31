'use client'

import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'

interface Props {
  value: string      // "HH:mm" or ""
  onChange: (value: string) => void
  disabled?: boolean
  placeholder?: string
}

const HOURS = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0'))
const MINUTES = Array.from({ length: 12 }, (_, i) => String(i * 5).padStart(2, '0'))

export function TimePicker({ value, onChange, disabled, placeholder }: Props) {
  const parts = value ? value.split(':') : []
  const h = parts[0] ?? ''
  const m = parts[1] ?? ''

  const handleHour = (hour: string) => {
    onChange(`${hour}:${m || '00'}`)
  }

  const handleMinute = (minute: string) => {
    // Radix Select closes automatically after this — exactly what the user wants
    onChange(`${h || '08'}:${minute}`)
  }

  return (
    <div className="flex items-center gap-1.5">
      <Select value={h} onValueChange={handleHour} disabled={disabled}>
        <SelectTrigger className="w-[72px]">
          <SelectValue placeholder="--" />
        </SelectTrigger>
        <SelectContent className="max-h-48">
          {HOURS.map((hour) => (
            <SelectItem key={hour} value={hour}>{hour}</SelectItem>
          ))}
        </SelectContent>
      </Select>
      <span className="text-muted-foreground font-medium">:</span>
      <Select value={m} onValueChange={handleMinute} disabled={disabled}>
        <SelectTrigger className="w-[72px]">
          <SelectValue placeholder="--" />
        </SelectTrigger>
        <SelectContent>
          {MINUTES.map((min) => (
            <SelectItem key={min} value={min}>{min}</SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
