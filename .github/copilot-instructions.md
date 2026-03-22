# DeliverMore Workspace — Copilot Instructions

## Workspace Overview

DeliverMore is a small food/beverage and misc delivery company that for now services a single town with a population of around 17000.  The app is used by 3 levels of users.  Admin is used by the owner and support staff (me primarilty as IT support for my daughters business), Manager role is split between 2 managers/dispatchers to cover each day and then the drivers who perform the delivery based on the instructions and tasks pushed to them.  The drivers use a mobile app (not built by me) that integrates with the Tookan delivery management platform, and the admin UI is used to manage orders, customers, drivers, and view reports.  The dm_collector service runs scheduled batch jobs to pull data from Tookan and other sources for reporting and operational purposes.

This workspace contains two related Spring Boot projects that together form the DeliverMore platform:

- **delivermoreAdmin** — The main web application (admin UI and business logic)
- **dm_collector** — A background data collection/processing service that is also used for shared domain models and utilities used by the admin app. It is packaged as a separate artifact to allow for independent deployment.

---

## Project: delivermoreAdmin

The main purpose of the admin app is to have a combined picture of all the data collected from sources such as Tookan (task management of orders placed using GloriaFood and integrated with Tookan), Order information pulled from GloriaFood for each restaurant, and then the ability to manage customers, drivers, and view reports.  The app also integrates with QuickBooks for accounting purposes.  The UI is built using Vaadin (Flow) and is used on both mobile and desktop devices.  Mobile is very important as owner, managers and drivers need to be mobile.  Desktop is primarily used by the admin/owner for weekly processes around restaurant and driver payouts and processing of perior summaries along with quickbooks.

**Artifact:** `ca.admin.delivermore:delivermore`  
**Base package:** `ca.admin.delivermore`

### Tech Stack
- Java 21
- Spring Boot 4.0
- Vaadin 25 (Flow) for the UI — components live in `views/`
- Spring Security for authentication/authorization
- Spring Data JPA for persistence
- Spring WebFlux (reactive HTTP client for external API calls)
- MariaDB (production and development database on different servers)
- Spring Boot Mail
- OpenCSV for CSV export/import
- Production server runs in the cloud on a Linode server in a docker container; development is done locally with a local MariaDB instance
- fr.opensagres.xdocreport for generating Word/PDF reports from templates. Used for weekly PDF reports sent to drivers and restaurants for payouts and summaries.

### Key Package Structure
- `views/` — Vaadin UI views and layouts
- `data/entity/` — JPA entities
- `data/service/` — Service layer (business logic)
- `data/scheduler/` — Scheduled jobs
- `data/report/` — Reporting logic
- `data/intuit/` — QuickBooks/Intuit integration
- `security/` — Spring Security configuration
- `components/` — Shared Vaadin UI components

---

## Project: dm_collector

- The dm_collector service is responsible for scheduled data collection and processing tasks.  It primarily pulls data from the Tookan API to keep an up-to-date local copy of all the delivery task information, which is then used for reporting and operational purposes in the admin app.  IT also collects data from GloriaFood API from each configured restaurant to match with the Tookan tasks for a full picture of orders from that source (restaurant menus are build with GloriaFoods interface and those menus are publiched on a website for customers to place orders.  Website is delivermore.ca)  It also contains shared domain models and utilities that are used by the admin app, such as JPA entities that represent the core business objects (orders, drivers, customers) and any shared constants or utility classes.  By packaging this as a separate artifact, it allows for independent deployment and scaling of the data collection service if needed in the future.  The scheduled task are primarily run through a docker process called Ofelia that runs the batch jobs on a schedule.  The batch jobs are defined using Spring Batch and are triggered by Ofelia through command line arguments. Tasks run every 2 mins and GloriaFood every 3 mins, with some additional tasks that run on a less frequent schedule (daily/weekly) for reporting and maintenance purposes. A daily process runs to send a driver schedule summary to the admin/owner.

**Artifact:** `ca.admin.delivermore:collector`  
**Base package:** `ca.admin.delivermore.collector`  
**Root:** `dm_collector/collector/`

### Tech Stack
- Java 21
- Spring Boot 4.0
- Spring Batch for scheduled data processing jobs
- Spring Data JPA for persistence
- Spring WebFlux (reactive HTTP client for external API calls)
- Spring Boot Mail
- MariaDB
- Lombok
- OpenCSV

### Key Package Structure
- `config/` — Spring Batch and application configuration
- `data/entity/` — JPA entities
- `data/service/` — Service layer
- `data/tookan/` — Tookan delivery platform integration
- `data/reportitem/` — Report data models
- `data/global/` — Shared utilities and constants

---

## Coding Guidelines (Both Projects)

### General
- Vaadin is the primary framework for the admin app, so follow Vaadin best practices for UI development (e.g. using `@Route` for views, keeping business logic out of views, etc.)
- where possible i like to keep the stack current on versions so it does not fall far behind keeping in mind updates of versions works across dependencies.  For example, if I update Vaadin, I will also update Spring Boot to the latest compatible version.  This keeps the codebase more maintainable and allows us to take advantage of new features and improvements in the frameworks.  Updates to these and the Java version need tom consider the smaller dependencies for things like PDF creation as well as they are key features.
- Use Java 21 features where appropriate (records, pattern matching, text blocks, virtual threads, etc.)
- Prefer constructor injection over field injection (`@Autowired` on fields)
- Follow Spring Boot conventions — keep configuration in `application.properties`, use `@ConfigurationProperties` for typed config
- Use Lombok in `dm_collector` (`@Data`, `@Builder`, `@Slf4j`, etc.) but not a hard fast rule where cleaner solutions are available; Generally not used in `delivermoreAdmin` unless already established

### Naming & Structure
- Name service classes with a `Service` suffix (e.g. `DriverService`) and Repository classes with a `Repository` suffix (e.g. `DriverRepository`)
- Name JPA entities without a suffix (e.g. `Driver`, `Order`)
- Keep business logic in the service layer; views and batch steps should delegate to services
- Use `@Transactional` on service methods that write to the database
- Some services and entities are tied closely to the Tookan API and data model, and are named accordingly (e.g. `TookanTask`, `TookanService`) to reflect that relationship.  This helps to keep a clear separation between core domain models and those that are specific to external integrations.

### Persistence
- Use Spring Data JPA repositories; avoid native SQL unless necessary for performance
- Name repository interfaces with a `Repository` suffix (e.g. `DriverRepository`)

### Error Handling & Logging
- Use SLF4J (`private static final Logger log = LoggerFactory.getLogger(...)`) for logging
- Log at `DEBUG` for verbose detail, `INFO` for important state changes, `WARN`/`ERROR` for failures
- Do not swallow exceptions silently — log with context before rethrowing or handling.  Handle whereever practical as the application is used daily and critical to operations so needs to be available and recover where possible.

### Testing
- at this point integrated tests have not be used.
- changes are kept to a minimum and only done when necessary to keep the system running smoothly and up to date with dependencies.  

---

## External Integrations

- **Tookan** — Delivery task management platform (used primarily in dm_collector) but provides the core data for all tasks managed within the admin app as well.  The Tookan API is used to pull task data, which is then stored locally and used for reporting and operational purposes in the admin app.
- **GloriaFood** — Online ordering system used by the restaurants; API is used to pull order data to match with Tookan tasks for a complete picture of orders and deliveries.
- **Intuit/QuickBooks** — Accounting integration (delivermoreAdmin)
- **fr.opensagres.xdocreport** — Used for generating Word/PDF reports from templates, primarily for weekly driver and restaurant payout summaries (delivermoreAdmin)