const fmt = new Intl.DateTimeFormat('en-US', {
  year: 'numeric',
  month: 'short',
  day: '2-digit',
})

export function formatDate(value: string | number | Date): string {
  return fmt.format(new Date(value))
}
