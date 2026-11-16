import type { ShortLink } from './types';

export class ApiError extends Error {
  constructor(message: string, public status: number) {
    super(message);
  }
}

export async function shortenUrl(longUrl: string): Promise<ShortLink> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch('/api/v1/links', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ long_url: longUrl, redirect_status: 302 }),
      signal: controller.signal,
    });
    if (!response.ok) {
      if (response.status === 422) {
        throw new ApiError('Liên kết chưa hợp lệ. Hãy kiểm tra địa chỉ HTTP/HTTPS và thử lại.', 422);
      }
      throw new ApiError('Máy chủ chưa xử lý được yêu cầu. Bạn hãy thử lại.', response.status);
    }
    return await response.json() as ShortLink;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError('Chưa kết nối được máy chủ. Bạn hãy thử lại.', 0);
  } finally {
    window.clearTimeout(timeout);
  }
}
