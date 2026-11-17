/**
 * Yardmaster, an open-source AI gateway.
 *
 * <p>Planned packages, filled in by the build steps in the spec:
 *
 * <ul>
 *   <li>{@code yardmaster.api} – OpenAI- and Anthropic-compatible caller endpoints (public port)
 *   <li>{@code yardmaster.auth} – signed token verification and trusted issuers
 *   <li>{@code yardmaster.routing} – labels, pools, preferences, cool-down and fallback
 *   <li>{@code yardmaster.limits} – Valkey counters, rule presets and caller limits
 *   <li>{@code yardmaster.providers} – provider adapters and format translation
 *   <li>{@code yardmaster.credits} – gRPC credits hook
 *   <li>{@code yardmaster.admin} – admin panel and admin API (private port)
 *   <li>{@code yardmaster.ops} – metrics, tracing, webhooks, config import and export
 * </ul>
 */
package yardmaster;
