/**
 * Slack Incoming Webhook for PushApp native API debug logs.
 *
 * 1. Slack → Apps → Incoming Webhooks → Add to channel
 * 2. Copy the full URL (https://hooks.slack.com/services/T…/B…/…)
 * 3. Paste below — leave empty to disable Slack API logging
 */
export const SLACK_WEBHOOK_URL =
  'https://hooks.slack.com/services/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';

/** Pass to PushApp.initialize only when a real webhook URL is set. */
export function slackWebhookInitOption(): { slackWebhookUrl?: string } {
  const url = SLACK_WEBHOOK_URL.trim();
  return url ? { slackWebhookUrl: url } : {};
}
