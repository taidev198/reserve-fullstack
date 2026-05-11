import type {
  ApiEnvelope,
  ApiErrorBody,
  ApiErrorDataPayload,
  CreateReservationRequest,
  CreateReservationResult,
  InventoryView,
  PageResponse,
  ReservationView,
} from '../types/api';

/**
 * Thin fetch wrapper. Centralising the HTTP/error contract here means
 * every component handles failures the same way (structured error code
 * + human message), and the rest of the app never touches `fetch`.
 */

const BASE_URL = '/api';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiErrorBody,
    public readonly url?: string | null,
  ) {
    super(body.message);
  }
}

function isApiEnvelope(json: unknown): json is ApiEnvelope<unknown> {
  return (
    typeof json === 'object' &&
    json !== null &&
    'statusCode' in json &&
    'message' in json &&
    'data' in json
  );
}

function unwrapData<T>(json: unknown): T {
  if (isApiEnvelope(json)) {
    return json.data as T;
  }
  return json as T;
}

function parseErrorBody(json: unknown, fallbackMessage: string): ApiErrorBody {
  if (isApiEnvelope(json) && json.data && typeof json.data === 'object') {
    const data = json.data as ApiErrorDataPayload;
    return {
      code: data.code ?? 'UNKNOWN',
      message: typeof json.message === 'string' ? json.message : fallbackMessage,
      details: Array.isArray(data.details) ? data.details : [],
      timestamp: typeof data.timestamp === 'string' ? data.timestamp : new Date().toISOString(),
    };
  }
  if (typeof json === 'object' && json !== null && 'code' in json) {
    const legacy = json as Partial<ApiErrorBody>;
    return {
      code: legacy.code ?? 'UNKNOWN',
      message: legacy.message ?? fallbackMessage,
      details: Array.isArray(legacy.details) ? legacy.details : [],
      timestamp: legacy.timestamp ?? new Date().toISOString(),
    };
  }
  return {
    code: 'UNKNOWN',
    message: fallbackMessage,
    details: [],
    timestamp: new Date().toISOString(),
  };
}

function parseErrorUrl(json: unknown): string | null | undefined {
  if (isApiEnvelope(json)) {
    return json.url ?? undefined;
  }
  return undefined;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!response.ok) {
    let json: unknown;
    try {
      json = await response.json();
    } catch {
      json = null;
    }
    const fallback = `Request failed with status ${response.status}`;
    const body = parseErrorBody(json, fallback);
    throw new ApiError(response.status, body, parseErrorUrl(json));
  }
  if (response.status === 204) return undefined as T;
  const json: unknown = await response.json();
  return unwrapData<T>(json);
}

export const api = {
  listInventory: async (page = 0, size = 10): Promise<PageResponse<InventoryView>> => {
    const payload = await request<PageResponse<InventoryView> | InventoryView[]>(
      `/inventory?page=${page}&size=${size}`,
    );
    // Backward-compatible fallback: older backend may still return a plain array.
    if (Array.isArray(payload)) {
      return {
        content: payload,
        page,
        size,
        totalElements: payload.length,
        totalPages: payload.length === 0 ? 0 : 1,
        first: true,
        last: true,
      };
    }
    return payload;
  },
  listReservations: async (page = 0, size = 10): Promise<PageResponse<ReservationView>> => {
    const payload = await request<PageResponse<ReservationView> | ReservationView[]>(
      `/reservations?page=${page}&size=${size}`,
    );
    if (Array.isArray(payload)) {
      return {
        content: payload,
        page,
        size,
        totalElements: payload.length,
        totalPages: payload.length === 0 ? 0 : 1,
        first: true,
        last: true,
      };
    }
    return payload;
  },
  createReservation: async (req: CreateReservationRequest): Promise<CreateReservationResult> => {
    const response = await fetch(`${BASE_URL}/reservations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    });
    if (!response.ok) {
      let json: unknown;
      try {
        json = await response.json();
      } catch {
        json = null;
      }
      const fallback = `Request failed with status ${response.status}`;
      const body = parseErrorBody(json, fallback);
      throw new ApiError(response.status, body, parseErrorUrl(json));
    }
    const json: unknown = await response.json();
    const reservation = unwrapData<ReservationView>(json);
    return {
      reservation,
      idempotentReplay: response.headers.get('X-Reservation-Idempotent-Replay') === 'true',
    };
  },
  confirmReservation: (id: number) =>
    request<ReservationView>(`/reservations/${id}/confirm`, { method: 'POST' }),
  cancelReservation: (id: number) =>
    request<ReservationView>(`/reservations/${id}/cancel`, { method: 'POST' }),
};
