export function formatDateTime(value?: string | null) {
  if (!value) {
    return 'N/A';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'Invalid date';
  }

  // Some environments/polyfills throw on newer options like dateStyle/timeStyle.
  // Prefer the widely supported field-based format and fall back to toLocaleString.
  try {
    return new Intl.DateTimeFormat(undefined, {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      timeZoneName: 'short',
    }).format(date);
  } catch {
    return date.toLocaleString();
  }
}

export function getBrowserTimeZone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    return 'UTC';
  }
}

export function formatTimeZoneLabel(timeZone?: string | null) {
  if (!timeZone) {
    return 'Local time';
  }

  try {
    const formatter = new Intl.DateTimeFormat('en-US', {
      timeZone,
      timeZoneName: 'short',
      hour: '2-digit',
    });
    const zonePart = formatter.formatToParts(new Date()).find((part) => part.type === 'timeZoneName')?.value;
    if (zonePart) {
      return zonePart;
    }
  } catch {
    // fall through
  }

  return timeZone;
}

export function getLocalTimeZoneLabel() {
  return formatTimeZoneLabel(getBrowserTimeZone());
}
