const LOCAL_HOSTNAMES = new Set(['localhost', '127.0.0.1']);

export function resolveApiBaseUrl() {
  const configured = process.env.REACT_APP_API_URL || '/api';

  if (typeof window === 'undefined') {
    return configured;
  }

  // If the app is opened from another machine (LAN), a hardcoded localhost API URL
  // would incorrectly point to the *viewer* machine. In that case, rewrite the host
  // to the current page hostname.
  try {
    const url = new URL(configured);
    const pageHost = window.location.hostname;
    if (!LOCAL_HOSTNAMES.has(pageHost) && LOCAL_HOSTNAMES.has(url.hostname)) {
      url.hostname = pageHost;
      return url.toString().replace(/\/$/, '');
    }
    return configured;
  } catch {
    // Relative URL like "/api"
    return configured;
  }
}

export function buildSocketUrlFromApiBase(apiBaseUrl: string) {
  if (apiBaseUrl.startsWith('http://') || apiBaseUrl.startsWith('https://')) {
    return apiBaseUrl.replace(/\/api\/?$/, '/api/ws');
  }
  return `${apiBaseUrl.replace(/\/$/, '')}/ws`;
}

