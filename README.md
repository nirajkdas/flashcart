# ⚡ FlashCart — Real-time Flash Sale E-commerce Engine

> **Java 17 · Spring Boot 3 · PostgreSQL · Redis · WebSocket (STOMP) · JWT**

---

## 🎯 What Makes This Resume-Worthy

| Concept | Implementation |
|---|---|
| **Concurrency & Oversell Prevention** | Redis atomic `DECR` + JPA `@Version` optimistic locking |
| **Real-time Features** | WebSocket (STOMP over SockJS) for live inventory countdown |
| **Caching Strategy** | Redis with per-cache TTL (`products` 10m, `categories` 1h, `flash-sales` 30s) |
| **Rate Limiting** | Redis sliding-window limiter per user per action |
| **System Design** | Layered architecture: Controller → Service → Repository |
| **Security** | JWT auth, role-based access (CUSTOMER / SELLER / ADMIN) |
| **DB Migrations** | Flyway versioned migrations with indexes and constraints |
| **Scheduled Jobs** | `@Scheduled` auto-activates/expires sales, seeds Redis counters |
| **API Documentation** | Swagger UI at `/swagger-ui.html` |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      Client (REST / WS)                  │
└───────────────┬─────────────────────────┬───────────────┘
                │ HTTP/REST               │ WebSocket (STOMP)
┌───────────────▼─────────────────────────▼───────────────┐
│               Spring Boot Application                    │
│  ┌──────────┐  ┌────────────┐  ┌──────────────────────┐ │
│  │   Auth   │  │  Products  │  │  Flash Sale Engine   │ │
│  │ Controller│  │ Controller │  │     Controller       │ │
│  └────┬─────┘  └─────┬──────┘  └──────────┬───────────┘ │
│       │              │                     │             │
│  ┌────▼──────────────▼─────────────────────▼───────────┐ │
│  │              Service Layer                          │ │
│  │  AuthService · ProductService · FlashSaleService   │ │
│  │  OrderService · RateLimitService                   │ │
│  └────┬──────────────┬──────────────────┬─────────────┘ │
│       │              │                  │               │
│  ┌────▼───┐    ┌─────▼────┐    ┌────────▼─────────────┐ │
│  │  JPA   │    │  Redis   │    │  WebSocket Broker    │ │
│  │Repositories│  │ Cache +  │    │  (STOMP / SockJS)  │ │
│  │        │    │  Counter │    │                      │ │
│  └────┬───┘    └──────────┘    └──────────────────────┘ │
└───────┼─────────────────────────────────────────────────┘
        │
┌───────▼──────────┐
│   PostgreSQL DB  │
│  8 tables, FK    │
│  constraints,    │
│  Flyway managed  │
└──────────────────┘
```

---

## ⚡ Flash Sale Purchase — How Overselling is Prevented

```
User clicks "Buy Now"
        │
        ▼
[1] Rate limit check (Redis)
    └─ > 5 req/min? → 429 Too Many Requests
        │
        ▼
[2] Per-user quantity check (Redis)
    └─ already bought max? → 400 Bad Request
        │
        ▼
[3] Redis DECR (atomic)          ← PRIMARY GUARD
    └─ result < 0? → rollback INCR → 409 Sold Out
        │
        ▼
[4] DB update with @Version      ← SAFETY NET
    └─ version mismatch? → rollback Redis → 409 Retry
        │
        ▼
[5] Create Order in PostgreSQL
        │
        ▼
[6] Broadcast inventory update via WebSocket
        │
        ▼
[7] Send personal notification to buyer
        │
        ▼
      200 OK — Order confirmed 🎉
```

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- PostgreSQL 14+
- Redis 7+
- Docker

### 1. Database setup
```sql
CREATE DATABASE flashcart_db;
```

### 2. Update credentials
Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/flashcart_db
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### 3. DB Setup
Run the below docker command 
docker run --name flashcart-postgres \
  -e POSTGRES_DB=flashcart_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 \
  -d postgres:16

### 4. Redis Setup
Run the below docker command
docker run --name flashcart-redis \
  -p 6379:6379 \
  -d redis:7 \
  redis-server --requirepass flashcart_redis_pass


### 5. Run
```bash
mvn spring-boot:run
```

Flyway runs automatically — creates all tables and seeds sample data.

### 6. Explore the API
Open **http://localhost:8080/swagger-ui.html**

---

## 📡 WebSocket (Real-time Inventory)

```javascript
// Connect
const socket = new SockJS('http://localhost:8080/ws');
const client = Stomp.over(socket);

