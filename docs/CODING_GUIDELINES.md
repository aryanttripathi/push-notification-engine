# Coding Guidelines

Binding for every change. The reviewer checks each rule.

## 1. Strings: Apache Commons Lang3

- Every string check and operation goes through `org.apache.commons.lang3.StringUtils`: `isBlank`, `isNotBlank`, `equals`, `equalsIgnoreCase`, `trim`, `defaultString`, `upperCase`, `abbreviate`, `join`.
- Banned: `s == null || s.isEmpty()`, `s.equals(other)` on nullable strings, `s.trim()` on nullable strings, `"".equals(s)`.
- Regex placeholder replacement in `TemplateRenderer` is the one place that needs `java.util.regex`. Lang3 has no regex substitution.

## 2. Method size

- **No method longer than 60 lines.** Aim for 30 or fewer. Enforced by Checkstyle `MethodLength` (max 60) in `mvn verify`.
- Long flows become an orchestrator plus named private steps (see `DecisionService.decide`).

## 3. MVC layering

| Layer | Does | Never |
|---|---|---|
| `controller` | HTTP mapping, `@Valid` request DTOs, status codes | touch repositories, hold business logic, return entities |
| `service` | business rules, `@Transactional` boundaries, entity ↔ DTO mapping | know about HTTP |
| `repository` | Spring Data queries | contain logic |
| `entity` | JPA mapping | leak to the API |
| view | static pages calling the JSON API | embed business rules |

Errors are thrown as typed exceptions from services and translated once in `GlobalExceptionHandler`.

## 4. Lombok + logging

- DI: `@RequiredArgsConstructor` with `private final` fields. No `@Autowired` fields.
- Logs: `@Slf4j`. Always **parameterized**, with the dynamic values in the message: `log.info("decision userId={} template={} result={} rule={}", ...)`. No string concatenation, no `System.out`.
- Entities: `@Getter @Setter @NoArgsConstructor`. **No `@Data` on entities** (broken equals/hashCode with lazy relations).
- DTOs: `@Value @Builder` (immutable). Requests: `@Data` (Jackson-friendly) + Bean Validation annotations.
- Log file: `logs/pronto.log`, created on first run (gitignored). Every line carries `requestId` from MDC.
- Log user ids, never names or other PII.

## 5. Design principles: earn every pattern

Rule of three: a pattern goes in only once at least three real variants or reasons exist **today**. Reference: [`MY_LLD`](https://github.com/aryanttripathi/MY_LLD).

| Principle / pattern | Used? | Where / why not |
|---|---|---|
| SRP | yes | `TemplateRenderer`, `ExpressionCompiler`, `RuleEngine` and `DecisionService` each have one reason to change |
| OCP | yes | new behaviour = new rule **row**, no code change |
| DIP | yes | constructor injection of Spring beans; `Clock` injected for testable time |
| Chain of responsibility | yes, as data | ordered rule rows evaluated until the first match. One loop over a list, **not** a handler class per rule (rules are runtime data) |
| Builder | yes, via Lombok | `@Builder` on response DTOs |
| Strategy | **no** | only 2 actions (BLOCK/ALLOW) → an enum + `switch`. Revisit at the 3rd real channel/sender |
| Factory | **no** | one way to build each object |
| Observer | **no** | no async consumers of decisions yet. Add with Kafka/outbox |
| Interface with one impl | **no** | e.g. no `PushSender` until a real FCM adapter exists |

## 6. Other rules

- No magic strings/numbers: enums or `private static final` constants.
- Every new behaviour gets a test. Each test asserts the behaviour, not the implementation.
- `ponytail:` comment on any deliberate shortcut, naming its ceiling and upgrade path.
- No secrets in the repo. Config comes from env vars in the `mysql` profile.
