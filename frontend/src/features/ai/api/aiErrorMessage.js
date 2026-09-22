// Provider details remain in the Network response, never in visitor-facing copy.
export default function aiErrorMessage(error) {
  const code = error?.response?.data?.code;
  if (code === 'AI_PROVIDER_QUOTA' || code === 'AI_APP_RATE_LIMIT') {
    return 'AI has reached its usage limit for now. Please try again later. You can still browse the news.';
  }
  if (code === 'AI_BUSY') return 'AI is helping with another request. Please try again in a moment.';
  if (code === 'AI_TIMEOUT' || error?.code === 'ECONNABORTED') {
    return 'AI is taking longer than expected. Please try again shortly. Your news is still available.';
  }
  if (code === 'AI_INVALID_REQUEST') return 'We couldn’t use this selection. Refresh the news and try again.';
  return 'AI is temporarily unavailable. Please try again later. You can still browse the news.';
}
