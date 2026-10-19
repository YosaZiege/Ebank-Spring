# Spring Cloud Microservices — Build Notes (Demo Transcript)

> Faithful write-up of the demo: two business microservices (`customer-service`,
> `ebank-service`), the technical services (`discovery`, `gateway`), synchronous
> inter-service communication with **OpenFeign**, fault tolerance with
> **Resilience4j**, and Swagger UI.
>
> **Stack:** Java 21 · Maven · Spring Boot **3.5.10** · Spring Cloud **2025.0.0**.
> The instructor explicitly stayed on Spring Boot 3 (not 4) because 4.x is still
> evolving. A future migration to 4.x is expected once it stabilises.
>
> **Deferred to the next session (NOT in this transcript):** config-server,
> chatbot service (Spring AI + MCP, streamable mode), Angular 21 frontend,
> Telegram client, security/Keycloak, Docker Compose.

---

## 1. Architecture overview

```
                        ┌──────────────────┐
        HTTP  :9999 ──► │   GATEWAY-SERVICE │  (Spring Cloud Gateway, reactive)
                        └───────┬──────────┘
                                │  discovery (load-balances by service name)
                        ┌───────▼──────────┐
                        │ DISCOVERY-SERVICE │  (Eureka, :8761)
                        └───────┬──────────┘
                 register    register
             ┌───────────────┴───────────────┐
     ┌───────▼────────┐              ┌───────▼────────┐
     │ CUSTOMER-SERVICE│◄── OpenFeign ──│  EBANK-SERVICE │
     │   (port 8056)   │  (sync REST)  │   (port 8057)  │
     └────────────────┘               └────────────────┘
```

- **customer-service** — CRUD over `Customer` (H2, JPA, Lombok).
- **ebank-service** — CRUD over `BankAccount`; each account references a
  `customerId` and, via OpenFeign, enriches itself with customer data.
- **discovery-service** — Eureka registry.
- **gateway-service** — single entry point; static and dynamic routing.

---

## 2. Prerequisites

- JDK 21 (or newer — 25 works to compile `release 21`).
- Maven 3.9+.
- (Optional) Springdoc OpenAPI dependency for `/swagger-ui.html`.

---

## 3. Parent project (Maven multi-module)

Create an empty Spring Boot project (`packaging=pom`) and add modules inside it.

**`ebank-microservice-app/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.10</version>
        <relativePath/>
    </parent>

    <groupId>com.ebank</groupId>
    <artifactId>ebank-microservice-app</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>ebank-microservice-app</name>

    <modules>
        <module>customer-service</module>
        <module>ebank-service</module>
        <module>gateway-service</module>
        <module>discovery-service</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2025.0.0</spring-cloud.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

---

## 4. `customer-service`

**Dependencies:** Spring Web · Spring Data JPA · H2 · Lombok · Eureka Discovery
Client · Config Client · Actuator · Springdoc OpenAPI.

**`customer-service/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ebank</groupId>
        <artifactId>ebank-microservice-app</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>customer-service</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.8.6</version>
        </dependency>
    </dependencies>
</project>
```

**Package layout**

```
src/main/java/com/ebank/customerservice/
├── entities/Customer.java
├── repository/CustomerRepository.java
├── service/CustomerService.java
├── controllers/CustomerRestController.java
└── CustomerServiceApplication.java
```

**`entities/Customer.java`**

```java
package com.ebank.customerservice.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;
}
```

**`repository/CustomerRepository.java`**

```java
package com.ebank.customerservice.repository;

import com.ebank.customerservice.entities.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
```

**`service/CustomerService.java`**

```java
package com.ebank.customerservice.service;

import com.ebank.customerservice.entities.Customer;
import com.ebank.customerservice.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Customer findCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not Found!"));
    }

    public Customer saveCustomer(Customer customer) {
        return customerRepository.save(customer);
    }
}
```

**`controllers/CustomerRestController.java`**

```java
package com.ebank.customerservice.controllers;

