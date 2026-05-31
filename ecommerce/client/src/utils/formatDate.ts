const dateFmt = new Intl.DateTimeFormat('en-IN', {
  year: 'numeric', month: 'short', day: '2-digit',
})

const dateTimeFmt = new Intl.DateTimeFormat('en-IN', {
  year: 'numeric', month: 'short', day: '2-digit',
  hour: '2-digit', minute: '2-digit',
})

const currencyFmt = new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', maximumFractionDigits: 0,
})

export function formatDate(value: string | number | Date): string {
  return dateFmt.format(new Date(value))
}

export function formatDateTime(value: string | number | Date): string {
  return dateTimeFmt.format(new Date(value))
}

/** Format a number as Indian Rupees: ₹1,23,456 */
export function formatINR(value: number): string {
  return currencyFmt.format(value)
}
