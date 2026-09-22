import React from 'react';
import { it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AiWorkspace from './AiWorkspace';
import aiApi from '../api/aiApi';
vi.mock('../api/aiApi', () => ({ default: { status: vi.fn(), brief: vi.fn(), ask: vi.fn(), compare: vi.fn() } }));
const stories = [{ title: 'First story', url: 'https://example.com/1', source: 'Guardian' }, { title: 'Second story', url: 'https://example.com/2', source: 'NYT' }];
const answer = { content: { sections: [{ heading: 'Key developments', items: [{ text: 'A supported result', sourceIds: [1] }] }] }, citations: [{ id: 1, source: 'Guardian', url: 'https://example.com/1' }] };
const props = { articles: stories, feedId: 'first-feed', page: 1 };
beforeEach(() => { vi.clearAllMocks(); aiApi.status.mockResolvedValue({ enabled: true }); });
it('shows checking instead of offline during startup', () => {
  aiApi.status.mockReturnValue(new Promise(() => {})); render(<AiWorkspace {...props} />);
  expect(screen.getByRole('status')).toHaveTextContent('Checking availability');
  expect(screen.queryByText(/AI_ENABLED/)).not.toBeInTheDocument();
});
it('uses backend references and guards repeated keyboard submission', async () => {
  aiApi.ask.mockReturnValue(new Promise(() => {})); render(<AiWorkspace {...props} />);
  await screen.findByText('Ready');
  await userEvent.click(screen.getByRole('button', { name: 'Ask a question' }));
  await userEvent.type(screen.getByLabelText('Ask the news'), 'What changed?');
  const form = screen.getByLabelText('Ask the news').closest('form');
  fireEvent.submit(form); fireEvent.submit(form);
  expect(aiApi.ask).toHaveBeenCalledTimes(1);
  expect(aiApi.ask.mock.calls[0][1]).toEqual({ feedId: 'first-feed', page: 1, articleIds: [0, 1] });
});
it('discards a late answer after changing feeds', async () => {
  let resolve; aiApi.brief.mockReturnValue(new Promise(done => { resolve = done; }));
  const view = render(<AiWorkspace {...props} />); await screen.findByText('Ready');
  await userEvent.click(screen.getByRole('button', { name: 'Create brief' }));
  view.rerender(<AiWorkspace {...props} feedId="second-feed" />); resolve(answer);
  await waitFor(() => expect(screen.queryByText('A supported result')).not.toBeInTheDocument());
});
it('clears completed answers on feed change and respects reduced motion', async () => {
  aiApi.brief.mockResolvedValue(answer); const view = render(<AiWorkspace {...props} />);
  await screen.findByText('Ready'); await userEvent.click(screen.getByRole('button', { name: 'Create brief' }));
  await screen.findByText('A supported result');
  expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith({ behavior: 'auto', block: 'nearest' });
  view.rerender(<AiWorkspace {...props} page={2} />);
  expect(screen.queryByText('A supported result')).not.toBeInTheDocument();
});
it('explains quota errors while keeping examples available', async () => {
  aiApi.brief.mockRejectedValue({ response: { status: 429, data: { code: 'AI_QUOTA_REACHED', detail: 'Shared quota reached', retryAfter: 60 } } });
  render(<AiWorkspace {...props} />); await screen.findByText('Ready');
  await userEvent.click(screen.getByRole('button', { name: 'Create brief' })); await screen.findByText('Quota reached');
  expect(screen.getByText('Shared quota reached')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: /View an example/ }));
  expect(screen.getByText('Example, not live news')).toBeInTheDocument();
});
it('disables comparison when only one publisher is selected', async () => {
  render(<AiWorkspace {...props} articles={[stories[0]]} />); await screen.findByText('Ready');
  expect(screen.getByRole('button', { name: 'Compare', exact: true })).toBeDisabled();
});

it('automatically includes both publishers without a story selection step', async () => {
  aiApi.brief.mockResolvedValue(answer);
  const articles = Array.from({ length: 12 }, (_, i) => ({ title: `Story ${i}`, url: `https://example.com/${i}`, source: i === 11 ? 'NYT' : 'Guardian' }));
  render(<AiWorkspace {...props} articles={articles} />); await screen.findByText('Ready');
  expect(screen.queryByText(/Choose stories/)).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Create brief' }));
  expect(aiApi.brief.mock.calls[0][0].articleIds).toEqual([0, 1, 2, 3, 4, 5, 6, 11]);
});
