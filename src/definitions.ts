export interface PushAppPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
