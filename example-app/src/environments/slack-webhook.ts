/**
 * Optional Slack Incoming Webhook for PushApp native API debug logs (development only).
 *
 * 1. Slack → Apps → Incoming Webhooks → Add to channel
 * 2. Set SLACK_WEBHOOK_URL in your local env or paste below for local dev
 * 3. Never commit a real webhook URL to version control
 */
export const SLACK_WEBHOOK_URL = '';

/** Pass to PushApp.initialize only when a real webhook URL is set. */
export function slackWebhookInitOption(): { slackWebhookUrl?: string } {
  const url = SLACK_WEBHOOK_URL.trim();
  return url ? { slackWebhookUrl: url } : {};
}