client.connect({}, () => {
  // Subscribe to live stock for flash sale #1
  client.subscribe('/topic/flash-sales/1/inventory', (msg) => {
    const data = JSON.parse(msg.body);
    console.log(`${data.productName}: ${data.remainingQuantity} left`);
  });

  // Subscribe to sale lifecycle events
  client.subscribe('/topic/flash-sales/events', (msg) => {
    const data = JSON.parse(msg.body);
    if (data.eventType === 'SALE_STARTED') showSaleBanner();
    if (data.eventType === 'SALE_ENDED')   hideSaleBanner();
  });
});
```

---

## 🔐 API Endpoints

### Auth
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/register` | ❌ | Register new account |
| POST | `/api/auth/login` | ❌ | Login → JWT |
| GET | `/api/auth/me` | ✅ | Current user profile |

### Flash Sales
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/flash-sales/active` | ❌ | Active sales with live stock |
| GET | `/api/flash-sales/{id}` | ❌ | Sale detail |
| POST | `/api/flash-sales/purchase` | CUSTOMER | **Atomic purchase** |
| POST | `/api/flash-sales` | ADMIN | Create flash sale |
| DELETE | `/api/flash-sales/{id}` | ADMIN | Cancel sale |

### Products
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/products` | ❌ | Paginated product list |
| GET | `/api/products/search?q=` | ❌ | Full-text search |
| GET | `/api/products/{id}` | ❌ | Product detail |
| POST | `/api/products` | SELLER | Create product |
| PUT | `/api/products/{id}` | SELLER | Update product |
| POST | `/api/products/{id}/reviews` | CUSTOMER | Leave review |

### Orders
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/orders` | CUSTOMER | Place regular order |
| GET | `/api/orders` | CUSTOMER | My orders |
| GET | `/api/orders/{id}` | CUSTOMER | Order detail |
| PATCH | `/api/orders/{id}/status` | ADMIN | Update status |

---

## 🧪 Running Tests

```bash
mvn test
```

Tests cover:
- ✅ Flash sale purchase — happy path
- ✅ Oversell prevention via Redis DECR rollback
- ✅ Per-user limit enforcement
- ✅ Rate limit rejection
- ✅ Inactive sale rejection
- ✅ Auth register/login (integration)
- ✅ Validation errors

---

## 📂 Project Structure

```
src/main/java/com/flashcart/
├── config/          # Security, Redis, WebSocket, OpenAPI
├── controller/      # REST endpoints
├── dto/
│   ├── request/     # Validated input DTOs
│   └── response/    # API response DTOs
├── entity/          # JPA entities (8 tables)
├── exception/       # Custom exceptions + global handler
├── repository/      # JPA repositories with JPQL queries
├── scheduler/       # @Scheduled sale activation/expiry
├── security/        # JWT filter + UserDetailsService
├── service/         # Business logic interfaces
│   └── impl/        # Implementations
└── websocket/       # STOMP controller + notification service

src/main/resources/
├── application.properties
└── db/migration/
    ├── V1__init_schema.sql   # 8 tables, indexes, constraints
    └── V2__seed_data.sql     # Sample users, products, sale
```

---

## 💡 Interview Talking Points

1. **"How did you prevent overselling in flash sales?"**  
   Two-layer approach: Redis DECR is atomic and handles 99% of cases with zero DB load. JPA `@Version` optimistic locking is the DB-level safety net — if two requests race past Redis, only one UPDATE wins.

2. **"How does real-time inventory work?"**  
   After every purchase, the service publishes an `InventoryUpdateMessage` to a STOMP topic. Any connected browser subscribed to `/topic/flash-sales/{id}/inventory` gets the update instantly.

3. **"Why Redis for caching and not just the DB?"**  
   Products and categories are read far more than written. Redis reduces DB load by 10-100x for catalog reads. For flash sales specifically, stock counters live in Redis because DECR is O(1) and atomic — impossible with standard SQL without locking.

4. **"How does your rate limiter work?"**  
   Sliding-window using Redis INCR + TTL. First request sets a 60-second expiry; each subsequent request increments the counter. If it exceeds the limit, return 429. Fails open if Redis is unavailable.

---

