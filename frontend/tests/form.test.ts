import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const html = readFileSync(resolve(process.cwd(), 'index.html'), 'utf8');
const sample = {
  id: 1, short_code: '1', short_url: 'http://127.0.0.1:8000/r/1',
  long_url: 'https://example.com/article?chapter=8#section',
  redirect_status: 302, created_at: '2026-11-12T00:00:00Z', reused: false,
};
const fetchMock = vi.fn();
const clipboard = vi.fn();

function el<T extends HTMLElement>(id: string): T {
  return document.getElementById(id) as T;
}

function submit(url = sample.long_url): void {
  el<HTMLInputElement>('long-url').value = url;
  el('shorten-form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

function respond(payload = sample, status = 201): Response {
  return new Response(JSON.stringify(payload), { status, headers: { 'Content-Type': 'application/json' } });
}

async function successfulSubmit(): Promise<void> {
  submit();
  await vi.waitFor(() => expect(el('result').hidden).toBe(false));
}

beforeEach(async () => {
  vi.resetModules();
  fetchMock.mockReset().mockResolvedValue(respond());
  clipboard.mockReset().mockResolvedValue(undefined);
  vi.stubGlobal('fetch', fetchMock);
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: clipboard } });
  // Dùng HTML thật làm fixture; dữ liệu người dùng được kiểm tra riêng bên dưới.
  document.body.innerHTML = html.split('<body>')[1].split('</body>')[0].replace(/<script[^>]*>[\s\S]*?<\/script>/g, '');
  await import('../src/main');
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('Form rút gọn', () => {
  it('báo lỗi trước khi gọi server và cho sửa rồi gửi lại', async () => {
    submit('ftp://example.com');
    expect(fetchMock).not.toHaveBeenCalled();
    expect(el('url-error').hidden).toBe(false);
    expect(el('long-url').getAttribute('aria-invalid')).toBe('true');
    el('long-url').dispatchEvent(new Event('input'));
    expect(el('url-error').hidden).toBe(true);
    await successfulSubmit();
    expect(el('long-url').hasAttribute('aria-invalid')).toBe(false);
  });

  it('giới hạn độ dài đầu vào', () => {
    submit('https://example.com/' + 'a'.repeat(4096));
    expect(fetchMock).not.toHaveBeenCalled();
    expect(el('url-error').textContent).toContain('4096');
  });

  it('gửi đúng API và không tự truy cập liên kết ngắn', async () => {
    await successfulSubmit();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [path, options] = fetchMock.mock.calls[0];
    expect(path).toBe('/api/v1/links');
    expect(JSON.parse(options.body)).toEqual({ long_url: sample.long_url, redirect_status: 302 });
    expect(el<HTMLAnchorElement>('open-link').href).toBe(sample.short_url);
    expect(el('status').textContent).toContain('thành công');
  });

  it('khóa gửi lặp và mở khóa sau khi có kết quả', async () => {
    let resolve!: (value: Response) => void;
    fetchMock.mockReturnValue(new Promise<Response>((done) => { resolve = done; }));
    submit();
    submit();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(el<HTMLButtonElement>('submit-button').disabled).toBe(true);
    expect(el<HTMLInputElement>('long-url').readOnly).toBe(true);
    expect(el('submit-label').textContent).toBe('Đang rút gọn...');
    resolve(respond());
    await vi.waitFor(() => expect(el<HTMLButtonElement>('submit-button').disabled).toBe(false));
    expect(el<HTMLInputElement>('long-url').readOnly).toBe(false);
  });

  it('hiển thị URL có ký tự HTML như văn bản an toàn', async () => {
    const original = 'https://example.com/?q=<img/src=x/onerror=alert(1)>';
    fetchMock.mockResolvedValue(respond({ ...sample, long_url: original }));
    submit(original);
    await vi.waitFor(() => expect(el('result').hidden).toBe(false));
    expect(el('original-url').textContent).toBe(original);
    expect(el('original-url').querySelector('img')).toBeNull();
  });

  it('thông báo tái sử dụng liên kết', async () => {
    fetchMock.mockResolvedValue(respond({ ...sample, reused: true }, 200));
    await successfulSubmit();
    expect(el('status').textContent).toContain('đã được tạo trước đó');
  });

  it('sao chép đúng URL và thông báo thành công', async () => {
    await successfulSubmit();
    el('copy-button').click();
    await vi.waitFor(() => expect(el('status').textContent).toBe('Đã sao chép liên kết'));
    expect(clipboard).toHaveBeenCalledWith(sample.short_url);
  });

  it('cho chọn và sao chép tay khi clipboard bị chặn', async () => {
    clipboard.mockRejectedValue(new Error('Permission denied'));
    await successfulSubmit();
    el('copy-button').click();
    await vi.waitFor(() => expect(el('manual-copy').hidden).toBe(false));
    const value = el<HTMLInputElement>('copy-value');
    expect(value.value).toBe(sample.short_url);
    expect(document.activeElement).toBe(value);
    expect(value.selectionStart).toBe(0);
    expect(value.selectionEnd).toBe(sample.short_url.length);
  });

  it('giữ đầu vào khi mất kết nối và cho gửi lại', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    submit();
    await vi.waitFor(() => expect(el('status').textContent).toBe('Chưa kết nối được máy chủ. Bạn hãy thử lại.'));
    expect(el<HTMLInputElement>('long-url').value).toBe(sample.long_url);
    expect(el('result').hidden).toBe(true);
    await successfulSubmit();
  });

  it('hiển thị lỗi 422 cạnh ô nhập', async () => {
    fetchMock.mockResolvedValue(new Response('{"detail":"internal parser error"}', { status: 422 }));
    submit();
    await vi.waitFor(() => expect(el('url-error').hidden).toBe(false));
    expect(el('url-error').textContent).toContain('Liên kết chưa hợp lệ');
    expect(document.body.textContent).not.toContain('internal parser error');
  });

  it('giấu chi tiết lỗi nội bộ của server', async () => {
    fetchMock.mockResolvedValue(new Response('private stack trace', { status: 500 }));
    submit();
    await vi.waitFor(() => expect(el('status').textContent).toContain('Máy chủ chưa xử lý được'));
    expect(document.body.textContent).not.toContain('private stack trace');
  });

  it('hủy yêu cầu sau 10 giây và mở lại nút gửi', async () => {
    vi.useFakeTimers();
    fetchMock.mockImplementation((_url: string, options: RequestInit) => new Promise((_resolve, reject) => {
      options.signal?.addEventListener('abort', () => reject(new DOMException('Timeout', 'AbortError')));
    }));
    submit();
    await vi.advanceTimersByTimeAsync(10000);
    expect(el('status').textContent).toBe('Chưa kết nối được máy chủ. Bạn hãy thử lại.');
    expect(el<HTMLButtonElement>('submit-button').disabled).toBe(false);
  });
});