import com.ebank.customerservice.entities.Customer;
import com.ebank.customerservice.service.CustomerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CustomerRestController {

    private final CustomerService customerService;

    public CustomerRestController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/customers")
    public List<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    @GetMapping("/customers/{id}")
    public Customer findCustomerById(@PathVariable Long id) {
        return customerService.findCustomerById(id);
    }

    @PostMapping("/customers")
    public Customer saveCustomer(@RequestBody Customer customer) {
        return customerService.saveCustomer(customer);
    }
}
```

**`CustomerServiceApplication.java`** (seeds 3 customers with a `CommandLineRunner`)

```java
package com.ebank.customerservice;

import com.ebank.customerservice.entities.Customer;
import com.ebank.customerservice.service.CustomerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner commandLineRunner(CustomerService customerService) {
        return args -> {
            List<String> names = List.of("Mohammed", "Iman", "Yassin");
            names.forEach(name -> customerService.saveCustomer(
                    Customer.builder()
                            .name(name)
                            .email(name + "@gmail.com")
                            .build()));
        };
    }
}
```

**`src/main/resources/application.properties`**

```properties
spring.application.name=customer-service
server.port=8056
spring.datasource.url=jdbc:h2:mem:customer-db

# disabled for now — discovery & config services don't exist yet
spring.cloud.discovery.enabled=false
spring.cloud.config.enabled=false
```

**Test:** start it, then `http://localhost:8056/customers` and
`http://localhost:8056/customers/1`. After adding springdoc:
`http://localhost:8056/swagger-ui.html`.

---

## 5. `ebank-service` (bank accounts)

**Dependencies:** Spring Web · Spring Data JPA · H2 · Lombok · Eureka Discovery
Client · Config Client · Actuator · **OpenFeign** · Springdoc OpenAPI.

**Package layout**

```
src/main/java/com/ebank/ebankservice/
├── entities/BankAccount.java
├── model/Customer.java
├── repository/BankAccountRepository.java
├── service/EBankService.java
├── controllers/BankAccountRestController.java
└── EbankServiceApplication.java
```

**`entities/BankAccount.java`**

```java
package com.ebank.ebankservice.entities;

import com.ebank.ebankservice.model.Customer;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankAccount {

    @Id
    private String id;
    private Date createdAt;
    private double balance;
    private String type;        // "CURRENT_ACCOUNT" | "SAVING_ACCOUNT"
    private Long customerId;    // foreign key to customer-service

    // Not persisted: resolved at runtime from customer-service via OpenFeign
    @Transient
    private Customer customer;
}
```

**`model/Customer.java`** — a plain class (NOT a JPA entity), holds only the
fields ebank-service needs.

```java
package com.ebank.ebankservice.model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Customer {
    private Long id;
    private String name;
    private String email;
}
```

**`repository/BankAccountRepository.java`**

```java
package com.ebank.ebankservice.repository;

import com.ebank.ebankservice.entities.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankAccountRepository extends JpaRepository<BankAccount, String> {

    List<BankAccount> findByCustomerId(Long customerId);
}
```

**`service/EBankService.java`** (ID + date are set here, not by the client)

```java
package com.ebank.ebankservice.service;

import com.ebank.ebankservice.entities.BankAccount;
import com.ebank.ebankservice.repository.BankAccountRepository;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class EBankService {

    private final BankAccountRepository bankAccountRepository;

    public EBankService(BankAccountRepository bankAccountRepository) {
        this.bankAccountRepository = bankAccountRepository;
    }

    public List<BankAccount> getAllBankAccounts() {
        return bankAccountRepository.findAll();
    }

    public BankAccount findBankAccountById(String id) {
        return bankAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not Found!"));
    }

    public BankAccount saveBankAccount(BankAccount bankAccount) {
        // id and date are generated server-side
        bankAccount.setId(UUID.randomUUID().toString());
        bankAccount.setCreatedAt(new Date());
        return bankAccountRepository.save(bankAccount);
    }
}
```

**`controllers/BankAccountRestController.java`**

