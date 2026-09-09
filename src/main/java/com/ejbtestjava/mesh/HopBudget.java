package com.ejbtestjava.mesh;

/**
 * The hop budget carried by {@code X-EPB-Test-Hops}.
 *
 * <p>sc-263 wires the five {@code epb_test_*} applications into a ring, so every
 * request that arrives with a budget hands the next application one hop less. A
 * ring with no terminating rule is an infinite loop, and the whole safety
 * property lives in one sentence: <b>anything that is not a base-10 integer
 * greater than zero is zero</b>. A missing header must never mean "unlimited" —
 * that is the one default that can run away, on a box that is simultaneously
 * being measured.
 *
 * <p>Java's own parsers are all wrong for this on their own, which is why none
 * of them appear below:
 * <ul>
 *   <li>{@code Integer.parseInt("+4")} returns 4; the contract says 0.</li>
 *   <li>{@code Integer.parseInt} throws on junk instead of answering 0, and
 *       throws again on a value larger than an int, where the contract wants a
 *       clamp.</li>
 *   <li>{@code Integer.parseInt} accepts any Unicode decimal digit, so
 *       {@code "\u0664"} (Arabic-Indic four) would parse to 4 off the wire.</li>
 *   <li>{@code String.strip()} trims Unicode whitespace; the contract trims
 *       ASCII whitespace only, and {@code String.trim()} would also eat control
 *       characters that ought to make the value junk.</li>
 * </ul>
 *
 * <p>Authoritative source:
 * {@code end_point_blank_deploy/docs/superpowers/specs/2026-09-08-hop-budget-contract.md}.
 */
public final class HopBudget {

    /** The budget header, in and out. */
    public static final String HOPS_HEADER = "X-EPB-Test-Hops";

    /** The opaque run identifier: forwarded verbatim, never modified, never generated. */
    public static final String RUN_HEADER = "X-EPB-Test-Run";

    /**
     * The clamp. A budget above this is treated as this rather than rejected, so
     * a typo is bounded without inventing a failure mode the load driver has to
     * handle. 64 is 12 full laps of a five-node ring.
     */
    public static final int MAX_HOPS = 64;

    private HopBudget() {
    }

    /**
     * Parses a raw header value into a budget.
     *
     * <p>The value before the first comma is taken first, and only then are the
     * rules above applied to it, so {@code "4, 8"} is 4 and {@code "abc,4"} is
     * 0. That is not cosmetic: PEP 3333 lets a WSGI server fold repeated
     * headers into one comma-separated value, and waitress — which
     * {@code epb_test_py} runs under — does, so by the time the framework
     * exposes the header the separate values no longer exist. An
     * implementation that can still see them must behave as if they had been
     * folded, or the five applications diverge on a case the load driver can
     * produce. Rejecting the whole value would be a <b>silent stop</b>: a
     * budget of 0 terminates the chain early and reads as an unexplainable
     * short run rather than as a parse bug.
     *
     * <p>Where repeated headers are visible as a list — {@code getHeader} on a
     * servlet request returns element 0 — the caller hands over element 0 and
     * this method applies the comma rule to it.
     *
     * @param headerValue the first {@code X-EPB-Test-Hops} value, or null when absent
     * @return the budget: 0 for absent, empty, whitespace-only, non-numeric,
     *         signed, negative and zero values; otherwise the value before the
     *         first comma, clamped to {@link #MAX_HOPS}
     */
    public static int parse(String headerValue) {
        if (headerValue == null) {
            return 0;
        }

        String value = trimAsciiWhitespace(beforeFirstComma(headerValue));
        if (value.isEmpty()) {
            return 0;
        }

        long budget = 0;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < '0' || character > '9') {
                // Junk anywhere makes the whole value junk: "4abc" is not 4.
                return 0;
            }
            // Stop accumulating once past the clamp. Every digit is still
            // validated, and no arithmetic can overflow, so a header of a
            // thousand nines clamps rather than throwing.
            if (budget <= MAX_HOPS) {
                budget = budget * 10 + (character - '0');
            }
        }

        return (int) Math.min(budget, MAX_HOPS);
    }

    /**
     * Everything up to the first comma, or the whole value when there is none.
     *
     * <p>Deliberately not {@code String.split(",")}: that discards trailing
     * empty fields, so {@code "4,".split(",")} is a one-element array and
     * {@code ",".split(",")} is an <i>empty</i> one, whose element 0 does not
     * exist. Both must reach the digit check as {@code "4"} and {@code ""}.
     *
     * <p>{@code comma < 0} and {@code comma <= 0} are indistinguishable here,
     * so a mutation between them survives: when the comma is at index 0 the
     * whole value still starts with a comma, the ASCII trim cannot remove one,
     * and the digit check rejects it. Both answer 0. Noted so the next reader
     * running a mutation pass does not go looking for the missing test.
     */
    private static String beforeFirstComma(String value) {
        int comma = value.indexOf(',');
        return comma < 0 ? value : value.substring(0, comma);
    }

    /** Trims ASCII whitespace only, so {@code " 4 "} is 4 but a no-break space is not. */
    private static String trimAsciiWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isAsciiWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && isAsciiWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isAsciiWhitespace(char character) {
        return character == ' '
                || character == '\t'
                || character == '\n'
                || character == '\u000B'
                || character == '\f'
                || character == '\r';
    }
}
