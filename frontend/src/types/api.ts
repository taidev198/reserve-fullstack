/** Mirror of the backend's response DTOs — kept in one place so any
 *  contract drift is caught at compile time. */

export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED';

/** Standard JSON envelope from the Spring API (`ApiResponse`). */
export interface ApiEnvelope<T> {
  statusCode: number;
  message: string;
  data: T;
  /** Present only on error responses */
  url?: string | null;
}

/** Nested under `data` when `statusCode` indicates an error. */
export interface ApiErrorDataPayload {
  code: string;
  details: ApiFieldError[];
  timestamp: string;
}

export interface InventoryView {
  sku: string;
  name: string;
  totalQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  version?: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ReservationItemView {
  sku: string;
  name: string;
  quantity: number;
}

export interface ReservationView {
  id: number;
  orderId: string;
  status: ReservationStatus;
  canConfirm: boolean;
  canCancel: boolean;
  items: ReservationItemView[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateReservationLine {
  sku: string;
  quantity: number;
}

export interface CreateReservationRequest {
  orderId: string;
  items: CreateReservationLine[];
}

export interface CreateReservationResult {
  reservation: ReservationView;
  idempotentReplay: boolean;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

/** Normalised error shape used by {@link ApiError} (works for envelope + legacy flat errors). */
export interface ApiErrorBody {
  code: string;
  message: string;
  details: ApiFieldError[];
  timestamp: string;
}
