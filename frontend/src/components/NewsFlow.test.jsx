import React from 'react';
import { expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import NewsFlow from './NewsFlow';

it('opens one explanation at a time using keyboard and pointer input', async () => {
  render(<NewsFlow />);
  const reporting = screen.getByRole('button', { name: /Reporting/ });
  reporting.focus();
  await userEvent.keyboard('{Enter}');
  expect(reporting).toHaveAttribute('aria-expanded', 'true');
  expect(screen.getByText(/Search reporting from/)).toBeVisible();
  await userEvent.click(screen.getByRole('button', { name: /AI insight/ }));
  expect(reporting).toHaveAttribute('aria-expanded', 'false');
  expect(screen.queryByText(/Search reporting from/)).not.toBeInTheDocument();
  expect(screen.getByText(/Gemini uses up to eight/)).toBeVisible();
  await userEvent.click(screen.getByRole('button', { name: /AI insight/ }));
  expect(document.getElementById('news-flow-detail')).not.toBeVisible();
});
