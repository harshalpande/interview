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

export function formatDateTimeCompact(value?: string | null) {
  if (!value) {
    return 'N/A';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'Invalid date';
  }

  try {
    const datePart = new Intl.DateTimeFormat(undefined, {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
    }).format(date);

    const timePart = new Intl.DateTimeFormat(undefined, {
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);

    return `${datePart}, ${timePart} ${getLocalTimeZoneLabel()}`;
  } catch {
    return `${date.toLocaleDateString()} ${date.toLocaleTimeString()}`;
  }
}

export function formatDateTimeSplit(value?: string | null) {
  if (!value) {
    return { dateLabel: 'N/A', timeLabel: '' };
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return { dateLabel: 'Invalid date', timeLabel: '' };
  }

  try {
    const dateLabel = new Intl.DateTimeFormat(undefined, {
      year: 'numeric',
      month: 'long',
      day: '2-digit',
    }).format(date);

    const timeLabel = new Intl.DateTimeFormat(undefined, {
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);

    return {
      dateLabel,
      timeLabel: `${timeLabel} ${getLocalTimeZoneLabel()}`,
    };
  } catch {
    return {
      dateLabel: date.toLocaleDateString(),
      timeLabel: `${date.toLocaleTimeString()} ${getLocalTimeZoneLabel()}`,
    };
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
    if (zonePart && !/^GMT[+-]/i.test(zonePart)) {
      return zonePart;
    }
  } catch {
    // fall through
  }

  const mappedLabel = COMMON_TIME_ZONE_LABELS[timeZone];
  if (mappedLabel) {
    return mappedLabel;
  }

  return timeZone;
}

export function getLocalTimeZoneLabel() {
  return formatTimeZoneLabel(getBrowserTimeZone());
}

const COMMON_TIME_ZONE_LABELS: Record<string, string> = {
  'Asia/Calcutta': 'IST',
  'Asia/Kolkata': 'IST',
  'Asia/Dubai': 'GST',
  'Asia/Singapore': 'SGT',
  'Asia/Tokyo': 'JST',
  'Europe/London': 'GMT',
  'Europe/Berlin': 'CET',
  'Europe/Paris': 'CET',
  'Europe/Amsterdam': 'CET',
  'UTC': 'UTC',
  'Etc/UTC': 'UTC',
  'America/New_York': 'ET',
  'America/Chicago': 'CT',
  'America/Denver': 'MT',
  'America/Los_Angeles': 'PT',
};
