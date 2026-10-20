# Ebank — Spring Cloud Microservices

A microservices architecture demo: two business services (`customer-service`,
`ebank-service`) plus the technical services (`discovery-service`,
`gateway-service`), with synchronous inter-service communication (OpenFeign),
fault tolerance (Resilience4j), and dynamic routing through Eureka.

## Status — where we are now

- ✅ **4 services** built, running, and registered with Eureka
- ✅ **Synchronous comms** — `ebank-service` calls `customer-service` via OpenFeign
- ✅ **Fault tolerance** — Resilience4j circuit breaker with a fallback
- ✅ **Gateway** — dynamic routing by service name (no static routes)
- ✅ **Swagger UI** — on `customer-service` and `ebank-service`
- ⏳ **Deferred**: config-server, chatbot (Spring AI + MCP), Angular 21 frontend,
  Telegram client, security/Keycloak, Docker Compose

## Stack

- Java 21 (compiled/run with JDK 25 — see [JDK note](#jdk-note))
- Spring Boot **4.1.1**
- Spring Cloud **2025.1.3**
- Maven (each service has its own `mvnw` wrapper)
- H2 (in-memory), Spring Data JPA, Lombok

## Architecture

```
                    ┌──────────────────┐
        HTTP :9999 →│  GATEWAY-SERVICE │   Spring Cloud Gateway (reactive/WebFlux)
                    └────────┬─────────┘
                             │ dynamic routing (Eureka discovery)
                    ┌────────▼─────────┐
                    │ DISCOVERY-SERVICE│   Eureka server, :8761
                    └────────┬─────────┘
               register     │      register
        ┌───────────────────┴────────────────────┐
┌───────▼─────────┐                      ┌───────▼─────────┐
│ CUSTOMER-SERVICE │◄──── OpenFeign ─────│  EBANK-SERVICE  │
│     :8056        │   (sync REST +      │     :8057       │
│                  │    circuit breaker) │                 │
└──────────────────┘                     └─────────────────┘
```

## Services

| Service | Port | Purpose |
|---|---|---|
| `discovery-service` | 8761 | Eureka server (registry) |
| `gateway-service` | 9999 | single entry point, dynamic routing |
| `customer-service` | 8056 | CRUD over `Customer` |
| `ebank-service` | 8057 | CRUD over `BankAccount`, enriched with customer data via Feign |

Each service is an **independent Maven project** (own `pom.xml` + `mvnw`), not a
module of an aggregator.

## Domain & endpoints

**customer-service**

- `Customer`: `Long id`, `String name`, `String email`
- `GET /customers` — all customers
- `GET /customers/{id}` — one customer
- `POST /customers` — create
- Seeds 3 customers on startup (`CommandLineRunner`)
- Swagger: `http://localhost:8056/swagger-ui.html`

**ebank-service**

- `BankAccount`: `String id` (UUID, set server-side), `Date createdAt`,
  `double balance`, `String type`, `long customerId`, `@Transient Customer customer`
- `model.Customer` — plain (non-entity) class holding the customer data pulled
  from `customer-service`
- `GET /accounts`, `GET /accounts/{id}`, `POST /accounts`
- `POST` validates the customer exists first (throws if not)
- Seeds 2 customers × 5 accounts on startup
- Swagger: `http://localhost:8057/swagger-ui.html`

**Inter-service call (OpenFeign + Resilience4j)**

- `CustomerRestClient` → `@FeignClient(name = "customer-service")`
- Method `getCustomerById(id)` is decorated with
  `@CircuitBreaker(name = "customerService", fallbackMethod = "getDefaultCustomer")`
- When `customer-service` is down, the account is still returned with a
  `"Not Available"` customer instead of a 500.

## Run it

Start in this order (discovery first; the others can be in any order):

```bash
# 1. discovery-service
cd discovery-service && ./mvnw spring-boot:run

# 2. business services
cd customer-service && ./mvnw spring-boot:run
cd ebank-service    && ./mvnw spring-boot:run

# 3. gateway
cd gateway-service  && ./mvnw spring-boot:run
```

## Test it

```bash
# direct
curl http://localhost:8056/customers
curl http://localhost:8056/customers/1
curl http://localhost:8057/accounts

# through the gateway (dynamic routing by service name, UPPERCASE)
curl http://localhost:9999/CUSTOMER-SERVICE/customers
curl http://localhost:9999/EBANK-SERVICE/accounts

# registry dashboard
# http://localhost:8761
```

## Configuration

Each service points at the registry and registers itself:

```properties
spring.cloud.discovery.enabled=true
eureka.client.service-url.defaultZone=http://localhost:8761/eureka
```

`discovery-service` disables self-registration:

```properties
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

The gateway uses a `DiscoveryClientRouteDefinitionLocator` bean (dynamic routes),
so requests are routed by service name: `/{SERVICE-NAME}/...`.

## Spring Cloud 2025.x gotchas (already handled)

Spring Boot 4 pairs with Spring Cloud 2025.x, which renamed several things vs.
the 3.x tutorials:

- Gateway starter: `spring-cloud-starter-gateway` → **`spring-cloud-starter-gateway-server-webflux`**
- Gateway config namespace: `spring.cloud.gateway.*` → **`spring.cloud.gateway.server.webflux.*`**
- A reactive gateway must **not** have `spring-boot-starter-webmvc` / H2 on its
  classpath (it runs on Netty/WebFlux).
- OpenFeign / Eureka artifact names and `@EnableEurekaServer` package are unchanged.

## JDK note

The projects target Java 21, but the default `java`/`javac` is **Temurin 25**
(sdkman). Compiling works because `javac 25` emits `--release 21`. Don't switch
the default back to 17 — Spring Boot 4 requires 21+.
