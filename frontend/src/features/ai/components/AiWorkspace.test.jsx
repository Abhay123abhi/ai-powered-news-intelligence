import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import AiWorkspace from './AiWorkspace';
import aiApi from '../api/aiApi';
import aiErrorMessage from '../api/aiErrorMessage';

vi.mock('../api/aiApi', () => ({
  default: {
    status: vi.fn(),
    brief: vi.fn(),
    ask: vi.fn(),
    compare: vi.fn()
  }
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

const articles = [{ title: 'A story', source: 'Guardian', url: 'https://example.com' }];
let root;
let container;

beforeEach(() => {
  vi.clearAllMocks();
  Element.prototype.scrollIntoView = vi.fn();
  aiApi.status.mockResolvedValue({ enabled: true });
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

const render = async (items = articles) => act(async () => {
  root.render(<AiWorkspace articles={items} />);
});

const briefButton = () => [...container.querySelectorAll('button')]
  .find((button) => button.textContent === 'Create brief');

test('provider diagnostics never appear in visitor error text', async () => {
  aiApi.brief.mockRejectedValue({
    response: { data: { code: 'AI_ACCESS_DENIED', detail: 'Private provider diagnostics' } }
  });

  await render();
  await act(async () => briefButton().click());

  expect(container.textContent).toContain('AI is temporarily unavailable');
  expect(container.textContent).not.toContain('Private provider diagnostics');
});

test('late results from the old feed are discarded', async () => {
  let resolve;
  aiApi.brief.mockImplementation(() => new Promise((done) => {
    resolve = done;
  }));

  await render();
  await act(async () => briefButton().click());
  await render([{ ...articles[0], title: 'New feed' }]);

  expect(aiApi.brief.mock.calls[0][1].aborted).toBe(true);

  await act(async () => resolve({ text: 'Stale answer' }));
  expect(container.textContent).not.toContain('Stale answer');
});

test('repeated clicks do not submit concurrent generations', async () => {
  aiApi.brief.mockReturnValue(new Promise(() => {}));

  await render();
  act(() => {
    briefButton().click();
    briefButton().click();
  });

  expect(aiApi.brief).toHaveBeenCalledTimes(1);
});

test('quota and transport errors get safe friendly messages', () => {
  expect(aiErrorMessage({ response: { data: { code: 'AI_PROVIDER_QUOTA' } } })).toContain('usage limit');
  expect(aiErrorMessage({ code: 'ECONNABORTED' })).toContain('longer than expected');
  expect(aiErrorMessage({ response: { data: { detail: 'secret' } } })).not.toContain('secret');
});

const insightCloseButton = () => container.querySelector('[aria-label="Close AI insight"]');

test('closes a Daily brief and allows Compare coverage without a refresh', async () => {
  aiApi.brief.mockResolvedValue({ text: 'Brief summary' });
  aiApi.compare.mockResolvedValue({ text: 'Coverage differences' });

  await render();
  await act(async () => briefButton().click());

  expect(container.textContent).toContain('Brief summary');
  expect(insightCloseButton()).not.toBeNull();

  await act(async () => insightCloseButton().click());
  expect(container.querySelector('.ai-insight-card')).toBeNull();

  const compare = [...container.querySelectorAll('button')]
    .find((button) => button.textContent === 'Compare');
  await act(async () => compare.click());

  expect(aiApi.compare).toHaveBeenCalledTimes(1);
  expect(container.textContent).toContain('Coverage differences');

  await act(async () => insightCloseButton().click());
  expect(container.querySelector('.ai-insight-card')).toBeNull();
});

test('closes Ask the news output without resetting the feed', async () => {
  aiApi.ask.mockResolvedValue({ text: 'Grounded answer' });

  await render();
  const input = container.querySelector('.ai-question input');
  const setNativeValue = Object.getOwnPropertyDescriptor(
    HTMLInputElement.prototype, 'value'
  ).set;

  await act(async () => {
    setNativeValue.call(input, 'What matters today?');
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });

  const askButton = [...container.querySelectorAll('button')]
    .find((button) => button.textContent === 'Ask');
  expect(askButton.disabled).toBe(false);

  await act(async () => askButton.click());
  expect(aiApi.ask).toHaveBeenCalledOnce();
  expect(container.textContent).toContain('Grounded answer');

  await act(async () => insightCloseButton().click());
  expect(container.querySelector('.ai-insight-card')).toBeNull();
  expect(container.querySelector('.ai-controls')).not.toBeNull();
});

test('closing a pending insight aborts its request and ignores a late result', async () => {
  let resolveBrief;
  aiApi.brief.mockImplementation(() => new Promise((resolve) => {
    resolveBrief = resolve;
  }));

  await render();
  await act(async () => briefButton().click());
  const signal = aiApi.brief.mock.calls[0][1];

  expect(insightCloseButton()).not.toBeNull();
  await act(async () => insightCloseButton().click());

  expect(signal.aborted).toBe(true);
  expect(container.querySelector('.ai-insight-card')).toBeNull();
  expect(briefButton().disabled).toBe(false);

  await act(async () => resolveBrief({ text: 'Unexpected late brief' }));
  expect(container.textContent).not.toContain('Unexpected late brief');
});

test('closes a failed insight to allow another attempt', async () => {
  aiApi.brief.mockRejectedValue({ response: { data: { code: 'AI_PROVIDER_QUOTA' } } });

  await render();
  await act(async () => briefButton().click());
  expect(container.textContent).toContain('usage limit');

  await act(async () => insightCloseButton().click());
  expect(container.querySelector('.ai-insight-card')).toBeNull();
  expect(briefButton().disabled).toBe(false);
});
