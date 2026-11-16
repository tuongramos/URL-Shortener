import './style.css';
import { ApiError, shortenUrl } from './api';

function element<T extends HTMLElement>(id: string): T {
  const value = document.getElementById(id);
  if (!value) throw new Error(`Thiếu phần tử ${id}`);
  return value as T;
}

const form = element<HTMLFormElement>('shorten-form');
const input = element<HTMLInputElement>('long-url');
const submit = element<HTMLButtonElement>('submit-button');
const submitLabel = element('submit-label');
const fieldError = element('url-error');
const status = element('status');
const result = element('result');
const shortLink = element<HTMLAnchorElement>('short-link');
const openLink = element<HTMLAnchorElement>('open-link');
const original = element('original-url');
const copyButton = element<HTMLButtonElement>('copy-button');
const copyLabel = element('copy-label');
const manualCopy = element('manual-copy');
const copyValue = element<HTMLInputElement>('copy-value');
let pending = false;

function showInputError(message: string): void {
  fieldError.textContent = message;
  fieldError.hidden = false;
  input.setAttribute('aria-invalid', 'true');
  input.focus();
}

input.addEventListener('input', () => {
  fieldError.hidden = true;
  input.removeAttribute('aria-invalid');
});

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (pending) return;
  fieldError.hidden = true;
  input.removeAttribute('aria-invalid');
  status.textContent = '';
  result.hidden = true;
  const url = input.value.trim();
  try {
    const parsed = new URL(url);
    if (!['http:', 'https:'].includes(parsed.protocol) || !/^https?:\/\//i.test(url)
      || /[\s\u0000-\u001f\u007f\\]/.test(url) || url.length > 4096) throw new Error();
  } catch {
    showInputError('Hãy nhập liên kết HTTP/HTTPS hợp lệ, tối đa 4096 ký tự.');
    return;
  }

  pending = true;
  submit.disabled = true;
  input.readOnly = true;
  form.setAttribute('aria-busy', 'true');
  submitLabel.textContent = 'Đang rút gọn...';
  status.textContent = 'Đang tạo liên kết của bạn...';
  try {
    const link = await shortenUrl(url);
    shortLink.textContent = link.short_url;
    shortLink.href = link.short_url;
    openLink.href = link.short_url;
    original.textContent = link.long_url;
    copyValue.value = link.short_url;
    copyLabel.textContent = 'Sao chép';
    manualCopy.hidden = true;
    result.hidden = false;
    status.textContent = link.reused ? 'Liên kết này đã được tạo trước đó. Bạn có thể dùng lại ngay.' : 'Đã tạo liên kết ngắn thành công.';
  } catch (error) {
    if (error instanceof ApiError && error.status === 422) {
      status.textContent = '';
      showInputError(error.message);
    } else {
      status.textContent = error instanceof ApiError ? error.message : 'Có lỗi xảy ra. Bạn hãy thử lại.';
    }
  } finally {
    pending = false;
    submit.disabled = false;
    input.readOnly = false;
    form.removeAttribute('aria-busy');
    submitLabel.textContent = 'Rút gọn';
  }
});

copyButton.addEventListener('click', async () => {
  const value = copyValue.value;
  copyButton.disabled = true;
  try {
    await navigator.clipboard.writeText(value);
    copyLabel.textContent = 'Đã sao chép';
    status.textContent = 'Đã sao chép liên kết';
  } catch {
    manualCopy.hidden = false;
    copyValue.focus();
    copyValue.select();
    status.textContent = 'Chưa sao chép tự động được. Hãy sao chép liên kết trong ô đã chọn.';
  } finally {
    copyButton.disabled = false;
  }
});

copyValue.addEventListener('click', () => copyValue.select());
