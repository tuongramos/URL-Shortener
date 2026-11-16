export interface ShortLink {
  id: number;
  short_code: string;
  short_url: string;
  long_url: string;
  redirect_status: 301 | 302;
  created_at: string;
  reused: boolean;
}
