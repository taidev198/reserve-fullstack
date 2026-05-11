# Warehouse Reservation — Frontend

React 18 + Vite + TypeScript + Tailwind CSS. See the
[top-level README](../README.md) for architecture and the API
contract.

## Run

```bash
npm install        # first time only
npm run dev        # http://localhost:5173
```

Requests to `/api/**` are proxied to the backend on
`http://localhost:8080` by the Vite dev server (see `vite.config.ts`).

After dependencies are installed once, daily run is a single command:

```bash
npm run dev
```

## Frontend requirement checklist

- React + TypeScript: implemented with strict typed DTOs in `src/types/api.ts`.
- Component decomposition: split across `pages`, `components/ui`, `components/reservation`, and `components/inventory`.
- Async handling: loading/success/error managed via `useAsync` and rendered by page/components.
- API integration: centralized in `src/api/client.ts` (no direct `fetch` in render paths).
- Form validation: controlled inputs + client-side checks in `ReservationForm` before API call.
- UX polish: disabled/loading button states, error banners, empty states, and responsive card layout.

## Tests

```bash
npm test            # one-shot
npm run test:watch  # watch mode
```

10 tests across:

* `StockHealthBar` — OK / LOW / OUT visual states
* `ReservationForm` — client-side validation, happy path, API error rendering
* `ReservationRow` — disabled state in terminal status, confirm flow, error path

## Type-check / build

```bash
npm run lint     # tsc --noEmit
npm run build    # production bundle into dist/
```