```java
package com.ebank.ebankservice.controllers;

import com.ebank.ebankservice.entities.BankAccount;
import com.ebank.ebankservice.service.EBankService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class BankAccountRestController {

    private final EBankService ebankService;

    public BankAccountRestController(EBankService ebankService) {
        this.ebankService = ebankService;
    }

    @GetMapping("/accounts")
    public List<BankAccount> getAllBankAccounts() {
        return ebankService.getAllBankAccounts();
    }

    @GetMapping("/accounts/{id}")
    public BankAccount findAccountById(@PathVariable String id) {
        return ebankService.findBankAccountById(id);
    }

    @PostMapping("/accounts")
    public BankAccount saveAccount(@RequestBody BankAccount bankAccount) {
        return ebankService.saveBankAccount(bankAccount);
    }
}
```

**`EbankServiceApplication.java`** (seeds 3 customers × 5 accounts)

```java
package com.ebank.ebankservice;

import com.ebank.ebankservice.entities.BankAccount;
import com.ebank.ebankservice.service.EBankService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class EbankServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EbankServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner commandLineRunner(EBankService ebankService) {
        return args -> {
            for (int i = 1; i <= 3; i++) {            // 3 customers
                for (int j = 1; j <= 5; j++) {        // 5 accounts each
                    BankAccount account = BankAccount.builder()
                            .balance(1000 + Math.random() * 60000)
                            .type(Math.random() > 0.5 ? "CURRENT_ACCOUNT" : "SAVING_ACCOUNT")
                            .customerId((long) i)
                            .build();
                    ebankService.saveBankAccount(account);
                }
            }
        };
    }
}
```

**`application.properties`**

```properties
spring.application.name=ebank-service
server.port=8057
spring.datasource.url=jdbc:h2:mem:account-db
spring.h2.console.enabled=true

spring.cloud.discovery.enabled=false
spring.cloud.config.enabled=false
```

---

## 6. OpenFeign — synchronous service-to-service call

**Add the dependency** (in `ebank-service`):

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

**`feign/CustomerRestClient.java`**

```java
package com.ebank.ebankservice.feign;

import com.ebank.ebankservice.model.Customer;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "customer-service")   // resolved by name through Eureka
public interface CustomerRestClient {

    @GetMapping("/customers/{id}")
    Customer getCustomerById(@PathVariable Long id);
}
```

**Enable Feign** on the application class — **required**, otherwise the client
bean can't be injected:

```java
@SpringBootApplication
@EnableFeignClients
public class EbankServiceApplication { /* ... */ }
```

**Use it in the service.** Inject the client and enrich the account, or validate
the customer before saving:

```java
@Service
public class EBankService {

    private final BankAccountRepository bankAccountRepository;
    private final CustomerRestClient customerRestClient;

    public EBankService(BankAccountRepository bankAccountRepository,
                        CustomerRestClient customerRestClient) {
        this.bankAccountRepository = bankAccountRepository;
        this.customerRestClient = customerRestClient;
    }

    public List<BankAccount> getAllBankAccounts() {
        List<BankAccount> accounts = bankAccountRepository.findAll();
        accounts.forEach(a -> a.setCustomer(
                customerRestClient.getCustomerById(a.getCustomerId())));
        return accounts;
    }

    public BankAccount findBankAccountById(String id) {
        BankAccount bankAccount = bankAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not Found!"));
        bankAccount.setCustomer(
                customerRestClient.getCustomerById(bankAccount.getCustomerId()));
        return bankAccount;
    }

    public BankAccount saveBankAccount(BankAccount bankAccount) {
        try {
            // must exist before we allow creating an account for it
            customerRestClient.getCustomerById(bankAccount.getCustomerId());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        bankAccount.setId(UUID.randomUUID().toString());
        bankAccount.setCreatedAt(new Date());
        return bankAccountRepository.save(bankAccount);
    }
}
```

> **Key idea:** splitting the DB removes referential integrity. Integrity rules
> ("can't create an account for a missing customer") are now enforced **in code**,
> and failures are handled with fault tolerance (below).

### 6.1 Resilience4j — circuit breaker fallback

**Add the dependency:**

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

**Annotate the Feign method** with a fallback:

```java
@FeignClient(name = "customer-service")
public interface CustomerRestClient {

    @GetMapping("/customers/{id}")
    @CircuitBreaker(name = "customerService", fallbackMethod = "getDefaultCustomer")
    Customer getCustomerById(@PathVariable Long id);

    // NOTE: fallback signature is (method args..., Throwable/Exception last)
    default Customer getDefaultCustomer(Long id, Exception exception) {
        return Customer.builder()
                .id(id)
                .name("Not available")
                .email("Not available")
                .build();
    }
}
```

