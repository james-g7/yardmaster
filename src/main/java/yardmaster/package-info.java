/**
 * Yardmaster, an open-source AI gateway.
 *
 * <p>Planned packages, filled in by the build steps in the spec:
 * <ul>
 *   <li>{@code yardmaster.api} – OpenAI- and Anthropic-compatible caller endpoints (public port)</li>
 *   <li>{@code yardmaster.auth} – signed token verification and trusted issuers</li>
 *   <li>{@code yardmaster.routing} – labels, pools, preferences, cool-down and fallback</li>
 *   <li>{@code yardmaster.limits} – Valkey counters, rule presets and caller limits</li>
 *   <li>{@code yardmaster.providers} – provider adapters</li>
 *   <li>{@code yardmaster.chat} – chat translation</li>
 *   <li>{@code yardmaster.credits} – gRPC credits hook</li>
 *   <li>{@code yardmaster.admin} – admin panel and admin API (private port)</li>
 *   <li>{@code yardmaster.ops} – metrics, tracing, webhooks, config import and export</li>
 * </ul>
 */
package yardmaster;
