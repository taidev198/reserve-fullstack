import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StockHealthBar } from '../components/ui/StockHealthBar';

describe('StockHealthBar', () => {
  it('renders Ample when availability is at least 50%', () => {
    render(<StockHealthBar total={100} available={50} />);
    expect(screen.getByText('Ample')).toBeInTheDocument();
    expect(screen.getByRole('meter')).toHaveAttribute('aria-valuenow', '50');
  });

  it('renders Low under 50% availability', () => {
    render(<StockHealthBar total={100} available={20} />);
    expect(screen.getByText('Low')).toBeInTheDocument();
  });

  it('renders Out at zero availability', () => {
    render(<StockHealthBar total={100} available={0} />);
    expect(screen.getByText('Out')).toBeInTheDocument();
  });

  it('handles zero total gracefully', () => {
    render(<StockHealthBar total={0} available={0} />);
    expect(screen.getByRole('meter')).toHaveAttribute('aria-valuenow', '0');
  });
});
