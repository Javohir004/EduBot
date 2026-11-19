import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react'
import { useId } from 'react'

export function Screen({ title, children, action }: { title?: string; children: ReactNode; action?: ReactNode }) {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-xl flex-col gap-4 px-4 pt-5 pb-10">
      {title && (
        <header className="flex items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          {action}
        </header>
      )}
      {children}
    </main>
  )
}

export function Section({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`rounded-2xl bg-section p-4 shadow-sm ${className}`}>{children}</section>
}

type ButtonVariant = 'primary' | 'secondary' | 'ghost'

const buttonStyles: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-accent-text',
  secondary: 'bg-surface text-text',
  ghost: 'bg-transparent text-link',
}

export function Button({
  variant = 'primary',
  loading = false,
  className = '',
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; loading?: boolean }) {
  return (
    <button
      {...props}
      disabled={disabled || loading}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 text-[15px] font-medium transition active:scale-[0.98] disabled:opacity-50 ${buttonStyles[variant]} ${className}`}
    >
      {loading && <Spinner small />}
      {children}
    </button>
  )
}

export function Field({
  label,
  hint,
  error,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string }) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-hint">
        {label}
      </label>
      <input
        id={id}
        {...props}
        aria-invalid={error ? true : undefined}
        className="min-h-11 rounded-xl border border-separator bg-bg px-3 text-[16px] text-text outline-none focus:border-accent"
      />
      {error ? (
        <p className="text-sm text-danger">{error}</p>
      ) : (
        hint && <p className="text-xs text-hint">{hint}</p>
      )}
    </div>
  )
}

export function SelectField({
  label,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string }) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-hint">
        {label}
      </label>
      <select
        id={id}
        {...props}
        className="min-h-11 rounded-xl border border-separator bg-bg px-3 text-[16px] text-text outline-none focus:border-accent"
      >
        {children}
      </select>
    </div>
  )
}

export function Spinner({ small = false }: { small?: boolean }) {
  return (
    <span
      role="status"
      className={`inline-block animate-spin rounded-full border-2 border-current border-t-transparent ${small ? 'size-4' : 'size-7 text-accent'}`}
    />
  )
}

export function FullScreenMessage({
  icon,
  title,
  text,
  action,
}: {
  icon: string
  title: string
  text?: string
  action?: ReactNode
}) {
  return (
    <main className="mx-auto flex min-h-dvh max-w-sm flex-col items-center justify-center gap-3 px-6 text-center">
      <div className="text-5xl" aria-hidden>
        {icon}
      </div>
      <h1 className="text-xl font-semibold">{title}</h1>
      {text && <p className="text-[15px] leading-relaxed text-hint">{text}</p>}
      {action}
    </main>
  )
}

export function EmptyState({ title, text, action }: { title: string; text?: string; action?: ReactNode }) {
  return (
    <Section className="flex flex-col items-center gap-2 py-10 text-center">
      <p className="font-medium">{title}</p>
      {text && <p className="max-w-xs text-sm text-hint">{text}</p>}
      {action}
    </Section>
  )
}

export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'good' | 'warn' | 'bad' }) {
  const tones = {
    neutral: 'bg-surface text-hint',
    good: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400',
    warn: 'bg-amber-500/15 text-amber-700 dark:text-amber-400',
    bad: 'bg-red-500/15 text-red-700 dark:text-red-400',
  }
  return <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${tones[tone]}`}>{children}</span>
}

export function SearchInput({ value, onChange, placeholder }: { value: string; onChange(v: string): void; placeholder: string }) {
  return (
    <input
      type="search"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      className="min-h-11 w-full rounded-xl bg-section px-4 text-[16px] text-text shadow-sm outline-none placeholder:text-hint"
    />
  )
}

export function Segmented<T extends string>({
  value,
  options,
  onChange,
}: {
  value: T
  options: { value: T; label: string }[]
  onChange(value: T): void
}) {
  return (
    <div className="flex rounded-xl bg-section p-1 shadow-sm" role="tablist">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="tab"
          aria-selected={o.value === value}
          onClick={() => onChange(o.value)}
          className={`flex-1 rounded-lg py-1.5 text-sm font-medium transition ${o.value === value ? 'bg-accent text-accent-text' : 'text-hint'}`}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

export function ListRow({
  title,
  subtitle,
  trailing,
  onClick,
}: {
  title: ReactNode
  subtitle?: ReactNode
  trailing?: ReactNode
  onClick?(): void
}) {
  const content = (
    <>
      <span className="min-w-0">
        <span className="block truncate font-medium">{title}</span>
        {subtitle && <span className="block truncate text-sm text-hint">{subtitle}</span>}
      </span>
      {trailing ?? (onClick && <span className="text-hint">›</span>)}
    </>
  )
  const className = 'flex w-full items-center justify-between gap-3 px-4 py-3 text-left'
  return onClick ? (
    <button type="button" onClick={onClick} className={`${className} active:bg-surface`}>
      {content}
    </button>
  ) : (
    <div className={className}>{content}</div>
  )
}

export function TextArea({ label, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string }) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-hint">
        {label}
      </label>
      <textarea
        id={id}
        {...props}
        rows={3}
        className="rounded-xl border border-separator bg-bg px-3 py-2 text-[16px] text-text outline-none focus:border-accent"
      />
    </div>
  )
}

export function Loading() {
  return (
    <div className="flex justify-center py-10">
      <Spinner />
    </div>
  )
}
