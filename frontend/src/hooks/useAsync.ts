import { useCallback, useEffect, useRef, useState } from 'react';

export type AsyncStatus = 'idle' | 'loading' | 'success' | 'error';

export interface AsyncState<T> {
  status: AsyncStatus;
  data: T | undefined;
  error: Error | undefined;
}

/**
 * Run an async function and expose loading / success / error state.
 * The work is deduplicated against an "active call id" so a stale
 * response (e.g. user clicked the button twice) cannot overwrite
 * the result of a newer one.
 */
export function useAsync<T, A extends unknown[]>(fn: (...args: A) => Promise<T>) {
  const [state, setState] = useState<AsyncState<T>>({
    status: 'idle',
    data: undefined,
    error: undefined,
  });
  const callIdRef = useRef(0);

  const run = useCallback(
    async (...args: A): Promise<T | undefined> => {
      const myCall = ++callIdRef.current;
      setState({ status: 'loading', data: undefined, error: undefined });
      try {
        const data = await fn(...args);
        if (myCall === callIdRef.current) {
          setState({ status: 'success', data, error: undefined });
        }
        return data;
      } catch (err) {
        if (myCall === callIdRef.current) {
          setState({
            status: 'error',
            data: undefined,
            error: err instanceof Error ? err : new Error(String(err)),
          });
        }
        return undefined;
      }
    },
    [fn],
  );

  const reset = useCallback(() => {
    callIdRef.current++;
    setState({ status: 'idle', data: undefined, error: undefined });
  }, []);

  return { ...state, run, reset };
}

/** Convenience: kick off the fetch on mount. */
export function useAsyncEffect<T>(fn: () => Promise<T>, deps: React.DependencyList = []) {
  const async = useAsync(fn);
  useEffect(() => {
    void async.run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return async;
}