> **Gotcha (from the demo):** the fallback method must take the same parameters
> as the target method **first**, then the exception **last**. Getting the order
> wrong breaks the fallback.

**Behaviour:** when `customer-service` is down, the account still returns, but
the embedded customer is the default "not available" object instead of a 500.
After N failures the breaker **opens** (short-circuit, no network call for a
cooldown period), then goes **half-open** (one probe call) and back to **closed**
on success.

---

## 7. `gateway-service`

**Dependencies:** Reactive Gateway · Eureka Discovery Client · Actuator.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

### 7.1 Static routing — `application.yml`

```yaml
server:
  port: 9999

spring:
  application:
    name: gateway-service
  cloud:
    gateway:
      routes:
        - id: r1
          uri: http://localhost:8056
          predicates:
            - Path=/customers/**
        - id: r2
          uri: http://localhost:8057
          predicates:
            - Path=/accounts/**
```

> **Syntax gotcha:** `Path=` must have no space, and the leading `-` before
> `Path` is kept so more predicates can be added later.

Test: `http://localhost:9999/customers` and `http://localhost:9999/accounts`.

### 7.2 Dynamic routing — via Discovery

Comment out the static `routes` block and add a bean:

```java
package com.ebank.gatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocator;
import org.springframework.cloud.gateway.discovery.DiscoveryLocatorProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }

    @Bean
    DiscoveryClientRouteDefinitionLocator dynamicRoutes(
            ReactiveDiscoveryClient rdc,
            DiscoveryLocatorProperties dlp) {
        return new DiscoveryClientRouteDefinitionLocator(rdc, dlp);
    }
}
```

Now reach services **by registered name** (uppercase):

```
http://localhost:9999/CUSTOMER-SERVICE/customers
http://localhost:9999/EBANK-SERVICE/accounts
```

---

## 8. `discovery-service` (Eureka)

**Dependencies:** Eureka Server · Actuator.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

**`DiscoveryServiceApplication.java`**

```java
package com.ebank.discoveryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}
```

**`application.properties`**

```properties
server.port=8761
spring.application.name=discovery-service

# don't register/fetch itself in its own registry
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

Dashboard: `http://localhost:8761`.

---

## 9. Enable registration + startup order

In **customer-service**, **ebank-service** and **gateway-service**, flip discovery
on (and point clients at the Eureka server):

```properties
spring.cloud.discovery.enabled=true
eureka.client.service-url.defaultZone=http://localhost:8761/eureka
```

(Remove/disable the `spring.cloud.config.enabled=false` only once a config server
exists.)

**Startup order**

1. `discovery-service`
2. `customer-service`
3. `ebank-service`
4. `gateway-service`

(Business services are independent of each other; at the end you'll orchestrate
this with Docker Compose.)

**Smoke tests**

```
# direct
curl http://localhost:8056/customers
curl http://localhost:8056/customers/1
curl -X POST http://localhost:8056/customers -H 'Content-Type: application/json' -d '{"name":"Ali","email":"ali@gmail.com"}'

# account enriched with customer (OpenFeign)
curl http://localhost:8057/accounts/{id}

# through the gateway
curl http://localhost:9999/CUSTOMER-SERVICE/customers
curl http://localhost:9999/EBANK-SERVICE/accounts
```

---

## 10. Deferred to next session (NOT covered here)

- **config-service** (Spring Cloud Config Server) — created, then the
  `spring.cloud.config.enabled=false` flags are removed.
- **ebank-chatbot-service** — Spring AI agent; questions dispatched to the right
  backend via **MCP (Model Context Protocol), streamable mode**; optional RAG.
- **Angular 21 frontend** — classic search UI + chatbot interface.
- **Telegram client** — talk to the chatbot asynchronously, bypassing the gateway.
- **Security / Keycloak** — authentication layer (with a dedicated Keycloak
  activity).
- **Docker Compose** — start all services with one command.
