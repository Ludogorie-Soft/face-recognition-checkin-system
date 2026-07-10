import axios from 'axios'

// Known error codes from the backend ErrorCode enum
export type ErrorCode =
  | 'INVALID_CREDENTIALS'
  | 'ACCESS_DENIED'
  | 'USER_NOT_FOUND'
  | 'COMPANY_NOT_FOUND'
  | 'SITE_NOT_FOUND'
  | 'DUPLICATE_EMAIL'
  | 'PASSWORD_REQUIRED'
  | 'WRONG_ROLE'
  | 'ALREADY_ASSIGNED'
  | 'ASSIGNMENT_NOT_FOUND'
  | 'SITE_NOT_ASSIGNED'
  | 'WORKER_NOT_IN_COMPANY'
  | 'FACE_ALREADY_REGISTERED'
  | 'DUPLICATE_ATTENDANCE'
  | 'VALIDATION_ERROR'
  | 'INTERNAL_ERROR'
  | 'UNKNOWN'

// Extract the backend ErrorCode string from an axios error
export function getApiErrorCode(error: unknown): ErrorCode {
  if (axios.isAxiosError(error)) {
    const code = error.response?.data?.code
    if (typeof code === 'string') return code as ErrorCode
  }
  return 'UNKNOWN'
}

// Return a translated error message.
// te = useTranslations('errors')
export function apiErrorMessage(
  te: (key: string) => string,
  error: unknown
): string {
  const code = getApiErrorCode(error)
  try {
    return te(code)
  } catch {
    return te('UNKNOWN')
  }
}
