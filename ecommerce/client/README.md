# ECommerce Admin — React Frontend

A production-ready React 18 + TypeScript + Tailwind CSS v4 SPA for the ecommerce microservices backend.

## Tech Stack

| Concern            | Library                                        |
|--------------------|------------------------------------------------|
| UI framework       | React 18, TypeScript, Vite 6                   |
| Styling            | Tailwind CSS v4 (`@tailwindcss/vite`)           |
| Routing            | React Router DOM v6                            |
| HTTP client        | Axios (centralised instance + interceptors)    |
| Server state       | TanStack React Query v5                        |
| Auth               | Keycloak ROPC → JWT in localStorage            |
| Notifications      | react-hot-toast                                |
| Icons              | lucide-react                                   |

## Folder Structure

```
src/
├── api/               Axios instance + per-service API functions
│   ├── axiosInstance.ts    interceptors, token refresh, auth bridge
│   ├── authApi.ts          Keycloak ROPC / refresh / revoke
│   └── userApi.ts          User CRUD via gateway
├── components/
│   ├── ErrorBoundary.tsx
│   └── ui/                 Button, Input, Spinner, Badge, Modal, Pagination, Skeleton
├── constants/         App-wide constants + env-var wrappers
├── context/
│   ├── AuthContext.tsx     login/logout, role check, proactive token refresh
│   └── ThemeContext.tsx    light/dark toggle persisted to localStorage
├── hooks/
│   ├── useDebounce.ts
│   └── useLocalStorage.ts
├── layouts/           AppLayout (sidebar+header), AuthLayout (centered form)
├── pages/             LoginPage, DashboardPage, UsersPage, UserDetailPage, …
├── routes/            ProtectedRoute, RoleRoute
├── types/             TypeScript interfaces
└── utils/             tokenUtils, cn, formatDate
```

## Getting Started

### Prerequisites

- Node 20+
- Running microservices (see root `deployment/k8s/`)
  - Keycloak on `localhost:30080`
  - API Gateway on `localhost:2027`

### 1. Configure environment

```bash
cp .env.example .env
# Edit .env if your ports differ
```

### 2. Install & run

```bash
npm install
npm run dev   # → http://localhost:5173
```

The Vite dev server **proxies** `/api/*` and `/auth/*` → `localhost:2027`, so no CORS issues in dev.

### 3. Production build

```bash
npm run build    # output → dist/
npm run preview  # serve dist/ locally
```

## Authentication Flow

```
Browser                  Keycloak :30080              API Gateway :2027
  │                            │                             │
  │  POST /token (ROPC)        │                             │
  │──────────────────────────►│                             │
  │  { access_token, ... }     │                             │
  │◄──────────────────────────│                             │
  │                            │                             │
  │  GET /api/v1/users         │                             │
  │  Authorization: Bearer … ──────────────────────────────►│
  │                            │   validate via JWKS         │
  │                            │◄───────────────────────────│
  │  [user list]               │                             │
  │◄───────────────────────────────────────────────────────│
```

**Token refresh:** A `401` triggers the Axios interceptor which silently exchanges the
refresh token with Keycloak and retries. All concurrent 401s are queued and replayed.

## Role-Based Access

| Route          | Required Role        |
|----------------|----------------------|
| `/dashboard`   | any authenticated    |
| `/users`       | `ROLE_ADMIN`         |
| `/users/:id`   | `ROLE_ADMIN`         |

Keycloak roles (`ADMIN`, `USER`) are prefixed with `ROLE_` to match the Spring Security convention.

## Environment Variables

| Variable                  | Default                   |
|---------------------------|---------------------------|
| `VITE_API_GATEWAY_URL`    | `http://localhost:2027`   |
| `VITE_KEYCLOAK_URL`       | `http://localhost:30080`  |
| `VITE_KEYCLOAK_REALM`     | `microservices-realm`     |
| `VITE_KEYCLOAK_CLIENT_ID` | `apigateway-client`       |

## Security Notes

- Tokens are in **localStorage** for cross-tab persistence. For high-security apps,
  use `sessionStorage` or an `httpOnly` cookie for the refresh token.
- ROPC is deprecated in OAuth 2.1. For public apps, migrate to **Auth Code + PKCE**
  and let Keycloak render its login UI.
